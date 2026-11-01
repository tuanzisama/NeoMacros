package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptEntry;
import ink.tuanzi.neomacros.core.module.ScriptMeta;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.thread.MainThreadScheduler;
import ink.tuanzi.neomacros.fabric.web.http.ApiError;
import ink.tuanzi.neomacros.fabric.web.v1.LocatedError;
import ink.tuanzi.neomacros.fabric.web.v1.MacroCategory;
import ink.tuanzi.neomacros.fabric.web.v1.MacroLifecycle;
import ink.tuanzi.neomacros.fabric.web.v1.MacroView;
import ink.tuanzi.neomacros.fabric.web.v1.Meta;
import ink.tuanzi.neomacros.fabric.web.v1.ServerInfo;
import ink.tuanzi.neomacros.fabric.web.v1.StateSnapshot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The fabric-side {@link PanelBackend}: builds the state snapshot and runs
 * macro actions over the module manager and core options. Callers reach it
 * through the {@link GameRunner} (the game main thread queue with its
 * five-second budget); the backend methods themselves assume that thread.
 * Load and runtime script errors are tracked per module so the panel's
 * {@code error} field stays current.
 */
public final class WebRuntime implements PanelBackend, GameRunner {

    private final ModuleManager modules;
    private final CoreOptions options;
    private final ConfigManager configManager;
    private final Path macrosDir;
    private final MainThreadScheduler mainThread;
    private final String modVersion;
    private final String gameVersion;
    private final Map<String, LocatedError> lastErrors = new ConcurrentHashMap<>();
    private volatile Supplier<ServerInfo> serverInfo = () -> new ServerInfo(0, 0, "loopback");

    /** Creates the backend over the live module manager and options. */
    public WebRuntime(ModuleManager modules, CoreOptions options, ConfigManager configManager,
                      Path macrosDir, MainThreadScheduler mainThread,
                      String modVersion, String gameVersion) {
        this.modules = modules;
        this.options = options;
        this.configManager = configManager;
        this.macrosDir = macrosDir;
        this.mainThread = mainThread;
        this.modVersion = modVersion;
        this.gameVersion = gameVersion;
    }

    /** Wires the server-status source once the service exists. */
    public void serverInfo(Supplier<ServerInfo> serverInfo) {
        this.serverInfo = serverInfo;
    }

    /** Tracks one module load failure (the panel's error field). */
    public void recordLoadError(String name, ScriptError error) {
        lastErrors.put(name, toLocatedError(error));
    }

    /** Clears a module's error after a successful (re)load. */
    public void clearError(String name) {
        lastErrors.remove(name);
    }

    /** Tracks one runtime script error by its source folder. */
    public void recordRuntimeError(Throwable error) {
        if (!(error instanceof ScriptError scriptError) || scriptError.getSourceName() == null) {
            return;
        }
        Path source = Path.of(scriptError.getSourceName());
        Optional<ScriptEntry> owner = modules.discover().stream()
                .filter(entry -> source.normalize().startsWith(entry.folder().normalize()))
                .findFirst();
        owner.ifPresent(entry -> lastErrors.put(entry.name(), toLocatedError(scriptError)));
    }

    private LocatedError toLocatedError(ScriptError error) {
        String script = relativize(error.getSourceName());
        return new LocatedError(script, error.getLine(), error.getColumn(), error.getMessage());
    }

    /** A macros-root-relative forward-slash path, or the raw text when outside. */
    private String relativize(String sourceName) {
        if (sourceName == null) {
            return "";
        }
        try {
            Path source = Path.of(sourceName).toAbsolutePath().normalize();
            return macrosDir.toAbsolutePath().relativize(source)
                    .toString().replace('\\', '/');
        } catch (RuntimeException e) {
            return sourceName;
        }
    }

    @Override
    public Meta meta() {
        return new Meta(List.of(1), modVersion, gameVersion);
    }

    @Override
    public StateSnapshot snapshot() {
        List<MacroView> macros = new ArrayList<>();
        for (ScriptEntry entry : modules.discover()) {
            macros.add(view(entry));
        }
        return new StateSnapshot(macros, serverInfo.get());
    }

    @Override
    public MacroView macroAction(String id, String action) {
        Optional<ScriptEntry> entry = modules.discover().stream()
                .filter(e -> e.name().equals(id))
                .findFirst();
        if (entry.isEmpty()) {
            throw ApiError.notFound("unknown macro: " + id);
        }
        switch (action) {
            case "enable" -> {
                options.autoload.put(id, true);
                configManager.save();
                if (modules.loaded(id).isEmpty()) {
                    modules.loadByName(id);
                }
            }
            case "disable" -> {
                options.autoload.put(id, false);
                configManager.save();
                modules.unload(id);
            }
            case "start" -> {
                if (modules.loaded(id).isEmpty() && modules.loadByName(id).isEmpty()) {
                    throw ApiError.conflict(
                            "cannot start '" + id + "': it is disabled or failed to load");
                }
            }
            case "stop" -> {
                if (modules.loaded(id).isEmpty()) {
                    throw ApiError.conflict("'" + id + "' is not running");
                }
                modules.unload(id);
            }
            case "reload" -> modules.reload(id);
            default -> throw ApiError.notFound("unknown action: " + action);
        }
        return view(entry.get());
    }

    private MacroView view(ScriptEntry entry) {
        String name = entry.name();
        String key = entry.effectiveKey(options.keybinds);
        MacroCategory category = MacroCategory.valueOf(entry.category().dirName());
        MacroLifecycle lifecycle = MacroLifecycle.valueOf(
                entry.meta().lifecycle().name().toLowerCase(Locale.ROOT));
        return new MacroView(
                entry.category().dirName() + "/" + name,
                category,
                name,
                entry.manifest().version(),
                entry.manifest().author(),
                lifecycle,
                options.autoload.getOrDefault(name, true),
                modules.loaded(name).isPresent(),
                key.isEmpty() ? null : key,
                entry.manifest().permissions(),
                lastErrors.get(name));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T call(Supplier<T> task) {
        try {
            return (T) mainThread.submit(task::get)
                    .get(GameRunner.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw ApiError.gameThreadTimeout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiError.gameThreadTimeout();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiError apiError) {
                throw apiError;
            }
            throw new ApiError(500, ApiError.INTERNAL,
                    cause == null ? "main-thread task failed" : cause.toString());
        }
    }
}
