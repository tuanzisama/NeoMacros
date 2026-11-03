package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptEntry;
import ink.tuanzi.neomacros.core.module.ScriptMeta;
import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.thread.MainThreadScheduler;
import ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier;
import ink.tuanzi.neomacros.fabric.web.http.ApiError;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import ink.tuanzi.neomacros.fabric.web.sse.PanelEvents;
import ink.tuanzi.neomacros.fabric.web.v1.FilesResponse;
import ink.tuanzi.neomacros.fabric.web.v1.LocatedError;
import ink.tuanzi.neomacros.fabric.web.v1.MacroCategory;
import ink.tuanzi.neomacros.fabric.web.v1.MacroLifecycle;
import ink.tuanzi.neomacros.fabric.web.v1.MacroView;
import ink.tuanzi.neomacros.fabric.web.v1.Meta;
import ink.tuanzi.neomacros.fabric.web.v1.ServerInfo;
import ink.tuanzi.neomacros.fabric.web.v1.StateSnapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The fabric-side {@link PanelBackend}: builds the state snapshot, runs
 * macro actions, and serves the file editor API over the module manager,
 * core options, and the macros root. Mutation methods run on the game main
 * thread (the {@link GameRunner} queue with its five-second budget);
 * {@link #readFile} is pure file IO and safe on any thread. Load and
 * runtime script errors are tracked per module so the panel's
 * {@code error} field stays current, and state changes are broadcast to
 * connected panels through {@link PanelEvents}.
 */
public final class WebRuntime implements PanelBackend, GameRunner {

    /** The write whitelist: text formats the editor may create or change. */
    private static final Set<String> WRITE_EXTENSIONS =
            Set.of("js", "mjs", "cjs", "yml", "yaml", "json", "json5", "md", "txt");

    /** The largest file a read serves (planted huge files must not OOM). */
    private static final long READ_CAP_BYTES = 4L * 1024 * 1024;

    /** The macro name rule for the scaffold endpoint. */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** The manifest file that always heads the folder listing. */
    private static final String MANIFEST_FILE = "neomacro.yml";

    private final ModuleManager modules;
    private final CoreOptions options;
    private final ConfigManager configManager;
    private final Path macrosDir;
    private final MainThreadScheduler mainThread;
    private final PanelEvents events;
    private final String modVersion;
    private final String gameVersion;
    private final Map<String, LocatedError> lastErrors = new ConcurrentHashMap<>();
    private volatile Supplier<ServerInfo> serverInfo = () -> new ServerInfo(0, 0, "loopback");
    private Path suppressDiskEvent;

    /** Creates the backend over the live module manager and options. */
    public WebRuntime(ModuleManager modules, CoreOptions options, ConfigManager configManager,
                      Path macrosDir, MainThreadScheduler mainThread, PanelEvents events,
                      String modVersion, String gameVersion) {
        this.modules = modules;
        this.options = options;
        this.configManager = configManager;
        this.macrosDir = macrosDir;
        this.mainThread = mainThread;
        this.events = events;
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

    /** Load/unload/error notifications for the panel broadcast. */
    public void moduleLoaded(LoadedModule module) {
        clearError(module.meta().name());
        broadcastState(module.entry());
    }

    /** Notifies the panel that one module stopped running. */
    public void moduleUnloaded(LoadedModule module) {
        broadcastState(module.entry());
    }

    /** Records a load failure and notifies the panel. */
    public void moduleLoadError(String name, ScriptError error) {
        recordLoadError(name, error);
        modules.entries().stream()
                .filter(entry -> entry.name().equals(name))
                .findFirst()
                .ifPresent(this::broadcastState);
    }

    /**
     * Notifies the panel that files inside one macro folder changed on
     * disk. Writes made through the panel itself are suppressed here and
     * announced by {@link #writeFile} with {@code by:"web"} instead.
     */
    public void folderChanged(Path folder, List<String> files) {
        if (folder.equals(suppressDiskEvent)) {
            return;
        }
        String prefix = relativePath(folder);
        for (String file : files) {
            events.broadcast("file:changed", Jsons.stringify(new FileChanged(
                    prefix.isEmpty() ? file : prefix + "/" + file, "disk")));
        }
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
        return new StateSnapshot(macros, serverInfo.get(), macrosRootPath());
    }

    /** The macros root as an absolute machine path (real when it exists). */
    private String macrosRootPath() {
        Path root = macrosDir.toAbsolutePath().normalize();
        Path real = realPath(root);
        return (real == null ? root : real).toString();
    }

    @Override
    public MacroView macroAction(String id, String action) {
        ScriptEntry entry = resolve(id)
                .orElseThrow(() -> ApiError.notFound("unknown macro: " + id));
        String name = entry.name();
        switch (action) {
            case "enable" -> {
                options.autoload = mutable(options.autoload);
                options.autoload.put(name, true);
                configManager.save();
                if (modules.loaded(name).isEmpty()) {
                    modules.loadByName(name);
                }
            }
            case "disable" -> {
                options.autoload = mutable(options.autoload);
                options.autoload.put(name, false);
                configManager.save();
                modules.unload(name);
            }
            case "start" -> {
                if (modules.loaded(name).isEmpty() && modules.loadByName(name).isEmpty()) {
                    throw ApiError.conflict(
                            "cannot start '" + name + "': it is disabled or failed to load");
                }
            }
            case "stop" -> {
                if (modules.loaded(name).isEmpty()) {
                    throw ApiError.conflict("'" + name + "' is not running");
                }
                modules.unload(name);
            }
            case "reload" -> modules.reload(name);
            default -> throw ApiError.notFound("unknown action: " + action);
        }
        broadcastState(entry);
        return view(entry);
    }

    @Override
    public FileContent readFile(String path) {
        Path target = resolveSandbox(path);
        if (!Files.isRegularFile(target)) {
            throw ApiError.notFound("no such file: " + path);
        }
        try {
            if (Files.size(target) > READ_CAP_BYTES) {
                throw new ApiError(413, ApiError.INVALID_REQUEST,
                        "the file exceeds the read cap of 4 MiB");
            }
            byte[] bytes = Files.readAllBytes(target);
            return new FileContent(bytes, Nm1Verifier.bodyHash(bytes));
        } catch (IOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot read the file: " + e.getMessage());
        }
    }

    @Override
    public FilesResponse listFiles(String id) {
        ScriptEntry entry = resolve(id)
                .orElseThrow(() -> ApiError.notFound("unknown macro: " + id));
        Path root = macrosDir.toAbsolutePath().normalize();
        Path folder = entry.folder().toAbsolutePath().normalize();
        Path realRoot = realPath(root);
        Path realFolder = realPath(folder);
        if (realRoot == null || realFolder == null || !realFolder.startsWith(realRoot)) {
            throw ApiError.forbidden("refusing to list outside the macros root");
        }
        List<String> files = new ArrayList<>();
        List<String> dirs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            // No-follow: an outward symlink is neither listed as a file nor
            // as a directory (reading through it is a 403 anyway, so the
            // listing must not advertise it).
            walk.filter(node -> !node.equals(folder)).forEach(node -> {
                String relative = folder.relativize(node).toString().replace('\\', '/');
                if (Files.isDirectory(node, LinkOption.NOFOLLOW_LINKS)) {
                    dirs.add(relative);
                } else if (Files.isRegularFile(node, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(relative);
                }
            });
        } catch (IOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot list the macro folder: " + e.getMessage());
        }
        files.sort(Comparator.comparing((String file) -> !file.equals(MANIFEST_FILE))
                .thenComparing(Comparator.naturalOrder()));
        dirs.sort(Comparator.naturalOrder());
        return new FilesResponse(List.copyOf(files), List.copyOf(dirs));
    }

    @Override
    public void deleteFile(String path) {
        Path target = resolveSandbox(path);
        if (!Files.exists(target)) {
            throw ApiError.notFound("no such file: " + path);
        }
        ScriptEntry owner = ownedEntry(target);
        Path folder = owner.folder().toAbsolutePath().normalize();
        Path normalized = target.normalize();
        if (normalized.equals(folder)) {
            throw ApiError.forbidden(
                    "refusing to delete a macro folder (use the macro endpoint)");
        }
        rejectManifestPath(normalized, folder, "delete");
        deleteTree(target);
        hotReload(owner);
        events.broadcast("file:changed",
                Jsons.stringify(new FileChanged(relativePath(target), "web")));
    }

    @Override
    public void moveFile(String from, String to) {
        Path source = resolveSandbox(from);
        Path target = resolveSandbox(to);
        rejectWhitespace(to);
        ScriptEntry owner = ownedEntry(source);
        ScriptEntry targetOwner = ownedEntry(target);
        if (!targetOwner.name().equals(owner.name())) {
            throw ApiError.forbidden("refusing to move across macro folders");
        }
        if (!Files.exists(source)) {
            throw ApiError.notFound("no such file: " + from);
        }
        if (Files.exists(target)) {
            throw ApiError.conflict("target already exists: " + to);
        }
        Path folder = owner.folder().toAbsolutePath().normalize();
        Path sourceNorm = source.normalize();
        Path targetNorm = target.normalize();
        if (sourceNorm.equals(folder)) {
            throw ApiError.forbidden("refusing to move a macro folder");
        }
        rejectManifestPath(sourceNorm, folder, "move");
        rejectManifestPath(targetNorm, folder, "move");
        if (Files.isRegularFile(source)) {
            if (!WRITE_EXTENSIONS.contains(extensionOf(target.getFileName().toString()))) {
                throw ApiError.unprocessable(
                        "the file extension must be one of " + WRITE_EXTENSIONS);
            }
        } else if (targetNorm.startsWith(sourceNorm)) {
            throw ApiError.unprocessable("cannot move a directory into itself");
        }
        try {
            Path parent = target.getParent();
            if (Files.isRegularFile(parent)) {
                throw ApiError.conflict("the parent path is an existing file: " + to);
            }
            Files.createDirectories(parent);
            Files.move(source, target);
        } catch (IOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot move: " + e.getMessage());
        }
        hotReload(owner);
        events.broadcast("file:changed",
                Jsons.stringify(new FileChanged(relativePath(target), "web")));
    }

    @Override
    public void makeDirectory(String path) {
        Path target = resolveSandbox(path);
        rejectWhitespace(path);
        ownedEntry(target);
        if (Files.exists(target)) {
            throw ApiError.conflict("already exists: " + path);
        }
        try {
            Path parent = target.getParent();
            if (Files.isRegularFile(parent)) {
                throw ApiError.conflict("the parent path is an existing file: " + path);
            }
            // mkdir -p: the target and every missing parent in one call.
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot create the directory: " + e.getMessage());
        }
    }

    @Override
    public void reveal(String path) {
        // The 422 shapes come before the sandbox's 403 gates: the panel
        // surfaces "illegal path" distinctly from "outside the macros root".
        // The -1 limit keeps trailing empties: "dir/" carries an empty
        // segment and must not silently normalize to "dir".
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || segment.indexOf('\\') >= 0) {
                throw ApiError.unprocessable("malformed reveal path: " + path);
            }
        }
        rejectWhitespace(path);
        Path target = resolveSandbox(path);
        if (!Files.exists(target)) {
            throw ApiError.notFound("no such file: " + path);
        }
        openInFileManager(target);
    }

    /**
     * Opens the path in the platform file manager. Arguments go to the
     * builder in list form — there is no shell anywhere in the chain — and
     * the commands are fixed binaries, so the only variable is a path that
     * already passed the sandbox.
     */
    private static void openInFileManager(Path target) {
        Path absolute = target.toAbsolutePath().normalize();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command;
        if (os.contains("win")) {
            command = Files.isRegularFile(target)
                    ? List.of("explorer.exe", "/select," + absolute)
                    : List.of("explorer.exe", absolute.toString());
        } else if (os.contains("mac") || os.contains("darwin")) {
            command = List.of("open", "-R", absolute.toString());
        } else {
            Path focus = Files.isRegularFile(target) ? absolute.getParent() : absolute;
            command = List.of("xdg-open", focus.toString());
        }
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot open the file manager: " + e.getMessage());
        }
    }

    /** The macro folder owning the target; the tree API never leaves one. */
    private ScriptEntry ownedEntry(Path target) {
        return ownerOf(target).orElseThrow(() -> ApiError.forbidden(
                "refusing to operate outside a macro folder"));
    }

    /** Rejects the macro's manifest as a delete or move source/target. */
    private static void rejectManifestPath(Path target, Path folder, String action) {
        if (target.equals(folder.resolve(MANIFEST_FILE))) {
            throw ApiError.forbidden("the macro manifest is protected (cannot " + action + ")");
        }
    }

    /** Created names must stay whitespace-free (NM1 signs the raw path). */
    private static void rejectWhitespace(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (Character.isWhitespace(path.charAt(i))) {
                throw ApiError.unprocessable("path must not contain whitespace: " + path);
            }
        }
    }

    /**
     * Reloads the owner after a disk mutation: one watch scan reloads and
     * refreshes the fingerprint atomically while the disk echo is
     * suppressed (the caller announces by=web instead).
     */
    private LocatedError hotReload(ScriptEntry owner) {
        suppressDiskEvent = owner.folder();
        try {
            modules.checkForChanges();
        } finally {
            suppressDiskEvent = null;
        }
        LocatedError error = lastErrors.get(owner.name());
        broadcastState(owner);
        return error;
    }

    @Override
    public FileWriteResult writeFile(String path, byte[] content, String ifMatch) {
        Path target = resolveSandbox(path);
        if (!WRITE_EXTENSIONS.contains(extensionOf(target.getFileName().toString()))) {
            throw ApiError.unprocessable(
                    "the file extension must be one of " + WRITE_EXTENSIONS);
        }
        byte[] current = null;
        if (Files.isRegularFile(target)) {
            try {
                current = Files.readAllBytes(target);
            } catch (IOException e) {
                throw new ApiError(500, ApiError.INTERNAL,
                        "cannot read the current file: " + e.getMessage());
            }
        }
        if (ifMatch != null && !ifMatch.isBlank()) {
            String currentHash = Nm1Verifier.bodyHash(current == null ? new byte[0] : current);
            if (!normalizeEtag(ifMatch).equals(currentHash)) {
                return FileWriteResult.conflict(current == null ? ""
                        : new String(current, StandardCharsets.UTF_8), currentHash);
            }
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot write the file: " + e.getMessage());
        }
        LocatedError error = null;
        Optional<ScriptEntry> owner = ownerOf(target);
        if (owner.isPresent()) {
            // The suppressed folder change becomes the by=web announcement
            // below instead of a by=disk echo.
            error = hotReload(owner.get());
        }
        events.broadcast("file:changed", Jsons.stringify(
                new FileChanged(relativePath(target), "web")));
        return FileWriteResult.saved(error);
    }

    @Override
    public MacroView createMacro(String category, String name, String description) {
        ScriptCategory parsed = ScriptCategory.fromDirName(category == null ? "" : category);
        if (parsed == null) {
            throw ApiError.unprocessable(
                    "unknown category '" + category + "' (keypress, event, services)");
        }
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw ApiError.unprocessable(
                    "the macro name must be 1-64 characters of [A-Za-z0-9_-]");
        }
        boolean duplicate = modules.discover().stream()
                .anyMatch(entry -> entry.name().equals(name));
        Path folder = macrosDir.toAbsolutePath().normalize()
                .resolve(parsed.dirName()).resolve(name).normalize();
        if (duplicate || Files.exists(folder)) {
            throw ApiError.conflict("a macro named '" + name + "' already exists");
        }
        try {
            Files.createDirectories(folder);
            StringBuilder manifest = new StringBuilder();
            manifest.append("name: ").append(yamlQuote(name)).append('\n');
            manifest.append("version: ").append(yamlQuote("0.0.1")).append('\n');
            manifest.append("author: ").append(yamlQuote("your-name")).append('\n');
            if (description != null && !description.isBlank()) {
                manifest.append("description: ").append(yamlQuote(oneLine(description)))
                        .append('\n');
            }
            Files.writeString(folder.resolve("neomacro.yml"), manifest.toString());
            Files.writeString(folder.resolve("index.js"), String.join("\n",
                    "// Entry script: top-level code runs once at load.",
                    "import { chat } from '@neomacros/chat';",
                    "",
                    "chat.log('" + name + " loaded');",
                    ""));
        } catch (IOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot create the macro folder: " + e.getMessage());
        }
        modules.checkForChanges();
        return modules.discover().stream()
                .filter(entry -> entry.name().equals(name))
                .findFirst()
                .map(this::view)
                .orElseThrow(() -> new ApiError(500, ApiError.INTERNAL,
                        "the scaffold was written but the macro did not parse"));
    }

    @Override
    public MacroView moveMacro(String from, String to) {
        ScriptCategory target = ScriptCategory.fromDirName(to == null ? "" : to);
        if (target == null) {
            throw ApiError.unprocessable(
                    "unknown category '" + to + "' (keypress, event, services)");
        }
        ScriptEntry entry = resolve(from)
                .orElseThrow(() -> ApiError.notFound("unknown macro: " + from));
        if (entry.category() == target) {
            return view(entry);
        }
        String name = entry.name();
        Path root = macrosDir.toAbsolutePath().normalize();
        Path source = entry.folder().toAbsolutePath().normalize();
        if (!source.getParent().normalize()
                .equals(root.resolve(entry.category().dirName()).normalize())) {
            throw ApiError.forbidden("refusing to move from outside a category folder");
        }
        Path destination = root.resolve(target.dirName()).resolve(name).normalize();
        if (Files.exists(destination)) {
            throw ApiError.conflict(
                    "a folder named '" + name + "' already exists in '" + to + "'");
        }
        Path realRoot = realPath(root);
        Path realSource = realPath(source);
        Path realDestinationParent = destination.getParent() == null
                ? null : nearestExistingReal(destination.getParent());
        if (realRoot == null || realSource == null || !realSource.startsWith(realRoot)
                || realDestinationParent == null || !realDestinationParent.startsWith(realRoot)) {
            throw ApiError.forbidden("refusing to move outside the macros root");
        }
        if (modules.loaded(name).isPresent()) {
            modules.unload(name);
        }
        try {
            Files.move(source, destination);
        } catch (IOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot move the macro folder: " + e.getMessage());
        }
        // The name-keyed autoload/keybinds state follows automatically; the
        // scan drops the old folder's watch and loads the new one.
        modules.checkForChanges();
        return modules.discover().stream()
                .filter(fresh -> fresh.name().equals(name))
                .findFirst()
                .map(this::view)
                .orElseThrow(() -> new ApiError(500, ApiError.INTERNAL,
                        "the macro moved but did not re-parse in '" + to + "'"));
    }

    @Override
    public void deleteMacro(String id) {
        ScriptEntry entry = resolve(id)
                .orElseThrow(() -> ApiError.notFound("unknown macro: " + id));
        String name = entry.name();
        Path root = macrosDir.toAbsolutePath().normalize();
        Path folder = entry.folder().toAbsolutePath().normalize();
        if (!folder.getParent().normalize()
                .equals(root.resolve(entry.category().dirName()).normalize())) {
            throw ApiError.forbidden("refusing to delete outside a category folder");
        }
        Path realFolder = realPath(folder);
        Path realRoot = realPath(root);
        if (realRoot == null || realFolder == null || !realFolder.startsWith(realRoot)) {
            throw ApiError.forbidden("refusing to delete outside the macros root");
        }
        if (modules.loaded(name).isPresent()) {
            modules.unload(name);
        }
        deleteTree(folder);
        options.autoload = mutable(options.autoload);
        options.autoload.remove(name);
        options.keybinds = mutable(options.keybinds);
        options.keybinds.remove(name);
        configManager.save();
        clearError(name);
        modules.checkForChanges();
    }

    /** Finds the entry for a bare manifest name or a category/name id. */
    private Optional<ScriptEntry> resolve(String id) {
        return modules.discover().stream()
                .filter(entry -> entry.name().equals(id)
                        || (entry.category().dirName() + "/" + entry.name()).equals(id))
                .findFirst();
    }

    /** The macro folder whose directory tree contains the target, if any. */
    private Optional<ScriptEntry> ownerOf(Path target) {
        return modules.discover().stream()
                .filter(entry -> target.normalize().startsWith(entry.folder().normalize()))
                .findFirst();
    }

    /**
     * Resolves a request path against the macros root behind three gates:
     * a conservative per-segment charset, lexical containment (normalize
     * plus prefix), and real-path containment (the symlink, junction, and
     * alternate-data-stream defense). Every rejection is 403.
     */
    private Path resolveSandbox(String path) {
        String[] segments = path.split("/");
        for (String segment : segments) {
            rejectBadSegment(segment);
        }
        Path root = macrosDir.toAbsolutePath().normalize();
        Path target;
        try {
            target = root.resolve(String.join("/", segments)).normalize();
        } catch (InvalidPathException e) {
            throw ApiError.forbidden("path rejected: " + path);
        }
        if (!target.startsWith(root)) {
            throw ApiError.forbidden("path escapes the macros root: " + path);
        }
        Path realRoot = realPath(root);
        if (realRoot != null) {
            // Climb to the nearest existing ancestor (new files have none of
            // their own) and verify that one against the real root.
            Path verified = nearestExistingReal(target);
            if (verified == null || !verified.startsWith(realRoot)) {
                throw ApiError.forbidden("path escapes the macros root: " + path);
            }
        }
        return target;
    }

    /** The real path of the nearest existing ancestor (the target included). */
    private static Path nearestExistingReal(Path target) {
        Path current = target;
        while (current != null) {
            Path real = realPath(current);
            if (real != null) {
                return real;
            }
            current = current.getParent();
        }
        return null;
    }

    /** Rejects empty, dot, and Windows-hostile segments outright. */
    private static void rejectBadSegment(String segment) {
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                || segment.endsWith(".") || segment.endsWith(" ")) {
            throw ApiError.forbidden("path rejected");
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < 0x20 || c == 0x7F || ":*?\"<>|\\".indexOf(c) >= 0) {
                throw ApiError.forbidden("path rejected");
            }
        }
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The lower-case extension without the dot, or the empty string. */
    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Strips the weakness marker and quotes browsers may add to an ETag. */
    private static String normalizeEtag(String value) {
        String etag = value.strip();
        if (etag.startsWith("W/")) {
            etag = etag.substring(2);
        }
        return etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"")
                ? etag.substring(1, etag.length() - 1) : etag;
    }

    /** A config map that accepts mutation (defaults are immutable). */
    private static <K, V> Map<K, V> mutable(Map<K, V> map) {
        return map instanceof LinkedHashMap<K, V> ? map : new LinkedHashMap<>(map);
    }

    /** Collapses control characters so a description stays one YAML line. */
    private static String oneLine(String value) {
        return value.replaceAll("\\p{Cntrl}", " ");
    }

    /** Double-quotes a YAML scalar with backslash and quote escaping. */
    private static String yamlQuote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A macros-root-relative forward-slash path. */
    private String relativePath(Path path) {
        return macrosDir.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    /** Deletes a folder tree depth-first without following links. */
    private static void deleteTree(Path folder) {
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            throw new ApiError(500, ApiError.INTERNAL,
                    "cannot delete the macro folder: " + e.getMessage());
        }
    }

    private void broadcastState(ScriptEntry entry) {
        MacroView snapshot = view(entry);
        events.broadcast("macro:state", Jsons.stringify(new MacroState(
                snapshot.id(), snapshot.enabled(), snapshot.running(), snapshot.error())));
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

    /** The macro:state SSE payload. */
    public record MacroState(String id, boolean enabled, boolean running, LocatedError error) {
    }

    /** The file:changed SSE payload. */
    public record FileChanged(String path, String by) {
    }
}
