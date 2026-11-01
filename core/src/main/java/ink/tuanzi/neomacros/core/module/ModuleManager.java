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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Discovers, loads, unloads, and reloads script folders below the macros
 * root ({@code keypress/event/services}, one folder with a {@code neomacro.*}
 * manifest per script). Loading opens a fresh engine session per module,
 * installs the binding tree, imports the resolved entry file, reads its
 * {@code dispose} export, and tracks every acquired resource for teardown.
 * Invalid folders are collected as {@link ScriptLoadError}s instead of
 * aborting discovery. Load failures mark the module {@code failed} and
 * surface through the error sink without affecting other modules.
 */
public final class ModuleManager implements AutoCloseable {

    /** Export a keypress script must define to receive key events. */
    public static final String KEY_HANDLER_EXPORT = "onKey";

    private final ScriptRuntime runtime;
    private final BindingRegistry bindings;
    private final java.util.function.BiFunction<String, List<String>, PermissionView> viewResolver;
    private final Path macrosRoot;
    private final Consumer<String> warn;
    private final Map<String, LoadedModule> loaded = new LinkedHashMap<>();
    private final Map<Path, Long> watchedFolders = new LinkedHashMap<>();
    private final Set<String> missingHandlerWarned = ConcurrentHashMap.newKeySet();
    private Predicate<String> autoloadFilter = name -> true;
    private Consumer<LoadedModule> loadListener;
    private Consumer<LoadedModule> unloadListener;
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

    /** Registers a listener invoked after a module finished loading. */
    public ModuleManager onLoad(Consumer<LoadedModule> listener) {
        this.loadListener = listener;
        return this;
    }

    /** Registers a listener invoked before a module is torn down. */
    public ModuleManager onUnload(Consumer<LoadedModule> listener) {
        this.unloadListener = listener;
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
    public LoadedModule load(ScriptEntry entry) throws ScriptError {
        String name = entry.name();
        ScriptContext context = runtime.createContext(name);
        LoadedModule module = new LoadedModule(entry, entry.entryFile(), context);
        // Attribution lives on the context loop thread for the module's whole
        // lifetime: event callbacks and async continuations run there long after
        // loading, and they all resolve their context through this marker. The
        // thread dies with the context, so the ThreadLocal clears itself.
        runOnLoop(context, () -> LoadTracker.begin(module));
        try {
            ScriptEngine.EngineSession session = runtime.engine().openSession(context);
            module.sessionInstalled(session);
            session.installBindings(bindings.neoTree(
                    viewResolver.apply(name, entry.manifest().permissions())));
            session.importModule(name, entry.entryFile());
            session.readExportedFunction("dispose").ifPresent(module::disposeHook);
            loaded.put(name, module);
            Consumer<LoadedModule> listener = loadListener;
            if (listener != null) {
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
    public List<LoadedModule> loadPhase(ScriptMeta.Lifecycle phase) {
        List<LoadedModule> result = new ArrayList<>();
        for (ScriptEntry entry : discover()) {
            if (loaded.containsKey(entry.name()) || !autoloadFilter.test(entry.name())) {
                continue;
            }
            try {
                LoadedModule module = load(entry);
                if (module.meta().lifecycle() == phase) {
                    result.add(module);
                } else {
                    unload(module.meta().name());
                }
            } catch (ScriptError e) {
                warn.accept("failed to load " + entry.name() + ": " + e.getMessage()
                        + (e.getSourceName() != null ? " (" + e.getSourceName() + ":" + e.getLine() + ")" : ""));
            }
        }
        return result;
    }

    /**
     * Manually loads the named script regardless of lifecycle (service
     * start / explicit start). Refuses scripts disabled in the autoload
     * table; returns empty when no such entry exists.
     */
    public Optional<LoadedModule> loadByName(String name) {
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
            warn.accept("failed to load " + name + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Delivers one key event to the named script's {@code onKey} export on
     * its context loop. Returns false when no such module is loaded; a
     * missing or failing export is reported once through the error sink.
     */
    public boolean trigger(String name, Object payload) {
        LoadedModule module = loaded.get(name);
        if (module == null) {
            return false;
        }
        module.session().invokeExport(KEY_HANDLER_EXPORT, payload).whenComplete((found, error) -> {
            if (error != null) {
                warn.accept("key handler of '" + name + "' failed: " + error.getMessage());
            } else if (!found && missingHandlerWarned.add(name)) {
                warn.accept("'" + name + "' has a key binding but exports no " + KEY_HANDLER_EXPORT
                        + "(ev) function");
            }
        });
        return true;
    }

    /** Unloads one module by manifest name; no-op when absent. */
    public void unload(String name) {
        LoadedModule module = loaded.remove(name);
        if (module != null) {
            Consumer<LoadedModule> listener = unloadListener;
            if (listener != null) {
                listener.accept(module);
            }
            module.close();
        }
    }

    /** Unloads every module whose lifecycle matches the phase. */
    public void unloadPhase(ScriptMeta.Lifecycle phase) {
        for (String name : List.copyOf(loaded.keySet())) {
            LoadedModule module = loaded.get(name);
            if (module.meta().lifecycle() == phase) {
                unload(name);
            }
        }
    }

    /** Unloads everything and reloads the automatic phases from a fresh discovery. */
    public void reloadAll() {
        for (String name : List.copyOf(loaded.keySet())) {
            unload(name);
        }
        runtime.engine().clearModuleCache();
        loadPhase(ScriptMeta.Lifecycle.ALWAYS);
        loadPhase(ScriptMeta.Lifecycle.WORLD);
    }

    /** Reloads one module by manifest name from its current folder. */
    public void reload(String name) {
        Optional<ScriptEntry> entry = discover().stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
        if (entry.isEmpty()) {
            warn.accept("cannot reload unknown module: " + name);
            return;
        }
        unload(name);
        runtime.engine().clearModuleCache();
        try {
            load(entry.get());
        } catch (ScriptError e) {
            warn.accept("failed to reload " + name + ": " + e.getMessage());
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

    /** Closes every module. */
    @Override
    public void close() {
        for (String name : List.copyOf(loaded.keySet())) {
            unload(name);
        }
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
    public void checkForChanges() {
        List<ScriptEntry> entries = discover();
        var seen = new HashSet<Path>();
        for (ScriptEntry entry : entries) {
            seen.add(entry.folder());
            long fingerprint = fingerprint(entry.folder());
            Long known = watchedFolders.get(entry.folder());
            if (known == null) {
                watchedFolders.put(entry.folder(), fingerprint);
                if (watchScanInitialized && !loaded.containsKey(entry.name())
                        && autoloadFilter.test(entry.name())) {
                    try {
                        load(entry);
                    } catch (ScriptError e) {
                        warn.accept("failed to load new script " + entry.name() + ": " + e.getMessage());
                    }
                }
            } else if (known != fingerprint) {
                watchedFolders.put(entry.folder(), fingerprint);
                if (loaded.containsKey(entry.name())) {
                    reload(entry.name());
                } else if (autoloadFilter.test(entry.name())) {
                    // The folder was known but not loadable before (e.g. the
                    // manifest was just fixed); pick it up now.
                    try {
                        load(entry);
                    } catch (ScriptError e) {
                        warn.accept("failed to load " + entry.name() + ": " + e.getMessage());
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
            }
        }
        watchScanInitialized = true;
    }

    /**
     * A change signature of one script folder: combines every file's relative
     * path and modification time, so edits, additions, renames, and deletions
     * inside the folder all register.
     */
    private long fingerprint(Path folder) {
        try (Stream<Path> files = Files.walk(folder)) {
            long hash = 0;
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                long mtime = Files.getLastModifiedTime(file).toMillis();
                hash = 31 * hash + folder.relativize(file).toString().hashCode();
                hash = 31 * hash + mtime;
            }
            return hash;
        } catch (IOException e) {
            return folder.toString().hashCode();
        }
    }
}
