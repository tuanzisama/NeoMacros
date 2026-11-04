package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.bindgen.ModuleBinding;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionError;
import ink.tuanzi.neomacros.core.console.ScriptConsole;
import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import ink.tuanzi.neomacros.core.runtime.EvalOutcome;
import ink.tuanzi.neomacros.core.runtime.EvalTimeoutError;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * GraalJS implementation of the engine SPI. One polyglot {@link Context} per
 * session (= one script context), bound to the context loop thread: every
 * session operation (module import, binding install, guest reads) funnels
 * onto the loop, so GraalJS never sees concurrent access (03 §3 thread
 * model). Host access is disabled, module loading routes through the sandbox
 * file system (virtual {@code @neomacros/*} modules plus the script
 * directory), the {@code neo} global is injected deeply frozen, and
 * {@link AsyncBinding} members surface as JS Promise factories.
 */
public final class GraalJsEngine implements ScriptEngine {

    /** MIME type marking a source as an ES module. */
    private static final String MODULE_MIME = "application/javascript+module";

    /** Maximum time a session operation may occupy the context loop. */
    private static final long LOOP_TIMEOUT_SECONDS = 30;

    /**
     * Engine glue evaluated once per session on the loop thread: the async
     * binding wrapper, guest Error construction for rejections, the
     * deeply-frozen {@code neo} global installation, the {@code console}
     * global (thin wrappers over the host emit proxy; the host formats
     * values and resolves call sites), and the script-facing class tree
     * builder. The binding tree itself is built from Java through the
     * polyglot Value API (host maps are not enumerable from guest code
     * when host access is disabled).
     */
    private static final String GLUE = """
            globalThis.__neoMakeAsync = function (hostFn) {
                return function (...args) {
                    return new Promise(function (resolve, reject) {
                        hostFn(args, resolve, reject);
                    });
                };
            };
            globalThis.__neoNewError = function (message) {
                return new Error(message);
            };
            globalThis.__neoBytes = function (list) {
                const out = new Uint8Array(list.length);
                for (let i = 0; i < list.length; i++) {
                    out[i] = list[i];
                }
                return out;
            };
            globalThis.__neoInstall = function (tree) {
                const deepFreeze = function (value) {
                    Object.freeze(value);
                    for (const key of Object.keys(value)) {
                        const child = value[key];
                        // Recurse into plain objects only: proxy-backed module
                        // bindings are read-only by construction, and touching
                        // their members would fire live getters.
                        const proto = child === null ? null : Object.getPrototypeOf(child);
                        if (child !== null && typeof child === 'object'
                                && (proto === Object.prototype || proto === null)) {
                            deepFreeze(child);
                        }
                    }
                };
                deepFreeze(tree);
                Object.defineProperty(globalThis, 'neo', {
                    value: tree,
                    writable: false,
                    enumerable: true,
                    configurable: false
                });
                return tree;
            };
            // Console: thin JS wrappers over the host emit proxy. Wrappers
            // only capture the call-site stack and keep group/timer/counter
            // state; all value formatting happens host-side. The host skips
            // frames from this glue source when resolving the location.
            globalThis.__neoInstallConsole = function (emit) {
                const groupDepth = { value: 0 };
                const timers = Object.create(null);
                const counters = Object.create(null);
                const nowMs = function () { return (new Date()).getTime(); };
                const channel = function (value) {
                    return value === undefined ? 'default' : String(value);
                };
                const send = function (level, kind, args, extra) {
                    let stack = '';
                    try {
                        stack = (new Error()).stack || '';
                    } catch (e) {
                        stack = '';
                    }
                    emit(level, kind, args, extra || {}, stack);
                };
                const basic = function (level) {
                    return function () {
                        send(level, level, Array.prototype.slice.call(arguments),
                                { depth: groupDepth.value });
                    };
                };
                const consoleObject = {
                    log: basic('log'),
                    info: basic('info'),
                    warn: basic('warn'),
                    error: basic('error'),
                    debug: basic('debug'),
                    trace: function () {
                        send('log', 'trace', Array.prototype.slice.call(arguments),
                                { depth: groupDepth.value });
                    },
                    dir: function () {
                        send('log', 'dir', Array.prototype.slice.call(arguments),
                                { depth: groupDepth.value });
                    },
                    table: function () {
                        send('log', 'table', Array.prototype.slice.call(arguments),
                                { depth: groupDepth.value });
                    },
                    assert: function (condition) {
                        if (!condition) {
                            const rest = Array.prototype.slice.call(arguments, 1);
                            send('error', 'assert', rest, { depth: groupDepth.value });
                        }
                    },
                    count: function (value) {
                        const key = channel(value);
                        counters[key] = (counters[key] || 0) + 1;
                        send('log', 'count', [],
                                { depth: groupDepth.value, label: key, count: counters[key] });
                    },
                    countReset: function (value) {
                        counters[channel(value)] = 0;
                    },
                    time: function (value) {
                        timers[channel(value)] = nowMs();
                    },
                    timeLog: function (value) {
                        const key = channel(value);
                        if (timers[key] !== undefined) {
                            send('log', 'time', [],
                                    { depth: groupDepth.value, label: key, ms: nowMs() - timers[key] });
                        }
                    },
                    timeEnd: function (value) {
                        const key = channel(value);
                        if (timers[key] !== undefined) {
                            const ms = nowMs() - timers[key];
                            delete timers[key];
                            send('log', 'timeEnd', [],
                                    { depth: groupDepth.value, label: key, ms: ms });
                        }
                    },
                    group: function () {
                        send('log', 'group', Array.prototype.slice.call(arguments),
                                { depth: groupDepth.value });
                        groupDepth.value = groupDepth.value + 1;
                    },
                    groupEnd: function () {
                        if (groupDepth.value > 0) {
                            groupDepth.value = groupDepth.value - 1;
                        }
                    },
                    clear: function () {
                        send('log', 'clear', [], { depth: 0 });
                    }
                };
                consoleObject.groupCollapsed = consoleObject.group;
                Object.defineProperty(globalThis, 'console', {
                    value: Object.freeze(consoleObject),
                    writable: false,
                    enumerable: true,
                    configurable: false
                });
            };
            globalThis.__neoDefineClass = function (name, parentName, properties, methods) {
                let table = globalThis.__neoClassTable;
                if (table === undefined) {
                    table = {};
                    Object.defineProperty(globalThis, '__neoClassTable', {
                        value: table,
                        writable: false,
                        enumerable: false,
                        configurable: false
                    });
                }
                if (table[name] !== undefined) {
                    return table[name];
                }
                const parent = parentName ? table[parentName] : Object;
                class C extends parent {
                    constructor(cell) {
                        super();
                        // configurable: a base constructor installs the cell
                        // first along the super() chain; the most-derived
                        // constructor re-defines it with the real value.
                        Object.defineProperty(this, '__cell', {
                            value: cell,
                            writable: false,
                            enumerable: false,
                            configurable: true
                        });
                    }
                }
                const readCell = function (self) {
                    const cell = self.__cell;
                    if (cell === undefined || cell === null) {
                        throw new Error('handle is no longer valid');
                    }
                    return cell;
                };
                for (const property of properties) {
                    Object.defineProperty(C.prototype, property, {
                        get() {
                            return readCell(this)[property];
                        },
                        configurable: true
                    });
                }
                for (const method of methods) {
                    C.prototype[method] = function (...args) {
                        return readCell(this)[method](...args);
                    };
                }
                Object.defineProperty(C.prototype, Symbol.toStringTag, {
                    value: name,
                    configurable: true
                });
                table[name] = C;
                return C;
            };
            """;

    private final ModuleResolver resolver;
    private final List<GraalJsSession> sessions = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    /** Creates the engine; the resolver feeds virtual modules and script files. */
    public GraalJsEngine(ModuleResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public EngineSession openSession(ScriptContext context, Path moduleRoot) throws ScriptError {
        if (closed) {
            throw new ScriptError("engine is closed");
        }
        GraalJsSession session = new GraalJsSession(context, moduleRoot);
        session.onLoop(() -> {
            try {
                session.context.eval(Source.newBuilder("js", GLUE, "neomacros-engine-glue")
                        .mimeType("application/javascript")
                        .buildLiteral());
            } catch (PolyglotException e) {
                throw translate("neomacros-engine-glue", e);
            }
            session.glue = session.context.getBindings("js");
            session.installConsole();
            return null;
        });
        sessions.add(session);
        return session;
    }

    /**
     * Module caching note: GraalJS caches imported modules per context, and
     * every script load opens a fresh session, so reload semantics are
     * guaranteed by session lifetime rather than a cache-clearing API. This
     * method exists on the SPI so callers can signal re-import intent; it
     * performs no work here by design.
     */
    @Override
    public void clearModuleCache() {
        // Per-context module caches die with their sessions (see javadoc).
    }

    @Override
    public void close() {
        closed = true;
        for (GraalJsSession session : sessions) {
            session.close();
        }
        sessions.clear();
    }

    /**
     * Translates a polyglot failure into a ScriptError with source positions.
     * The top-level source location is often null for guest-thrown errors; the
     * first guest stack frame carries the throw site and is used as fallback.
     */
    static ScriptError translate(String fallbackSource, PolyglotException pe) {
        var location = pe.getSourceLocation();
        if (location == null) {
            for (var frame : pe.getPolyglotStackTrace()) {
                if (frame.isGuestFrame() && frame.getSourceLocation() != null) {
                    location = frame.getSourceLocation();
                    break;
                }
            }
        }
        return new ScriptError(pe.getMessage(), pe,
                location != null ? location.getSource().getName() : fallbackSource,
                location != null ? location.getStartLine() : null,
                location != null ? location.getStartColumn() : null);
    }

    /** One session: a polyglot context bound to one script context's loop thread. */
    final class GraalJsSession implements EngineSession {

        private final ScriptContext owner;
        private final Context context;
        private final Path moduleRoot;
        private final ConsoleRateLimiter rateLimiter = new ConsoleRateLimiter();
        private Value glue;
        private Value lastNamespace;
        private Value cachedUndefined;
        private final Map<String, Value> guestClasses = new ConcurrentHashMap<>();

        private GraalJsSession(ScriptContext owner, Path moduleRoot) {
            this.owner = owner;
            this.moduleRoot = moduleRoot;
            this.context = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.NONE)
                    .allowIO(IOAccess.newBuilder()
                            .fileSystem(new ScriptSandboxFileSystem(resolver, moduleRoot))
                            .build())
                    .build();
        }

        @Override
        public void installBindings(Map<String, Object> bindings) throws ScriptError {
            onLoop(() -> {
                Value makeAsync = glue.getMember("__neoMakeAsync");
                Value tree = buildTree(bindings, makeAsync);
                glue.getMember("__neoInstall").execute(tree);
                return null;
            });
        }

        @Override
        public void importModule(String moduleName, Path sourceFile) throws ScriptError {
            // Dynamic import keeps the loop free: static eval of a module with
            // top-level await would block the loop thread and deadlock promise
            // resolution, while import(uri) returns a promise immediately.
            String uriLiteral = sourceFile.toUri().normalize().toString().replace("'", "\\'");
            CompletableFuture<Void> settled = new CompletableFuture<>();
            AtomicReference<Value> rejection = new AtomicReference<>();
            onLoop(() -> {
                context.eval("js",
                        "globalThis.__neoModulePromise = import('" + uriLiteral + "');");
                Value promise = context.getBindings("js").getMember("__neoModulePromise");
                promise.getMember("then").execute(
                        executable(arguments -> {
                            if (arguments.length > 0) {
                                lastNamespace = arguments[0];
                            }
                            settled.complete(null);
                            return null;
                        }),
                        executable(arguments -> {
                            if (arguments.length > 0) {
                                rejection.set(arguments[0]);
                            }
                            settled.complete(null);
                            return null;
                        }));
                return null;
            });
            try {
                settled.get(LOOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ScriptError scriptError) {
                    throw scriptError;
                }
                throw new ScriptError("module import failed: " + moduleName, cause);
            } catch (TimeoutException e) {
                throw new ScriptError("module import did not settle within " + LOOP_TIMEOUT_SECONDS + "s: "
                        + moduleName, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScriptError("interrupted while importing module: " + moduleName, e);
            }
            Value failed = rejection.get();
            if (failed != null) {
                throw new ScriptError("module '" + moduleName + "' rejected: " + guestMessage(failed));
            }
        }

        /** Evaluates a module-mime source string; observation reads globals afterwards. */
        void importSource(String moduleName, String source) throws ScriptError {
            onLoop(() -> {
                context.eval(Source.newBuilder("js", source, moduleName)
                        .mimeType(MODULE_MIME)
                        .buildLiteral());
                return null;
            });
        }

        /** Matches any {@code await} token; script-mime eval cannot run top-level await. */
        private static final Pattern AWAIT_TOKEN = Pattern.compile("\\bawait\\b");

        /** Source name REPL frames report in error stacks. */
        private static final String REPL_SOURCE_NAME = "devtools-repl";

        /**
         * Evaluates one REPL expression with script semantics: top-level
         * {@code let/const/function} persist in the global lexical environment
         * across evaluations (module-mime eval would scope them to the module
         * record). Sources containing {@code await} are wrapped in an async
         * IIFE, and a promise completion value is settled on the loop without
         * blocking it (mirroring {@link #importModule}). Guest-level failures
         * come back as an error outcome; loop trouble throws
         * {@link EvalTimeoutError} / {@link ScriptError}.
         */
        @Override
        public EvalOutcome eval(String expression) throws ScriptError {
            long startNanos = System.nanoTime();
            CompletableFuture<EvalOutcome> done = new CompletableFuture<>();
            owner.loop().execute(() -> {
                try {
                    Value result = context.eval(Source.newBuilder("js", awaitWrapped(expression),
                            REPL_SOURCE_NAME).buildLiteral());
                    if (isPromiseLike(result)) {
                        result.getMember("then").execute(
                                executable(arguments -> {
                                    done.complete(valueOutcome(
                                            arguments.length > 0 ? arguments[0] : undefinedValue(),
                                            startNanos));
                                    return null;
                                }),
                                executable(arguments -> {
                                    done.complete(errorOutcome(
                                            arguments.length > 0 ? arguments[0] : null,
                                            startNanos));
                                    return null;
                                }));
                        return;
                    }
                    done.complete(valueOutcome(result, startNanos));
                } catch (PolyglotException e) {
                    done.complete(polyglotErrorOutcome(e, startNanos));
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            });
            try {
                return done.get(LOOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ScriptError scriptError) {
                    throw scriptError;
                }
                throw new ScriptError(cause.getMessage(), cause);
            } catch (TimeoutException e) {
                throw new EvalTimeoutError("the expression did not settle within "
                        + LOOP_TIMEOUT_SECONDS + "s");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScriptError("interrupted while waiting on the context loop", e);
            }
        }

        /** Script-mime eval cannot run top-level await; wrap such sources in an async IIFE. */
        private static String awaitWrapped(String expression) {
            if (AWAIT_TOKEN.matcher(expression).find()) {
                return "(async () => {\n" + expression + "\n})()";
            }
            return expression;
        }

        /** Promise-shaped check: any object whose {@code then} member is callable. */
        private static boolean isPromiseLike(Value value) {
            try {
                if (value == null || value.isNull() || !value.hasMembers()) {
                    return false;
                }
                Value then = value.getMember("then");
                return then != null && then.canExecute();
            } catch (RuntimeException e) {
                return false;
            }
        }

        /** Renders one settled value as a value outcome (loop thread). */
        private EvalOutcome valueOutcome(Value value, long startNanos) {
            ConsoleFormatter formatter = new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES);
            return EvalOutcome.ofValue(formatter.node(value), elapsedMs(startNanos));
        }

        /** Renders one rejection reason as an error outcome (loop thread). */
        private EvalOutcome errorOutcome(Value thrown, long startNanos) {
            long durationMs = elapsedMs(startNanos);
            if (thrown == null || thrown.isNull()) {
                return EvalOutcome.ofError("Error", "promise rejected without a reason", null, durationMs);
            }
            ConsoleFormatter formatter = new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES);
            Map<String, Object> node = formatter.node(thrown);
            if ("err".equals(node.get("t"))) {
                return EvalOutcome.ofError(String.valueOf(node.get("name")),
                        String.valueOf(node.get("msg")),
                        String.valueOf(node.get("stack")), durationMs);
            }
            // Non-Error rejection reason (a string, a number, ...): generic wrapper.
            return EvalOutcome.ofError("Error", ConsoleFormatter.previewOf(node), null, durationMs);
        }

        /** Renders a host-visible polyglot failure as an error outcome (loop thread). */
        private EvalOutcome polyglotErrorOutcome(PolyglotException pe, long startNanos) {
            try {
                Object guest = pe.getGuestObject();
                if (guest != null) {
                    Value asValue = context.asValue(guest);
                    if (asValue != null && !asValue.isNull()) {
                        return errorOutcome(asValue, startNanos);
                    }
                }
            } catch (Throwable ignored) {
                // fall through to the frame-derived rendering
            }
            String message = pe.getMessage() == null ? "eval failed" : pe.getMessage();
            return EvalOutcome.ofError(fallbackErrorName(message), message,
                    synthesizedStack(pe), elapsedMs(startNanos));
        }

        /** Splits a "TypeError: ..." style message into name and message. */
        private static String fallbackErrorName(String message) {
            int split = message.indexOf(": ");
            if (split > 0 && split <= 32 && !message.substring(0, split).contains(" ")) {
                return message.substring(0, split);
            }
            return "Error";
        }

        /** Guest frames of a polyglot failure as a displayable stack string. */
        private static String synthesizedStack(PolyglotException pe) {
            StringBuilder sb = new StringBuilder();
            for (var frame : pe.getPolyglotStackTrace()) {
                if (!frame.isGuestFrame() || frame.getSourceLocation() == null) {
                    continue;
                }
                var location = frame.getSourceLocation();
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("    at ")
                        .append(frame.getRootName() == null || frame.getRootName().isEmpty()
                                ? "<anonymous>" : frame.getRootName())
                        .append(" (").append(location.getSource().getName())
                        .append(':').append(location.getStartLine())
                        .append(':').append(location.getStartColumn()).append(')');
                if (sb.length() > 2000) {
                    break;
                }
            }
            return sb.toString();
        }

        private static long elapsedMs(long startNanos) {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        }

        /** Reads a global left behind by executed modules (observation surface). */
        Value global(String name) throws ScriptError {
            return onLoop(() -> context.getBindings("js").getMember(name));
        }

        /**
         * Installs the console global: a host emit proxy (value formatting,
         * rate limiting, call-site resolution, dispatch) handed to the GLUE
         * wrappers that define the browser-shaped methods. Runs on the loop
         * thread; the proxy body therefore also runs on the loop when a
         * script emits, and ScriptConsole.dispatch hands the finished event
         * to the ordered pipeline thread so sinks never block the loop.
         */
        void installConsole() {
            Value emit = context.asValue((ProxyExecutable) arguments -> {
                emitConsole(arguments);
                return null;
            });
            glue.getMember("__neoInstallConsole").execute(emit);
        }

        /** One console emission from the GLUE wrapper (loop thread). */
        private void emitConsole(Value[] arguments) {
            ConsoleRateLimiter.Decision decision = rateLimiter.acquire();
            if (!decision.allowed() && decision.suppressedCount() == 0) {
                return;
            }
            String level = stringArg(arguments, 0, "log");
            String kind = stringArg(arguments, 1, "log");
            Value jsArgs = arguments.length > 2 && arguments[2] != null ? arguments[2] : null;
            Value extra = arguments.length > 3 && arguments[3] != null ? arguments[3] : null;
            String stack = stringArg(arguments, 4, "");
            ConsoleLocation.Location location = ConsoleLocation.parse(stack, moduleRoot);

            if (decision.suppressedCount() > 0) {
                Map<String, Object> notice = new LinkedHashMap<>();
                notice.put("t", "str");
                notice.put("v", "console: " + decision.suppressedCount()
                        + " message(s) suppressed by rate limit");
                ScriptConsole.dispatch(new ScriptConsoleEvent(owner.name(), "log", "warn",
                        location.file(), location.line(), location.column(), depthOf(extra),
                        List.of(notice), null, null, null, null, System.currentTimeMillis()));
            }
            if (!decision.allowed()) {
                return;
            }

            ConsoleFormatter formatter = new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES);
            List<Object> args = new ArrayList<>();
            if (jsArgs != null && jsArgs.hasArrayElements()) {
                long shown = Math.min(jsArgs.getArraySize(), ConsoleFormatter.MAX_ARGS);
                for (long i = 0; i < shown; i++) {
                    args.add(formatter.node(jsArgs.getArrayElement(i)));
                }
            }

            boolean wantsStack = kind.equals("trace") || kind.equals("assert");
            ScriptConsole.dispatch(new ScriptConsoleEvent(owner.name(), kind, level,
                    location.file(), location.line(), location.column(), depthOf(extra),
                    args,
                    stringOf(extra, "label"), intOf(extra, "count"), doubleOf(extra, "ms"),
                    wantsStack ? ConsoleFormatter.capStack(stack) : null,
                    System.currentTimeMillis()));
        }

        /** Extra-object member readers (GLUE sends depth/label/count/ms). */
        private static int depthOf(Value extra) {
            try {
                if (extra != null && extra.hasMember("depth")) {
                    return extra.getMember("depth").asInt();
                }
            } catch (RuntimeException ignored) {
                // fall through to the default
            }
            return 0;
        }

        private static String stringOf(Value extra, String name) {
            try {
                if (extra != null && extra.hasMember(name)) {
                    Value member = extra.getMember(name);
                    if (member != null && !member.isNull() && member.isString()) {
                        return member.asString();
                    }
                }
            } catch (RuntimeException ignored) {
                // fall through to null
            }
            return null;
        }

        private static Integer intOf(Value extra, String name) {
            try {
                if (extra != null && extra.hasMember(name)) {
                    Value member = extra.getMember(name);
                    if (member != null && member.isNumber()) {
                        return member.asInt();
                    }
                }
            } catch (RuntimeException ignored) {
                // fall through to null
            }
            return null;
        }

        private static Double doubleOf(Value extra, String name) {
            try {
                if (extra != null && extra.hasMember(name)) {
                    Value member = extra.getMember(name);
                    if (member != null && member.isNumber()) {
                        return member.asDouble();
                    }
                }
            } catch (RuntimeException ignored) {
                // fall through to null
            }
            return null;
        }

        private static String stringArg(Value[] arguments, int index, String fallback) {
            try {
                if (index < arguments.length && arguments[index] != null && arguments[index].isString()) {
                    return arguments[index].asString();
                }
            } catch (RuntimeException ignored) {
                // fall through to the fallback
            }
            return fallback;
        }

        /** Guest-side Error factory for binding rejections (host throwables cannot cross). */
        Value newGuestError(String message) throws ScriptError {
            return onLoop(() -> glue.getMember("__neoNewError").execute(message));
        }

        /** Guest Error factory for code already running inside the context (loop-thread bindings). */
        Value newGuestErrorHere(String message) {
            return glue.getMember("__neoNewError").execute(message);
        }

        @Override
        public Map<String, Object> readExportedData(String exportName) throws ScriptError {
            return onLoop(() -> {
                if (lastNamespace == null) {
                    return Map.of();
                }
                Value v = lastNamespace.getMember(exportName);
                if (v == null || v.isNull()) {
                    return Map.of();
                }
                // Snapshot inside the loop: the mapped map/list are live
                // polyglot views that must not escape to other threads.
                @SuppressWarnings("unchecked")
                Map<String, Object> view = (Map<String, Object>) v.as(Map.class);
                Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : view.entrySet()) {
                    snapshot.put(entry.getKey(), snapshotValue(entry.getValue()));
                }
                return snapshot;
            });
        }

        /** Converts one guest value to its neutral Java form (scalars direct, objects/arrays enumerated). */
        private Object asNeutral(Value value) {
            if (value == null || value.isNull()) {
                return null;
            }
            try {
                if (value.hasMember("__neoRaw")) {
                    Value rawValue = value.getMember("__neoRaw");
                    Object raw = rawValue != null && rawValue.isHostObject()
                            ? rawValue.asHostObject() : null;
                    if (raw != null) {
                        return raw;
                    }
                }
            } catch (RuntimeException ignored) {
                // guest objects without the probe fall through to the normal mapping
            }
            if (value.isString()) {
                return value.asString();
            }
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isNumber()) {
                return value.as(Number.class);
            }
            if (value.canExecute()) {
                // Guest functions adapt to Consumer<Object>: invoking re-enters
                // the context loop and executes with the given host arguments.
                Value function = value;
                return (java.util.function.Consumer<Object[]>) args -> {
                    Value[] callArgs = new Value[args == null ? 0 : args.length];
                    for (int i = 0; i < callArgs.length; i++) {
                        callArgs[i] = context.asValue(args[i]);
                    }
                    try {
                        onLoop(() -> {
                            function.execute(callArgs);
                            return null;
                        });
                    } catch (ScriptError e) {
                        throw new RuntimeException(e);
                    }
                };
            }
            if (value.hasArrayElements()) {
                List<Object> copy = new ArrayList<>();
                for (long i = 0; i < value.getArraySize(); i++) {
                    copy.add(asNeutral(value.getArrayElement(i)));
                }
                return copy;
            }
            if (value.hasMembers()) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (String key : value.getMemberKeys()) {
                    copy.put(key, asNeutral(value.getMember(key)));
                }
                return copy;
            }
            return value.as(Object.class);
        }

        /** Recursively copies polyglot map/list views into plain Java collections. */
        private static Object snapshotValue(Object value) {
            if (value instanceof java.util.UUID uuid) {
                // Neutral surface: identifiers cross as strings, not host objects.
                return uuid.toString();
            }
            if (value != null && value.getClass().isRecord()) {
                // Event payload records: expose components as a neutral map so
                // the guest can read ev.message.text style paths.
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (java.lang.reflect.RecordComponent component :
                        value.getClass().getRecordComponents()) {
                    try {
                        component.getAccessor().setAccessible(true);
                        copy.put(component.getName(),
                                snapshotValue(component.getAccessor().invoke(value)));
                    } catch (ReflectiveOperationException ignored) {
                        copy.put(component.getName(), null);
                    }
                }
                return copy;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), snapshotValue(entry.getValue()));
                }
                return copy;
            }
            if (value instanceof List<?> list) {
                List<Object> copy = new java.util.ArrayList<>(list.size());
                for (Object element : list) {
                    copy.add(snapshotValue(element));
                }
                return copy;
            }
            return value;
        }

        @Override
        public java.util.Optional<Runnable> readExportedFunction(String exportName) throws ScriptError {
            return onLoop(() -> {
                if (lastNamespace == null) {
                    return java.util.Optional.<Runnable>empty();
                }
                Value v = lastNamespace.getMember(exportName);
                if (v == null || v.isNull() || !v.canExecute()) {
                    return java.util.Optional.<Runnable>empty();
                }
                // HostAccess.NONE forbids functional-interface mapping, so the
                // handle re-enters the context loop and executes the guest value.
                return java.util.Optional.of(() -> onLoop(() -> {
                    v.execute();
                    return null;
                }));
            });
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> invokeExport(String exportName, Object payload) {
            var done = new java.util.concurrent.CompletableFuture<Boolean>();
            owner.loop().execute(() -> {
                try {
                    if (lastNamespace == null) {
                        done.complete(false);
                        return;
                    }
                    Value v = lastNamespace.getMember(exportName);
                    if (v == null || v.isNull() || !v.canExecute()) {
                        done.complete(false);
                        return;
                    }
                    v.execute(payload == null ? undefinedValue() : asGuestValue(payload));
                    done.complete(true);
                } catch (PolyglotException e) {
                    done.completeExceptionally(translate("script", e));
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            });
            return done;
        }

        @Override
        public void close() {
            context.close();
        }

        /** Runs one piece of guest work on the context loop, blocking the caller. */
        private <T> T onLoop(Supplier<T> work) throws ScriptError {
            if (owner.loop().isOnLoop()) {
                try {
                    return work.get();
                } catch (PolyglotException e) {
                    throw translate("script", e);
                } catch (Throwable t) {
                    throw new ScriptError(t.getMessage(), t);
                }
            }
            CompletableFuture<T> done = new CompletableFuture<>();
            owner.loop().execute(() -> {
                try {
                    done.complete(work.get());
                } catch (PolyglotException e) {
                    done.completeExceptionally(translate("script", e));
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            });
            try {
                return done.get(LOOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ScriptError scriptError) {
                    throw scriptError;
                }
                throw new ScriptError(cause.getMessage(), cause);
            } catch (TimeoutException e) {
                throw new ScriptError("context loop did not settle within " + LOOP_TIMEOUT_SECONDS + "s");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScriptError("interrupted while waiting on the context loop", e);
            }
        }

        /** Builds a plain JS object tree from the binding map through the Value API. */
        private Value buildTree(Map<String, Object> bindings, Value makeAsync) {
            Value tree = context.eval("js", "({})");
            for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nested) {
                    tree.putMember(entry.getKey(), buildTree(unchecked(nested), makeAsync));
                } else if (value instanceof ModuleBinding module) {
                    tree.putMember(entry.getKey(), context.asValue(moduleProxy(module, makeAsync)));
                } else if (value instanceof AsyncBinding async) {
                    tree.putMember(entry.getKey(), makeAsync.execute(executable(arguments -> {
                        Value jsArgs = arguments[0];
                        Value[] unpacked = new Value[(int) jsArgs.getArraySize()];
                        for (int i = 0; i < unpacked.length; i++) {
                            unpacked[i] = jsArgs.getArrayElement(i);
                        }
                        async.execute(unpacked, arguments[1], arguments[2]);
                        return null;
                    })));
                } else if (value instanceof Supplier<?> supplier) {
                    tree.putMember(entry.getKey(), context.asValue(
                            (ProxyExecutable) arguments -> supplier.get()));
                } else {
                    tree.putMember(entry.getKey(), context.asValue(value));
                }
            }
            return tree;
        }

        /**
         * Renders a module binding as a read-only guest object: bean getters
         * read live on every property access, functions cross as proxies
         * (async ones wrapped into promise factories), and putMember throws
         * so the proxy has no write path.
         */
        private org.graalvm.polyglot.proxy.ProxyObject moduleProxy(ModuleBinding binding, Value makeAsync) {
            return new org.graalvm.polyglot.proxy.ProxyObject() {
                @Override
                public Object getMember(String name) {
                    ModuleBinding.BindingMember member = binding.members().get(name);
                    if (member == null) {
                        throw new IllegalStateException("unknown binding member: " + name);
                    }
                    if (member instanceof ModuleBinding.GuestTypeMember typeMember) {
                        return guestClassFor(typeMember.cellClass());
                    }
                    if (member instanceof ModuleBinding.PropertyMember property) {
                        try {
                            return asGuestValue(property.get());
                        } catch (PolyglotException e) {
                            throw e;
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    }
                    if (member instanceof ModuleBinding.FunctionMember function) {
                        if (function.isAsync()) {
                            return makeAsync.execute(executable(arguments ->
                                    invokeAsync(function, arguments)));
                        }
                        return executable(arguments -> {
                            try {
                                return asGuestValue(function.invoke(convertArgs(function, arguments)));
                            } catch (PolyglotException e) {
                                throw e;
                            } catch (Throwable t) {
                                throw new RuntimeException(t);
                            }
                        });
                    }
                    if (member instanceof ModuleBinding.DeniedMember denied) {
                        return executable(arguments -> {
                            throw new PermissionError("permission '" + denied.permission()
                                    + "' is not declared in this script's meta.permissions");
                        });
                    }
                    if (member instanceof ModuleBinding.ConstantMember constant) {
                        return constant.value();
                    }
                    throw new IllegalStateException("unrenderable binding member");
                }

                @Override
                public boolean hasMember(String name) {
                    return binding.members().containsKey(name);
                }

                @Override
                public Object getMemberKeys() {
                    // A host array would require array-access grants under
                    // HostAccess.NONE; a proxy array crosses cleanly.
                    String[] keys = binding.members().keySet().toArray(new String[0]);
                    return new org.graalvm.polyglot.proxy.ProxyArray() {
                        @Override
                        public long getSize() {
                            return keys.length;
                        }

                        @Override
                        public Object get(long index) {
                            return keys[(int) index];
                        }

                        @Override
                        public void set(long index, Value value) {
                            throw new UnsupportedOperationException("member keys are read-only");
                        }
                    };
                }

                @Override
                public void putMember(String key, Value value) {
                    throw new UnsupportedOperationException(
                            "module bindings are read-only: " + key);
                }
            };
        }

        /**
         * Prepares a facade result for the guest: plain-data containers are
         * snapshotted (polyglot views must not escape) and wrapped as proxies
         * so the guest can read them under HostAccess.NONE.
         */
        private Value asGuestValue(Object result) {
            Object plain = snapshotValue(result);
            if (plain == null) {
                // ES surface rule: an absent value reads as undefined, never
                // host null (missing handles/properties per the facade spec).
                return undefinedValue();
            }
            if (plain instanceof ink.tuanzi.neomacros.core.api.java.JavaInterop interop) {
                return interopProxy(interop);
            }
            if (plain instanceof byte[] bytes) {
                List<Object> numbers = new ArrayList<>(bytes.length);
                for (byte entry : bytes) {
                    numbers.add((int) entry);
                }
                return glueMember("__neoBytes")
                        .execute(org.graalvm.polyglot.proxy.ProxyArray.fromList(numbers));
            }
            if (plain instanceof ink.tuanzi.neomacros.core.api.ScriptHandle handle) {
                return guestHandle(handle);
            }
            if (plain instanceof java.util.Map<?, ?> map) {
                // Plain Java maps are not member-readable under HostAccess.NONE;
                // a ProxyObject exposes keys as members the guest can read.
                // Nested containers recurse, or their hash entries would stay
                // invisible to plain JS property access.
                Map<String, Object> source = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    source.put(String.valueOf(entry.getKey()),
                            guestReadable(entry.getValue()));
                }
                return context.asValue(org.graalvm.polyglot.proxy.ProxyObject.fromMap(source));
            }
            if (plain instanceof java.util.List<?> list) {
                List<Object> copy = new ArrayList<>(list.size());
                for (Object element : list) {
                    copy.add(guestReadable(element));
                }
                return context.asValue(
                        org.graalvm.polyglot.proxy.ProxyArray.fromList(copy));
            }
            if (plain instanceof String || plain instanceof Boolean
                    || plain instanceof Number || plain instanceof Character) {
                return context.asValue(plain);
            }
            // Any other host object crossing the boundary wraps as an interop
            // proxy: the guest can read its members under HostAccess.NONE
            // (escape-layer auto-wrapping; scalars above stay plain).
            return interopProxy((ink.tuanzi.neomacros.core.api.java.JavaObject)
                    ink.tuanzi.neomacros.core.api.java.JavaInterop.wrap(plain));
        }

        /** Converts one container level into its guest-readable proxy form. */
        private Value guestReadable(Object value) {
            Object plain = snapshotValue(value);
            if (plain instanceof java.util.Map<?, ?> map) {
                Map<String, Object> source = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    source.put(String.valueOf(entry.getKey()),
                            guestReadable(entry.getValue()));
                }
                return context.asValue(org.graalvm.polyglot.proxy.ProxyObject.fromMap(source));
            }
            if (plain instanceof java.util.List<?> list) {
                List<Object> copy = new ArrayList<>(list.size());
                for (Object element : list) {
                    copy.add(guestReadable(element));
                }
                return context.asValue(org.graalvm.polyglot.proxy.ProxyArray.fromList(copy));
            }
            return asGuestValue(plain);
        }

        /**
         * Renders a Java-interop object as a dynamic guest proxy: member
         * reads resolve per call through the interop contract (fields as
         * values, methods as callables, {@code new} on classes), writes
         * reach public fields, and results marshal through the neutral path
         * with automatic {@code JavaObject} wrapping.
         */
        private Value interopProxy(ink.tuanzi.neomacros.core.api.java.JavaInterop target) {
            org.graalvm.polyglot.proxy.ProxyObject proxy = new org.graalvm.polyglot.proxy.ProxyObject() {
                @Override
                public Object getMember(String name) {
                    if (name.equals("className")) {
                        return context.asValue(target.className());
                    }
                    if (name.equals("instanceOf")) {
                        return executable(arguments -> context.asValue(
                                target.instanceOf(stringArg(arguments, 0, null))));
                    }
                    if (name.equals("__neoRaw")) {
                        // Hidden probe: hands the raw host value to the host
                        // side when an interop proxy crosses back into Java.
                        return target.rawValue();
                    }
                    var callable = target.readCallable(name);
                    if (callable != null) {
                        return executable(arguments -> asInteropResult(
                                callable.apply(interopArgs(arguments))));
                    }
                    Object value = target.readValue(name);
                    return value == null ? undefinedValue() : asInteropResult(value);
                }

                @Override
                public boolean hasMember(String name) {
                    return name.equals("className") || name.equals("instanceOf")
                            || name.equals("__neoRaw") || target.hasMember(name);
                }

                @Override
                public Object getMemberKeys() {
                    String[] keys = {"className", "instanceOf"};
                    return new org.graalvm.polyglot.proxy.ProxyArray() {
                        @Override
                        public long getSize() {
                            return keys.length;
                        }

                        @Override
                        public Object get(long index) {
                            return keys[(int) index];
                        }

                        @Override
                        public void set(long index, Value value) {
                            throw new UnsupportedOperationException("member keys are read-only");
                        }
                    };
                }

                @Override
                public void putMember(String key, Value value) {
                    target.write(key, interopValue(value));
                }
            };
            return context.asValue(proxy);
        }

        /** Converts guest call arguments to their neutral Java form. */
        private Object[] interopArgs(Value[] arguments) {
            Object[] out = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                out[i] = asNeutral(arguments[i]);
            }
            return out;
        }

        /** Converts one guest value for a field write. */
        private Object interopValue(Value value) {
            return value == null || value.isNull() ? null : asNeutral(value);
        }

        /** Marshals one interop result for the guest. */
        private Value asInteropResult(Object value) {
            Object marshalled = ink.tuanzi.neomacros.core.api.java.JavaInterop.wrap(value);
            if (marshalled == null) {
                return undefinedValue();
            }
            if (marshalled instanceof ink.tuanzi.neomacros.core.api.java.JavaInterop interop) {
                return interopProxy(interop);
            }
            return asGuestValue(marshalled);
        }

        /**
         * Renders a live facade handle for the guest. Annotated handles
         * ({@code @ApiClass}) become instances of a real guest ES class
         * (built once per session): {@code instanceof} and user subclassing
         * work natively, getters read the backing member proxy live on every
         * access, and {@link Symbol#toStringTag} renders the script-visible
         * type name. Un-annotated handles keep the flat member-proxy
         * rendering (raw host objects are unreadable under the closed
         * host-access policy, so the proxy is the only readable surface).
         */
        private Value guestHandle(Object handle) {
            Class<?> type = handle.getClass();
            if (type.getAnnotation(ink.tuanzi.neomacros.doc.api.ApiClass.class) == null) {
                return handleProxy(handle);
            }
            Value constructor = guestClassFor(type);
            return constructor.newInstance(handleProxy(handle));
        }

        /** Defines (once, parents first) and returns the guest class for one cell type. */
        private Value guestClassFor(Class<?> type) {
            String name = guestNameOf(type);
            Value existing = guestClasses.get(name);
            if (existing != null) {
                return existing;
            }
            Class<?> parent = annotatedAncestor(type);
            if (parent != null) {
                guestClassFor(parent);
            }
            HandleShape shape = handleShape(type);
            Value constructor = glueMember("__neoDefineClass").execute(
                    name,
                    parent != null ? guestNameOf(parent) : null,
                    org.graalvm.polyglot.proxy.ProxyArray.fromList(
                            new ArrayList<Object>(shape.properties())),
                    org.graalvm.polyglot.proxy.ProxyArray.fromList(
                            new ArrayList<Object>(shape.methods())));
            guestClasses.put(name, constructor);
            return constructor;
        }

        /** The nearest {@code @ApiClass}-annotated ancestor class, or null. */
        private static Class<?> annotatedAncestor(Class<?> type) {
            Class<?> sup = type.getSuperclass();
            while (sup != null && sup != Object.class) {
                if (sup.getAnnotation(ink.tuanzi.neomacros.doc.api.ApiClass.class) != null) {
                    return sup;
                }
                sup = sup.getSuperclass();
            }
            return null;
        }

        /** The script-visible type name of one cell class. */
        private static String guestNameOf(Class<?> type) {
            var annotation = type.getAnnotation(ink.tuanzi.neomacros.doc.api.ApiClass.class);
            return annotation != null ? annotation.name() : type.getSimpleName();
        }

        /**
         * Renders a live facade handle as a guest-readable member proxy: bean
         * getters read as properties on every access (real-time per the
         * handle contract), zero-argument public methods cross as functions,
         * writes throw. Raw host objects are unreadable under the closed
         * host-access policy, so the proxy is the only readable surface.
         */
        private Value handleProxy(Object handle) {
            HandleShape shape = handleShape(handle.getClass());
            return context.asValue(new org.graalvm.polyglot.proxy.ProxyObject() {
                @Override
                public Object getMember(String name) {
                    java.lang.reflect.Executable member = shape.members().get(name);
                    if (member == null) {
                        return undefinedValue();
                    }
                    java.lang.reflect.Method method = (java.lang.reflect.Method) member;
                    if (shape.properties().contains(name)) {
                        // Bean getter: read live on every property access.
                        return invokeHandleMember(handle, method, name);
                    }
                    // Zero-arg action/method: cross as a callable so the guest
                    // invokes it explicitly (matching the generated .d.ts).
                    return executable(arguments -> invokeHandleMember(handle, method, name));
                }

                @Override
                public boolean hasMember(String name) {
                    return shape.members().containsKey(name);
                }

                @Override
                public Object getMemberKeys() {
                    String[] keys = shape.members().keySet().toArray(new String[0]);
                    return new org.graalvm.polyglot.proxy.ProxyArray() {
                        @Override
                        public long getSize() {
                            return keys.length;
                        }

                        @Override
                        public Object get(long index) {
                            return keys[(int) index];
                        }

                        @Override
                        public void set(long index, Value value) {
                            throw new UnsupportedOperationException("handle members are read-only");
                        }
                    };
                }

                @Override
                public void putMember(String key, Value value) {
                    throw new UnsupportedOperationException("handle members are read-only: " + key);
                }
            });
        }

        /** Invokes one handle member and renders the result for the guest. */
        private Value invokeHandleMember(Object handle, java.lang.reflect.Method method,
                                         String name) {
            try {
                return asGuestValue(method.invoke(handle));
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("handle member failed: " + name, cause);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("handle member not accessible: " + name, e);
            }
        }

        /** Declared script surface of one handle class: member lookup plus the property/method split. */
        private record HandleShape(Map<String, java.lang.reflect.Executable> members,
                                   List<String> properties, List<String> methods) {
        }

        private static final Map<Class<?>, HandleShape> HANDLE_SHAPES = new ConcurrentHashMap<>();

        private static HandleShape handleShape(Class<?> type) {
            return HANDLE_SHAPES.computeIfAbsent(type, cls -> {
                Map<String, java.lang.reflect.Executable> members = new LinkedHashMap<>();
                List<String> properties = new ArrayList<>();
                List<String> methods = new ArrayList<>();
                for (java.lang.reflect.Method method : cls.getMethods()) {
                    if (method.getDeclaringClass() == Object.class) {
                        continue;
                    }
                    String name = method.getName();
                    int params = method.getParameterCount();
                    boolean async = CompletableFuture.class
                            .isAssignableFrom(method.getReturnType());
                    if (params == 0 && !async && (name.startsWith("get") || name.startsWith("is"))
                            && !name.equals("getClass")) {
                        String property = name.startsWith("is")
                                ? decapitalize(name.substring(2))
                                : decapitalize(name.substring(3));
                        if (members.putIfAbsent(property, method) == null) {
                            properties.add(property);
                        }
                    } else if (params == 0 && !name.equals("getClass")) {
                        if (members.putIfAbsent(name, method) == null) {
                            methods.add(name);
                        }
                    }
                }
                return new HandleShape(Map.copyOf(members),
                        List.copyOf(properties), List.copyOf(methods));
            });
        }

        /** Lower-cases the first character of one member name suffix. */
        private static String decapitalize(String name) {
            if (name.isEmpty()) {
                return name;
            }
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        /** The guest-side {@code undefined} value (resolved once per session). */
        private Value undefinedValue() {
            if (cachedUndefined == null) {
                cachedUndefined = context.getBindings("js").getMember("undefined");
            }
            return cachedUndefined;
        }

        /** Converts guest arguments to the facade method's declared Java types. */
        private Object[] convertArgs(ModuleBinding.FunctionMember function, Value[] arguments) {
            Class<?>[] types = function.parameterTypes();
            Object[] out = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                Value argument = i < arguments.length ? arguments[i] : null;
                out[i] = argument == null ? null : asJava(argument, types[i]);
            }
            return out;
        }

        /** Converts one guest value to the declared Java type. */
        private Object asJava(Value value, Class<?> type) {
            if (type == String.class) {
                return value.asString();
            }
            if (type == boolean.class) {
                return value.asBoolean();
            }
            if (type == int.class) {
                return value.asInt();
            }
            if (type == long.class) {
                return value.asLong();
            }
            if (type == double.class) {
                return value.asDouble();
            }
            if (type == float.class) {
                return (float) value.asDouble();
            }
            if (type == java.util.UUID.class) {
                return java.util.UUID.fromString(value.asString());
            }
            if (type.isInterface() && value.canExecute()) {
                return adaptFunction(value, type);
            }
            if (type == Object.class) {
                // Plain-data snapshot: guest objects become neutral maps/lists
                // (Object.class mapping would hand back the raw guest object).
                return snapshotValue(asNeutral(value));
            }
            throw new ScriptError("cannot convert argument to " + type.getName());
        }

        /**
         * Adapts a guest function to a host functional interface: invoking the
         * proxy re-enters the context loop and executes the guest value.
         * Supported return types are void, boolean, and object (mapped to a
         * neutral map).
         */
        private Object adaptFunction(Value function, Class<?> type) {
            ClassLoader loader = type.getClassLoader() != null
                    ? type.getClassLoader() : GraalJsEngine.class.getClassLoader();
            return java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{type},
                    (proxy, method, args) -> {
                        Value[] callArgs = new Value[args == null ? 0 : args.length];
                        for (int i = 0; i < callArgs.length; i++) {
                            callArgs[i] = asGuestValue(args[i]);
                        }
                        Value result = onLoop(() -> function.execute(callArgs));
                        if (method.getReturnType() == void.class) {
                            return null;
                        }
                        if (method.getReturnType() == boolean.class) {
                            // JS falsiness: a handler without a return value
                            // reads as false (no cancel / no match).
                            return result.isBoolean() && result.asBoolean();
                        }
                        return result.as(Map.class);
                    });
        }

        /** Invokes an async facade function and settles the promise on the loop. */
        private Object invokeAsync(ModuleBinding.FunctionMember function, Value[] arguments) {
            // arguments = [jsArgsArray, resolve, reject]; only [0] carries method params.
            Value jsArgs = arguments[0];
            Value resolve = arguments[1];
            Value reject = arguments[2];
            Value[] unpacked = new Value[(int) jsArgs.getArraySize()];
            for (int i = 0; i < unpacked.length; i++) {
                unpacked[i] = jsArgs.getArrayElement(i);
            }
            CompletableFuture<?> future;
            try {
                future = (CompletableFuture<?>) function.invoke(convertArgs(function, unpacked));
            } catch (PolyglotException e) {
                throw e;
            } catch (Throwable t) {
                reject.execute(newGuestErrorHere(String.valueOf(t.getMessage())));
                return null;
            }
            future.whenComplete((value, failure) -> owner.loop().execute(() -> {
                if (failure != null) {
                    reject.execute(newGuestErrorHere(String.valueOf(failure)));
                } else if (value == null) {
                    resolve.execute();
                } else {
                    resolve.execute(value);
                }
            }));
            return null;
        }

        private Value glueMember(String name) {
            Value member = glue.getMember(name);
            if (member == null) {
                throw new IllegalStateException("engine glue member missing: " + name);
            }
            return member;
        }

        private String guestMessage(Value guestValue) {
            try {
                if (guestValue.isHostObject()) {
                    Object host = guestValue.asHostObject();
                    if (host instanceof Throwable t) {
                        var sw = new java.io.StringWriter();
                        t.printStackTrace(new java.io.PrintWriter(sw));
                        return sw.toString();
                    }
                    return String.valueOf(host);
                }
                Value message = guestValue.getMember("message");
                if (message != null && !message.isNull() && message.isString()) {
                    return message.asString();
                }
            } catch (Throwable ignored) {
                // fall through to the generic rendering below
            }
            return String.valueOf(guestValue);
        }

        private String guestMessageLegacy(Value guestValue) {
            Value message = guestValue.getMember("message");
            if (message != null && !message.isNull() && message.isString()) {
                return message.asString();
            }
            return guestValue.toString();
        }
    }

    /** Proxy executable from a lambda with throw-through of runtime failures. */
    private static ProxyExecutable executable(java.util.function.Function<Value[], Object> body) {
        return arguments -> body.apply(arguments);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unchecked(Map<?, ?> nested) {
        return (Map<String, Object>) nested;
    }
}
