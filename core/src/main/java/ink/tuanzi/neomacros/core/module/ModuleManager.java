package ink.tuanzi.neomacros.core.module;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Discovers, loads, unloads, and reloads drop-in script modules from the
 * scripts directory (one top-level {@code .js} file per module). Loading
 * opens a fresh engine session per module, installs the binding tree, imports
 * the module, reads its {@code meta} and {@code dispose} exports, and tracks
 * every acquired resource for teardown. Load failures mark the module
 * {@code failed} and surface through the error sink without affecting other
 * modules.
 */
public final class ModuleManager implements AutoCloseable {

    private final ScriptRuntime runtime;
    private final BindingRegistry bindings;
    private final PermissionView permissions;
    private final java.util.function.BiFunction<String, List<String>, PermissionView> viewResolver;
    private final Path scriptsDir;
    private final Consumer<String> warn;
    private final Map<String, LoadedModule> loaded = new LinkedHashMap<>();
    private final Map<String, Long> watched = new LinkedHashMap<>();
    private boolean watchScanInitialized;

    /** Creates the manager over one scripts directory with a fixed permission view. */
    public ModuleManager(ScriptRuntime runtime, BindingRegistry bindings, PermissionView permissions,
                         Path scriptsDir, Consumer<String> warn) {
        this(runtime, bindings, (name, declared) -> permissions, scriptsDir, warn);
    }

    /**
     * Creates the manager with a per-script view resolver (the trust gate):
     * the resolver receives the manifest name and declared capabilities and
     * returns the binding view for this load.
     */
    public ModuleManager(ScriptRuntime runtime, BindingRegistry bindings,
                         java.util.function.BiFunction<String, List<String>, PermissionView> viewResolver,
                         Path scriptsDir, Consumer<String> warn) {
        this.runtime = runtime;
        this.bindings = bindings;
        this.permissions = PermissionView.ALLOW_ALL;
        this.viewResolver = viewResolver;
        this.scriptsDir = scriptsDir;
        this.warn = warn;
    }

    /** Lists the discoverable script files, sorted by name. */
    public List<Path> discover() {
        if (!Files.isDirectory(scriptsDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(scriptsDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".js"))
                    .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            warn.accept("script discovery failed: " + e.getMessage());
            return List.of();
        }
    }

    /** Loads one script file; returns the loaded module. */
    public LoadedModule load(Path file) throws ScriptError {
        String stem = stemOf(file);
        ScriptContext context = runtime.createContext(stem);
        LoadedModule module = new LoadedModule(file, context);
        // Attribution lives on the context loop thread: guest top-level code
        // (and every binding call it makes) resolves its owner there. The
        // submit waits so the marker is active before any session work runs.
        // Attribution lives on the context loop thread for the module's whole
        // lifetime: event callbacks and async continuations run there long after
        // loading, and they all resolve their context through this marker. The
        // thread dies with the context, so the ThreadLocal clears itself.
        runOnLoop(context, () -> LoadTracker.begin(module));
        try {
            ScriptEngine.EngineSession session = runtime.engine().openSession(context);
            module.sessionInstalled(session);
            session.installBindings(bindings.neoTree(
                    viewResolver.apply(module.meta().name(), module.meta().permissions())));
            session.importModule(stem, file);

            Map<String, Object> metaExport = session.readExportedData("meta");
            ScriptMeta meta = ScriptMeta.from(metaExport, stem, warn);
            module.metaResolved(meta);

            session.readExportedFunction("dispose").ifPresent(module::disposeHook);
            loaded.put(module.meta().name(), module);
            return module;
        } catch (RuntimeException e) {
            module.close();
            throw e instanceof ScriptError se ? se
                    : new ScriptError("module load failed: " + stem, e, file.toString(), null, null);
        } finally {
            // No LoadTracker.end here: callbacks keep resolving their context
            // through the loop-thread marker until the context closes.
        }
    }

    /** Loads every discovered script whose lifecycle matches the phase. */
    public List<LoadedModule> loadPhase(ScriptMeta.Lifecycle phase) {
        List<LoadedModule> result = new ArrayList<>();
        for (Path file : discover()) {
            String stem = stemOf(file);
            if (loaded.containsKey(stem)) {
                continue;
            }
            try {
                LoadedModule module = load(file);
                if (module.meta().lifecycle() == phase) {
                    result.add(module);
                } else {
                    unload(module.meta().name());
                }
            } catch (ScriptError e) {
                warn.accept("failed to load " + stem + ": " + e.getMessage()
                        + (e.getSourceName() != null ? " (" + e.getSourceName() + ":" + e.getLine() + ")" : ""));
            }
        }
        return result;
    }

    /** Unloads one module by manifest name; no-op when absent. */
    public void unload(String name) {
        LoadedModule module = loaded.remove(name);
        if (module != null) {
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

    /** Unloads everything, clears the engine module cache, and reloads all phases. */
    public void reloadAll() {
        List<ScriptMeta.Lifecycle> phases = loaded.values().stream()
                .map(m -> m.meta().lifecycle())
                .distinct()
                .toList();
        for (String name : List.copyOf(loaded.keySet())) {
            unload(name);
        }
        runtime.engine().clearModuleCache();
        for (ScriptMeta.Lifecycle phase : phases.isEmpty() ? List.of(ScriptMeta.Lifecycle.WORLD) : phases) {
            loadPhase(phase);
        }
    }

    /** Reloads one module by manifest name (or file stem when it never loaded). */
    public void reload(String name) {
        Optional<Path> file = discover().stream()
                .filter(p -> stemOf(p).equals(name) || loaded.get(name) != null
                        && loaded.get(name).source().equals(p))
                .findFirst();
        if (file.isEmpty()) {
            warn.accept("cannot reload unknown module: " + name);
            return;
        }
        LoadedModule existing = loaded.get(name);
        if (existing != null) {
            ScriptMeta.Lifecycle phase = existing.meta().lifecycle();
            unload(existing.meta().name());
            runtime.engine().clearModuleCache();
            try {
                LoadedModule module = load(file.get());
                if (module.meta().lifecycle() != phase) {
                    unload(module.meta().name());
                }
            } catch (ScriptError e) {
                warn.accept("failed to reload " + name + ": " + e.getMessage());
            }
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
        var done = new java.util.concurrent.CompletableFuture<Void>();
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
     * One watch scan: loads newly discovered scripts, reloads changed ones,
     * and unloads deleted ones. Callers drive the cadence (a tick sink or a
     * scheduler); scan state is kept across calls so the first scan only
     * registers what it saw.
     */
    public void checkForChanges() {
        List<Path> current = discover();
        var seen = new java.util.HashSet<String>();
        for (Path file : current) {
            long mtime;
            try {
                mtime = Files.getLastModifiedTime(file).toMillis();
            } catch (IOException e) {
                continue;
            }
            String stem = stemOf(file);
            seen.add(stem);
            Long known = watched.get(stem);
            if (known == null) {
                watched.put(stem, mtime);
                if (watchScanInitialized && !loaded.containsKey(stem)) {
                    try {
                        load(file);
                    } catch (ScriptError e) {
                        warn.accept("failed to load new script " + stem + ": " + e.getMessage());
                    }
                }
            } else if (known != mtime) {
                watched.put(stem, mtime);
                if (loaded.containsKey(stem)) {
                    reload(stem);
                }
            }
        }
        for (String stem : List.copyOf(watched.keySet())) {
            if (!seen.contains(stem)) {
                watched.remove(stem);
                unload(stem);
            }
        }
        watchScanInitialized = true;
    }

    private static String stemOf(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".js".length());
    }
}
