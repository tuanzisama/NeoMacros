package ink.tuanzi.neomacros.core.module;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.module.manifest.ManifestParseException;
import ink.tuanzi.neomacros.core.module.manifest.ManifestParser;
import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Discovers, loads, unloads, and reloads script folders below the macros
 * root ({@code keypress/event/services}, one folder with a {@code neomacro.*}
 * manifest per script). Loading opens a fresh engine session per module,
 * installs the binding tree, imports the resolved entry file, requires the
 * {@code startup} export, wires the manifest-declared triggers (event
 * auto-subscriptions, the service activation call), reads the {@code
 * dispose} export, and tracks every acquired resource for teardown. Invalid
 * folders are collected as {@link ScriptLoadError}s instead of aborting
 * discovery. Load failures mark the module {@code failed} and surface
 * through the error sink without affecting other modules.
 */
public final class ModuleManager implements AutoCloseable {

    /** Export every macro must define: the startup entry invoked per trigger. */
    public static final String STARTUP_EXPORT = "startup";

    private final ScriptRuntime runtime;
    private final BindingRegistry bindings;
    private final java.util.function.BiFunction<String, List<String>, PermissionView> viewResolver;
    private final Path macrosRoot;
    private final Consumer<String> warn;
    // Concurrent maps: mutations serialize on one caller thread, but the
    // game main thread reads them between frames while that thread works.
    private final Map<String, LoadedModule> loaded = new ConcurrentHashMap<>();
    private final Map<Path, Map<String, Long>> watchedFolders = new ConcurrentHashMap<>();
    private volatile ink.tuanzi.neomacros.core.event.EventCatalog eventCatalog;
    private volatile ink.tuanzi.neomacros.core.task.TaskRegistry taskRegistry;
    private Predicate<String> autoloadFilter = name -> true;
    private final List<Consumer<LoadedModule>> loadListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<LoadedModule>> unloadListeners = new CopyOnWriteArrayList<>();
    private final List<java.util.function.BiConsumer<String, ScriptError>> loadErrorListeners =
            new CopyOnWriteArrayList<>();
    private final List<java.util.function.BiConsumer<Path, List<String>>> folderChangeListeners =
            new CopyOnWriteArrayList<>();
    private final List<Consumer<Path>> folderRemovedListeners = new CopyOnWriteArrayList<>();
    private volatile List<ScriptEntry> lastEntries = List.of();
    private volatile List<ScriptLoadError> lastErrors = List.of();
    private boolean watchScanInitialized;

    /** Creates the manager over one macros root with a fixed permission view. */
    public ModuleManager(ScriptRuntime runtime, BindingRegistry bindings, PermissionView permissions,
                         Path macrosRoot, Consumer<String> warn) {
        this(runtime, bindings, (name, declared) -> permissions, macrosRoot, warn);
    }

    /**
     * Creates the manager with a per-script view resolver (the trust gate):
     * the resolver receives the manifest name and declared capabilities and
     * returns the binding view for this load.
     */
    public ModuleManager(ScriptRuntime runtime, BindingRegistry bindings,
                         java.util.function.BiFunction<String, List<String>, PermissionView> viewResolver,
                         Path macrosRoot, Consumer<String> warn) {
        this.runtime = runtime;
        this.bindings = bindings;
        this.viewResolver = viewResolver;
        this.macrosRoot = macrosRoot;
        this.warn = warn;
    }

    /** Sets the enable filter consulted by every automatic load path. */
    public ModuleManager withAutoloadFilter(Predicate<String> filter) {
        this.autoloadFilter = filter;
        return this;
    }

    /**
     * Installs the event catalog used to validate {@code config.event.on}
     * names at load time. Without it (bare test harnesses) declared event
     * names are not validated against the catalog.
     */
    public ModuleManager withEventCatalog(ink.tuanzi.neomacros.core.event.EventCatalog catalog) {
        this.eventCatalog = catalog;
        return this;
    }

    /** The installed event catalog, or null when none was installed. */
    public ink.tuanzi.neomacros.core.event.EventCatalog eventCatalog() {
        return eventCatalog;
    }

    /** Installs the task registry receiving one task per macro activation. */
    public ModuleManager withTaskRegistry(ink.tuanzi.neomacros.core.task.TaskRegistry registry) {
        this.taskRegistry = registry;
        return this;
    }

    /** Registers a listener invoked after a module finished loading. */
    public ModuleManager onLoad(Consumer<LoadedModule> listener) {
        loadListeners.add(listener);
        return this;
    }

    /** Registers a listener invoked before a module is torn down. */
    public ModuleManager onUnload(Consumer<LoadedModule> listener) {
        unloadListeners.add(listener);
        return this;
    }

    /**
     * Registers a listener invoked when a module fails to load, with the
     * script name and the structured error (source location included) — the
     * surfaces that show per-module error state subscribe here.
     */
    public ModuleManager onLoadError(java.util.function.BiConsumer<String, ScriptError> listener) {
        loadErrorListeners.add(listener);
        return this;
    }

    /**
     * Registers a listener invoked when files inside an already-watched
     * script folder changed on disk (edited, added, or removed — the forward-
     * slash paths relative to the folder). Newly created folders surface
     * through the load listeners; deleted folders through
     * {@link #onFolderRemoved} (plus unload for the loaded ones).
     */
    public ModuleManager onFolderChange(java.util.function.BiConsumer<Path, List<String>> listener) {
        folderChangeListeners.add(listener);
        return this;
    }

    /**
     * Registers a listener invoked when an already-watched script folder
     * disappeared from disk (deleted or renamed outside the panel).
     */
    public ModuleManager onFolderRemoved(Consumer<Path> listener) {
        folderRemovedListeners.add(listener);
        return this;
    }

    /**
     * Scans every category directory one level deep and returns the valid
     * script entries (keypress, then event, then services; folders sorted by
     * name within a category). Invalid or duplicate folders land in
     * {@link #errors()}.
     */
    public List<ScriptEntry> discover() {
        List<ScriptEntry> entries = new ArrayList<>();
        List<ScriptLoadError> errors = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ScriptCategory category : ScriptCategory.values()) {
            Path categoryDir = macrosRoot.resolve(category.dirName());
            if (!Files.isDirectory(categoryDir)) {
                continue;
            }
            List<Path> folders;
            try (Stream<Path> files = Files.list(categoryDir)) {
                folders = files.filter(Files::isDirectory)
                        .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
            } catch (IOException e) {
                warn.accept("script discovery failed in " + category.dirName() + ": " + e.getMessage());
                continue;
            }
            for (Path folder : folders) {
                ink.tuanzi.neomacros.core.module.manifest.MacroManifest manifest;
                try {
                    manifest = ManifestParser.parse(folder, warn);
                    manifest.validateFor(category, warn);
                } catch (ManifestParseException e) {
                    errors.add(new ScriptLoadError(folder, e.getMessage()));
                    continue;
                }
                Path entryFile = resolveEntry(folder, manifest);
                if (entryFile == null) {
                    errors.add(new ScriptLoadError(folder,
                            "'" + manifest.name() + "': no entry file (index.js or manifest 'main')"));
                    continue;
                }
                if (!names.add(manifest.name())) {
                    errors.add(new ScriptLoadError(folder,
                            "duplicate script name '" + manifest.name() + "' (already used by another folder)"));
                    continue;
                }
                entries.add(new ScriptEntry(category, folder, manifest, entryFile));
            }
        }
        lastEntries = List.copyOf(entries);
        lastErrors = List.copyOf(errors);
        return entries;
    }

    /** The folders rejected by the most recent discovery, with reasons. */
    public List<ScriptLoadError> errors() {
        return lastErrors;
    }

    /** The valid entries from the most recent discovery. */
    public List<ScriptEntry> entries() {
        return lastEntries;
    }

    /** Resolves the entry file: {@code index.js} first, then the manifest {@code main}. */
    private Path resolveEntry(Path folder, ink.tuanzi.neomacros.core.module.manifest.MacroManifest manifest) {
        Path index = folder.resolve("index.js");
        if (Files.isRegularFile(index)) {
            return index;
        }
        if (!manifest.main().isEmpty()) {
            Path main = folder.resolve(manifest.main()).normalize();
            if (!main.startsWith(folder.normalize())) {
                warn.accept("'" + manifest.name() + "': manifest 'main' escapes the script folder: "
                        + manifest.main());
                return null;
            }
            return Files.isRegularFile(main) ? main : null;
        }
        return null;
    }

    /** Loads one discovered entry; returns the loaded module. */
    public synchronized LoadedModule load(ScriptEntry entry) throws ScriptError {
        return load(entry, "manual");
    }

    /** Loads one discovered entry, attributing the activation to {@code startedBy}. */
    private LoadedModule load(ScriptEntry entry, String startedBy) throws ScriptError {
        String name = entry.name();
        validateEventNames(entry);
        ScriptContext context = runtime.createContext(name);
        LoadedModule module = new LoadedModule(entry, entry.entryFile(), context);
        // Attribution lives on the context loop thread for the module's whole
        // lifetime: event callbacks and async continuations run there long after
        // loading, and they all resolve their context through this marker. The
        // thread dies with the context, so the ThreadLocal clears itself.
        runOnLoop(context, () -> LoadTracker.begin(module));
        try {
            ScriptEngine.EngineSession session = runtime.engine()
                    .openSession(context, entry.folder());
            module.sessionInstalled(session);
            session.installBindings(bindings.neoTree(
                    viewResolver.apply(name, entry.manifest().permissions())));
            session.importModule(name, entry.entryFile());
            session.readExportedFunction("dispose").ifPresent(module::disposeHook);
            if (session.readExportedFunction(STARTUP_EXPORT).isEmpty()) {
                throw new ScriptError("'" + name + "' must export a startup(...) function"
                        + " (see the migration guide)", null, entry.entryFile().toString(), null, null);
            }
            subscribeManifestEvents(module);
            loaded.put(name, module);
            if (taskRegistry != null) {
                module.taskId(taskRegistry.start(name, entry.category(), startedBy).taskId());
            }
            if (entry.category() == ScriptCategory.SERVICES) {
                // Single activation call; long-running services keep themselves
                // alive through subscriptions or an async loop inside startup.
                if (taskRegistry != null && module.taskId() != 0) {
                    taskRegistry.countTrigger(module.taskId());
                }
                session.invokeExport(STARTUP_EXPORT, Map.of()).whenComplete((found, error) -> {
                    if (error != null) {
                        warn.accept("startup of '" + name + "' failed: " + error.getMessage());
                    }
                });
            }
            for (Consumer<LoadedModule> listener : loadListeners) {
                listener.accept(module);
            }
            return module;
        } catch (RuntimeException e) {
            module.close();
            throw e instanceof ScriptError se ? se
                    : new ScriptError("module load failed: " + name, e, entry.entryFile().toString(), null, null);
        } finally {
            // No LoadTracker.end here: callbacks keep resolving their context
            // through the loop-thread marker until the context closes.
        }
    }

    /** Loads every discovered, enabled script whose lifecycle matches the phase. */
    public synchronized List<LoadedModule> loadPhase(ScriptMeta.Lifecycle phase) {
        List<LoadedModule> result = new ArrayList<>();
        for (ScriptEntry entry : discover()) {
            if (loaded.containsKey(entry.name()) || !autoloadFilter.test(entry.name())) {
                continue;
            }
            // Pre-filter by declared lifecycle: scripts of other phases are
            // never loaded, so no phantom startup/subscription side effects.
            if (entry.manifest().lifecycleOr(entry.category()) != phase) {
                continue;
            }
            try {
                result.add(load(entry, "lifecycle"));
            } catch (ScriptError e) {
                warn.accept("failed to load " + entry.name() + ": " + e.getMessage()
                        + (e.getSourceName() != null ? " (" + e.getSourceName() + ":" + e.getLine() + ")" : "")
                        + rootCauseText(e));
                notifyLoadError(entry.name(), e);
            }
        }
        return result;
    }

    /**
     * Manually loads the named script regardless of lifecycle (service
     * start / explicit start). Refuses scripts disabled in the autoload
     * table; returns empty when no such entry exists.
     */
    public synchronized Optional<LoadedModule> loadByName(String name) {
        if (loaded.containsKey(name)) {
            return Optional.of(loaded.get(name));
        }
        if (!autoloadFilter.test(name)) {
            warn.accept("cannot start '" + name + "': the script is disabled");
            return Optional.empty();
        }
        Optional<ScriptEntry> entry = discover().stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(load(entry.get()));
        } catch (ScriptError e) {
            warn.accept("failed to load " + name + ": " + e.getMessage() + rootCauseText(e));
            notifyLoadError(name, e);
            return Optional.empty();
        }
    }

    /**
     * Rejects declared {@code config.event.on} names that the catalog does
     * not know, listing the available events in the error.
     */
    private void validateEventNames(ScriptEntry entry) throws ScriptError {
        var event = entry.manifest().configOrEmpty().event();
        var catalog = eventCatalog;
        if (event == null || catalog == null) {
            return;
        }
        for (String eventName : event.onOrEmpty()) {
            if (catalog.descriptor(eventName).isEmpty()) {
                String available = catalog.all().stream()
                        .map(d -> d.name())
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new ScriptError("'" + entry.name() + "': unknown event '" + eventName
                        + "' in config.event.on; available events: " + available,
                        null, entry.entryFile().toString(), null, null);
            }
        }
    }

    /**
     * Subscribes a freshly loaded event macro to every {@code config.event.on}
     * entry; each occurrence invokes {@code startup(payload)} on the context
     * loop (fire-and-forget; async startup returns settle on the loop through
     * the async primitives). Subscriptions auto-unsubscribe on unload.
     */
    private void subscribeManifestEvents(LoadedModule module) {
        var event = module.entry().manifest().configOrEmpty().event();
        if (event == null) {
            return;
        }
        for (String eventName : event.onOrEmpty()) {
            var subscription = runtime.eventBus()
                    .on(eventName, module.context().loop(), payload ->
                            module.session().invokeExport(STARTUP_EXPORT, payload)
                                    .whenComplete((found, error) -> {
                                        if (error != null) {
                                            warn.accept("event handler of '"
                                                    + module.meta().name() + "' failed: "
                                                    + error.getMessage());
                                        }
                                    }));
            module.track(subscription);
        }
    }

    /** Notifies the load-error listeners. */
    private void notifyLoadError(String name, ScriptError error) {
        for (var listener : loadErrorListeners) {
            listener.accept(name, error);
        }
    }

    /** Appends the deepest cause's class and message, when one exists. */
    private static String rootCauseText(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause == error ? "" : " | caused by " + cause.getClass().getSimpleName()
                + ": " + cause.getMessage();
    }

    /**
     * Delivers one trigger payload to the named script's {@code startup}
     * export on its context loop. Returns false when no such module is
     * loaded; a failing export is reported through the error sink.
     */
    public boolean trigger(String name, Object payload) {
        LoadedModule module = loaded.get(name);
        if (module == null) {
            return false;
        }
        if (taskRegistry != null && module.taskId() != 0) {
            taskRegistry.countTrigger(module.taskId());
        }
        module.session().invokeExport(STARTUP_EXPORT, payload).whenComplete((found, error) -> {
            if (error != null) {
                warn.accept("startup handler of '" + name + "' failed: " + error.getMessage());
            }
        });
        return true;
    }

    /** Unloads one module by manifest name; no-op when absent. */
    public synchronized void unload(String name) {
        LoadedModule module = loaded.remove(name);
        if (module != null) {
            for (Consumer<LoadedModule> listener : unloadListeners) {
                listener.accept(module);
            }
            if (taskRegistry != null && module.taskId() != 0) {
                taskRegistry.stop(module.taskId());
            }
            module.close();
        }
    }

    /** Unloads every module whose lifecycle matches the phase. */
    public synchronized void unloadPhase(ScriptMeta.Lifecycle phase) {
        for (String name : List.copyOf(loaded.keySet())) {
            LoadedModule module = loaded.get(name);
            if (module.meta().lifecycle() == phase) {
                unload(name);
            }
        }
    }

    /** Unloads everything and reloads the automatic phases from a fresh discovery. */
    public synchronized void reloadAll() {
        for (String name : List.copyOf(loaded.keySet())) {
            unload(name);
        }
        runtime.engine().clearModuleCache();
        invalidateDevToolsSession();
        loadPhase(ScriptMeta.Lifecycle.ALWAYS);
        loadPhase(ScriptMeta.Lifecycle.WORLD);
    }

    /** Reloads one module by manifest name from its current folder. */
    public synchronized void reload(String name) {
        Optional<ScriptEntry> entry = discover().stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
        if (entry.isEmpty()) {
            warn.accept("cannot reload unknown module: " + name);
            return;
        }
        unload(name);
        runtime.engine().clearModuleCache();
        invalidateDevToolsSession();
        try {
            load(entry.get(), "reload");
        } catch (ScriptError e) {
            warn.accept("failed to reload " + name + ": " + e.getMessage());
            notifyLoadError(name, e);
        }
    }

    /** All currently loaded modules keyed by manifest name (load order). */
    public Collection<LoadedModule> loaded() {
        return List.copyOf(loaded.values());
    }

    /** The loaded module with the given manifest name. */
    public Optional<LoadedModule> loaded(String name) {
        return Optional.ofNullable(loaded.get(name));
    }

    // ---- DevTools console session -----------------------------------------

    private static final String DEVTOOLS_CONTEXT = "devtools";
    private ScriptContext devToolsContext;
    private ScriptEngine.EngineSession devToolsSession;

    /**
     * The DevTools console (REPL) session: one persistent engine session on
     * its own {@code devtools} context loop, opened lazily on first use and
     * bound with the full binding surface (a paired editor device already
     * has the power to write arbitrary macro code through the file API, so
     * evaluation grants it nothing new). Reloads invalidate it, so freshly
     * reloaded module code is visible to later imports and REPL globals
     * reset with the modules they may reference.
     */
    public synchronized ScriptEngine.EngineSession devToolsSession() throws ScriptError {
        if (devToolsSession != null) {
            return devToolsSession;
        }
        ScriptContext context = runtime.createContext(DEVTOOLS_CONTEXT);
        ScriptEngine.EngineSession session = runtime.engine().openSession(context, macrosRoot);
        try {
            session.installBindings(bindings.neoTree(
                    viewResolver.apply(DEVTOOLS_CONTEXT, List.of())));
        } catch (RuntimeException e) {
            try {
                session.close();
            } catch (Exception closeFailure) {
                // best effort: the primary failure is what we report
            }
            context.close();
            throw e instanceof ScriptError se ? se : new ScriptError("devtools session init failed", e);
        }
        this.devToolsContext = context;
        this.devToolsSession = session;
        return session;
    }

    /** The DevTools REPL session's context, when the session is open. */
    public synchronized Optional<ScriptContext> devToolsContext() {
        return Optional.ofNullable(devToolsContext);
    }

    /** Drops the DevTools console session (the next evaluation reopens it fresh). */
    public synchronized void invalidateDevToolsSession() {
        ScriptEngine.EngineSession session = devToolsSession;
        this.devToolsSession = null;
        ScriptContext context = devToolsContext;
        this.devToolsContext = null;
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                // the context loop may already be gone; nothing to salvage
                warn.accept("closing the devtools session failed: " + e.getMessage());
            }
        }
        if (context != null) {
            context.close();
        }
    }

    /** Closes every module. */
    @Override
    public synchronized void close() {
        for (String name : List.copyOf(loaded.keySet())) {
            unload(name);
        }
        invalidateDevToolsSession();
    }

    /** Runs one task on the context loop and blocks until it completed. */
    private static void runOnLoop(ScriptContext context, Runnable task) {
        var done = new CompletableFuture<Void>();
        context.loop().execute(() -> {
            try {
                task.run();
            } finally {
                done.complete(null);
            }
        });
        try {
            done.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException ignored) {
            // the loop is shutting down or the task failed; attribution ends here
        }
    }

    /**
     * One watch scan: loads newly discovered folders, reloads changed ones,
     * and unloads deleted ones. Callers drive the cadence (a tick sink or a
     * scheduler); scan state is kept across calls so the first scan only
     * registers what it saw.
     */
    public synchronized void checkForChanges() {
        List<ScriptEntry> entries = discover();
        var seen = new HashSet<Path>();
        for (ScriptEntry entry : entries) {
            seen.add(entry.folder());
            Map<String, Long> fingerprint = fingerprint(entry.folder());
            Map<String, Long> known = watchedFolders.get(entry.folder());
            if (known == null) {
                watchedFolders.put(entry.folder(), fingerprint);
                if (watchScanInitialized && !loaded.containsKey(entry.name())
                        && autoloadFilter.test(entry.name())) {
                    try {
                        load(entry, "autostart");
                    } catch (ScriptError e) {
                        warn.accept("failed to load new script " + entry.name() + ": " + e.getMessage());
                        notifyLoadError(entry.name(), e);
                    }
                }
            } else if (!known.equals(fingerprint)) {
                watchedFolders.put(entry.folder(), fingerprint);
                notifyFolderChange(entry.folder(), changedFiles(known, fingerprint));
                if (loaded.containsKey(entry.name())) {
                    reload(entry.name());
                } else if (autoloadFilter.test(entry.name())) {
                    // The folder was known but not loadable before (e.g. the
                    // manifest was just fixed); pick it up now.
                    try {
                        load(entry, "autostart");
                    } catch (ScriptError e) {
                        warn.accept("failed to load " + entry.name() + ": " + e.getMessage());
                        notifyLoadError(entry.name(), e);
                    }
                }
            }
        }
        for (Path folder : List.copyOf(watchedFolders.keySet())) {
            if (!seen.contains(folder)) {
                watchedFolders.remove(folder);
                for (String name : List.copyOf(loaded.keySet())) {
                    if (loaded.get(name).entry().folder().equals(folder)) {
                        unload(name);
                    }
                }
                for (Consumer<Path> listener : folderRemovedListeners) {
                    listener.accept(folder);
                }
            }
        }
        watchScanInitialized = true;
    }

    /** Notifies the folder-change listeners with the files that differ. */
    private void notifyFolderChange(Path folder, List<String> changedFiles) {
        if (!folderChangeListeners.isEmpty()) {
            for (var listener : folderChangeListeners) {
                listener.accept(folder, changedFiles);
            }
        }
    }

    /** The relative paths whose mtime differs or that were added or removed. */
    private static List<String> changedFiles(Map<String, Long> known, Map<String, Long> fingerprint) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Long> file : fingerprint.entrySet()) {
            if (!file.getValue().equals(known.get(file.getKey()))) {
                changed.add(file.getKey());
            }
        }
        for (String removed : known.keySet()) {
            if (!fingerprint.containsKey(removed)) {
                changed.add(removed);
            }
        }
        return changed;
    }

    /**
     * A change signature of one script folder: every entry's (files and
     * directories) relative path (forward slashes) and modification time,
     * so edits, additions, renames, deletions, and empty-directory changes
     * inside the folder all register.
     */
    private Map<String, Long> fingerprint(Path folder) {
        Map<String, Long> files = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path entry : walk.filter(p -> !p.equals(folder)).toList()) {
                files.put(folder.relativize(entry).toString().replace('\\', '/'),
                        Files.getLastModifiedTime(entry).toMillis());
            }
        } catch (IOException e) {
            // unreadable folder: a stable sentinel (mtime can never be -1) so
            // repeated failures compare equal and do not re-trigger reloads
            files.put("!unreadable", -1L);
        }
        return files;
    }
}
