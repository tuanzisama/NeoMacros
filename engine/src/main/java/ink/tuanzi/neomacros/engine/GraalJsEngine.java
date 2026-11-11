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
 * file system confined to the script directory, the {@code neo} global is
 * injected deeply frozen, and {@link AsyncBinding} members surface as JS
 * Promise factories.
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
            globalThis.__neoBaseEvent = class BaseEvent {
                get type() { return this.__neoType; }
                get timestamp() { return this.__neoTimestamp; }
                get cancellable() { return this.__neoCancellable === true; }
                get direction() { return this.__neoDirection; }
                toJSON() {
                    const out = {};
                    for (const key of Object.keys(this)) {
                        out[key] = this[key];
                    }
                    return out;
                }
                toString() {
                    return '[' + this.constructor.name + ' type=' + this.type + ']';
                }
            };
            // Direction middle classes: C2S input interception, S2C display
            // interception, local state observation (never cancellable).
            globalThis.__neoDirectionClasses = {};
            globalThis.__neoDirectionClass = function (direction) {
                const table = globalThis.__neoDirectionClasses;
                if (table[direction] !== undefined) {
                    return table[direction];
                }
                let cls;
                if (direction === 'c2s') {
                    cls = class InputEvent extends globalThis.__neoBaseEvent {
                    };
                } else if (direction === 's2c') {
                    cls = class DisplayEvent extends globalThis.__neoBaseEvent {
                    };
                } else {
                    cls = class StateEvent extends globalThis.__neoBaseEvent {
                    };
                }
                const className = direction === 'c2s' ? 'InputEvent'
                        : direction === 's2c' ? 'DisplayEvent' : 'StateEvent';
                Object.defineProperty(cls, 'name', { value: className, writable: false });
                table[direction] = cls;
                return cls;
            };
            globalThis.__neoEventClasses = {};
            globalThis.__neoEventClassName = function (name) {
                const camel = name.replace(/[:_-]+(.)/g, function (m, ch) {
                    return ch.toUpperCase();
                });
                return camel.charAt(0).toUpperCase() + camel.slice(1) + 'Event';
            };
            globalThis.__neoEventClass = function (name, cancellable, direction) {
                const table = globalThis.__neoEventClasses;
                if (table[name] !== undefined) {
                    return table[name];
                }
                const cls = class extends __neoDirectionClass(direction) {
                };
                Object.defineProperty(cls, 'name', {
                    value: __neoEventClassName(name),
                    writable: false
                });
                table[name] = cls;
                return cls;
            };
            // Attaches the event class prototype to one payload snapshot and
            // freezes it: the fields stay own enumerable properties (JSON,
            // destructuring, property access unchanged), the prototype adds
            // the type/timestamp/cancellable/direction introspection face.
            globalThis.__neoWrapEvent = function (name, cancellable, direction, timestamp, source) {
                if (source === undefined || source === null
                        || typeof source !== 'object') {
                    return source;
                }
                const obj = {};
                const keys = Object.keys(source);
                for (const key of keys) {
                    obj[key] = source[key];
                }
                const hidden = { writable: false, enumerable: false, configurable: false };
                Object.defineProperty(obj, '__neoType', { value: name, ...hidden });
                Object.defineProperty(obj, '__neoTimestamp', { value: timestamp, ...hidden });
                Object.defineProperty(obj, '__neoCancellable', {
                    value: cancellable === true, ...hidden
                });
                Object.defineProperty(obj, '__neoDirection', { value: direction, ...hidden });
                Object.setPrototypeOf(obj, __neoEventClass(name, cancellable, direction).prototype);
                Object.freeze(obj);
                return obj;
            };
            // Mutable sync-channel face: whitelisted payload fields become
            // accessor properties that commit writes through the host hooks;
            // other payload fields stay read-only (strict writes throw,
            // matching the d.ts readonly face). MONITOR deliveries are
            // readOnly: writes and cancel changes drop with one warning.
            globalThis.__neoWrapMutableEvent = function (name, cancellable, direction,
                                                        timestamp, source, mutableKeys,
                                                        hooks, readOnly) {
                if (source === undefined || source === null
                        || typeof source !== 'object') {
                    return source;
                }
                const obj = {};
                const store = {};
                const mutable = {};
                for (const key of Object.keys(source)) {
                    store[key] = source[key];
                    mutable[key] = false;
                }
                for (const key of mutableKeys) {
                    if (key in store) {
                        mutable[key] = true;
                    }
                }
                const hidden = { writable: false, enumerable: false, configurable: false };
                Object.defineProperty(obj, '__neoType', { value: name, ...hidden });
                Object.defineProperty(obj, '__neoTimestamp', { value: timestamp, ...hidden });
                Object.defineProperty(obj, '__neoCancellable', {
                    value: cancellable === true, ...hidden
                });
                Object.defineProperty(obj, '__neoDirection', { value: direction, ...hidden });
                let warned = false;
                const warnReadOnly = function () {
                    if (!warned) {
                        warned = true;
                        console.warn('onSync monitor subscriptions are read-only;'
                                + ' modifications are dropped');
                    }
                };
                for (const key of Object.keys(store)) {
                    (function (k) {
                        Object.defineProperty(obj, k, {
                            enumerable: true,
                            configurable: false,
                            get: function () {
                                return store[k];
                            },
                            set: function (value) {
                                if (!mutable[k]) {
                                    throw new TypeError("cannot rewrite '" + k
                                            + "': field is not mutable on event " + name);
                                }
                                if (readOnly) {
                                    warnReadOnly();
                                    return;
                                }
                                store[k] = value;
                                hooks.write(k, value);
                            }
                        });
                    })(key);
                }
                Object.defineProperty(obj, 'cancel', {
                    value: function () {
                        if (readOnly) {
                            warnReadOnly();
                            return;
                        }
                        hooks.cancel();
                    },
                    writable: true,
                    enumerable: false,
                    configurable: true
                });
                Object.defineProperty(obj, 'cancelled', {
                    enumerable: false,
                    configurable: false,
                    get: function () {
                        return hooks.isCancelled() === true;
                    },
                    set: function (value) {
                        if (readOnly) {
                            warnReadOnly();
                            return;
                        }
                        hooks.setCancelled(value === true);
                    }
                });
                Object.setPrototypeOf(obj, __neoEventClass(name, cancellable, direction).prototype);
                return obj;
            };
            // One EventPriority constant: a frozen cell carrying its name and
            // the hidden host enum (__neoCell probe marshals it back to Java).
            globalThis.__neoPriorityCell = function (cell, name) {
                const obj = { name: name };
                const hidden = { writable: false, enumerable: false, configurable: false };
                Object.defineProperty(obj, '__neoCell', { value: cell, ...hidden });
                Object.defineProperty(obj, 'toString', {
                    value: function () {
                        return 'EventPriority.' + name;
                    },
                    writable: true,
                    enumerable: false,
                    configurable: true
                });
                Object.freeze(obj);
                return obj;
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
            globalThis.__neoBuildCell = null;
            globalThis.__neoBuildCellInstall = function (impl) {
                globalThis.__neoBuildCell = impl;
            };
            globalThis.__neoMakeCell = function (name, args) {
                return __neoBuildCell(name, args);
            };
            globalThis.__neoDefineClass = function (name, parentName, properties, methods, selfConstructing) {
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
                    constructor(...ctorArgs) {
                        super();
                        // configurable: a base constructor installs the cell
                        // first along the super() chain; the most-derived
                        // constructor re-defines it with the real value. The
                        // factory name is the defining closure name so user
                        // subclasses build the inherited cell type.
                        // Self-constructing classes: a single object argument
                        // is the engine re-rendering an existing handle
                        // (asGuestValue hands the cell back for chaining) —
                        // reuse it; scalar arguments are a script-side
                        // construction and route to the host factory.
                        var cell = selfConstructing
                            ? (ctorArgs.length === 1 && ctorArgs[0] !== null
                                && typeof ctorArgs[0] === 'object'
                                ? ctorArgs[0]
                                : __neoMakeCell(name, ctorArgs))
                            : ctorArgs[0];
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
                    if (method === 'toJSON') {
                        // Cell toJSON hands back a JSON string; parse it so
                        // JSON.stringify renders the plain value (a raw
                        // string would be double-encoded).
                        C.prototype[method] = function (...args) {
                            const value = readCell(this)[method](...args);
                            return typeof value === 'string' ? JSON.parse(value) : value;
                        };
                        continue;
                    }
                    C.prototype[method] = function (...args) {
                        return readCell(this)[method](...args);
                    };
                }
                Object.defineProperty(C.prototype, Symbol.toStringTag, {
                    value: name,
                    configurable: true
                });
                // The constructor identity must equal the guest name so the
                // self-constructing factory resolves by new.target.name.
                Object.defineProperty(C, 'name', {
                    value: name,
                    writable: false,
                    configurable: true
                });
                table[name] = C;
                return C;
            };
            """;

    private final List<GraalJsSession> sessions = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    /** Creates the engine. */
    public GraalJsEngine() {
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
        private final Map<String, ink.tuanzi.neomacros.core.api.SelfConstructing> cellFactories =
                new ConcurrentHashMap<>();

        private GraalJsSession(ScriptContext owner, Path moduleRoot) {
            this.owner = owner;
            this.moduleRoot = moduleRoot;
            this.context = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.NONE)
                    .allowIO(IOAccess.newBuilder()
                            .fileSystem(new ScriptSandboxFileSystem(moduleRoot))
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
                if (value.hasMember("__neoCell")) {
                    Value cellValue = value.getMember("__neoCell");
                    if (cellValue != null && cellValue.isHostObject()) {
                        return cellValue.asHostObject();
                    }
                }
            } catch (RuntimeException ignored) {
                // probe misses fall through
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

        /** Recursively copies host values into neutral plain data (shared
         * with the core event pipeline so both boundaries agree). */
        private static Object snapshotValue(Object value) {
            return ink.tuanzi.neomacros.core.event.Payloads.snapshot(value);
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
            // Leave the tracked set first so sampling never touches a
            // closing session; removal is idempotent with engine shutdown.
            sessions.remove(this);
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
                    tree.putMember(entry.getKey(),
                            context.asValue(bindingValue(entry.getKey(), module, makeAsync)));
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
         * Renders one binding tree entry: a binding whose single member
         * carries the binding's own name (root-promoted functions,
         * root-level classes) renders as that member's value itself, so
         * {@code neo.sleep} and {@code neo.Pos3D} are directly usable; any
         * other binding renders as a namespace object.
         */
        private Object bindingValue(String key, ModuleBinding binding, Value makeAsync) {
            if (binding.members().size() == 1) {
                Map.Entry<String, ModuleBinding.BindingMember> single =
                        binding.members().entrySet().iterator().next();
                if (single.getKey().equals(key)) {
                    return renderMember(single.getValue(), makeAsync);
                }
            }
            return moduleProxy(binding, makeAsync);
        }

        /**
         * Renders one binding member as its guest value: bean getters read
         * live on every access, functions cross as proxies (async ones
         * wrapped into promise factories), and class members build the
         * script-facing guest classes.
         */
        private Object renderMember(ModuleBinding.BindingMember member, Value makeAsync) {
            if (member instanceof ModuleBinding.GuestTypeMember typeMember) {
                return guestClassFor(typeMember.cellClass());
            }
            if (member instanceof ModuleBinding.EventTypeMember eventType) {
                // Guest event classes live in the glue; "BaseEvent" is the
                // shared base class itself, the direction names are the
                // middle classes (InputEvent / DisplayEvent / StateEvent).
                if (eventType.eventName().equals("BaseEvent")) {
                    return glueMember("__neoBaseEvent");
                }
                if (eventType.eventName().equals("InputEvent")) {
                    return glueMember("__neoDirectionClass").execute("c2s");
                }
                if (eventType.eventName().equals("DisplayEvent")) {
                    return glueMember("__neoDirectionClass").execute("s2c");
                }
                if (eventType.eventName().equals("StateEvent")) {
                    return glueMember("__neoDirectionClass").execute("local");
                }
                return glueMember("__neoEventClass")
                        .execute(eventType.eventName(), eventType.cancellable(),
                                eventType.direction().name().toLowerCase(java.util.Locale.ROOT));
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
                // EventPriority constants cross as frozen priority cells so
                // they marshal back to the host enum through the cell probe.
                if (constant.value() instanceof ink.tuanzi.neomacros.core.event.EventPriority priority) {
                    return glueMember("__neoPriorityCell")
                            .execute(context.asValue(priority), priority.name());
                }
                return constant.value();
            }
            throw new IllegalStateException("unrenderable binding member");
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
                    return renderMember(member, makeAsync);
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
         * Prepares a API result for the guest: plain-data containers are
         * snapshotted (polyglot views must not escape) and wrapped as proxies
         * so the guest can read them under HostAccess.NONE.
         */
        /** Unwraps event envelopes so handlers receive the event-class
         * prototype face; everything else crosses as before. */
        private Value guestValueOrEvent(Object value) {
            if (value instanceof ink.tuanzi.neomacros.core.event.EventPayloadEnvelope envelope) {
                return eventValue(envelope);
            }
            return asGuestValue(value);
        }

        /**
         * Renders one event payload with its BaseEvent prototype attached:
         * async deliveries freeze the snapshot face, sync deliveries render
         * the mutable face (whitelisted accessors + cancel) bound to the
         * envelope's hooks sink.
         */
        private Value eventValue(ink.tuanzi.neomacros.core.event.EventPayloadEnvelope envelope) {
            Value source = asGuestValue(envelope.payload());
            if (envelope.syncChannel()) {
                List<Object> mutableKeys = new ArrayList<>(envelope.mutableKeys());
                return glueMember("__neoWrapMutableEvent")
                        .execute(envelope.eventName(), envelope.cancellable(),
                                envelope.direction().name().toLowerCase(java.util.Locale.ROOT),
                                envelope.timestamp(), source,
                                org.graalvm.polyglot.proxy.ProxyArray.fromList(mutableKeys),
                                syncHooksValue(envelope.hooks()), envelope.readOnly());
            }
            return glueMember("__neoWrapEvent")
                    .execute(envelope.eventName(), envelope.cancellable(),
                            envelope.direction().name().toLowerCase(java.util.Locale.ROOT),
                            envelope.timestamp(), source);
        }

        /** Host-side hook executables bound to one sync delivery's mutation sink. */
        private Value syncHooksValue(ink.tuanzi.neomacros.core.event.SyncEventHooks hooks) {
            Map<String, Object> members = new LinkedHashMap<>();
            members.put("write", executable(arguments -> {
                hooks.write(stringArg(arguments, 0, null), asNeutral(
                        arguments.length > 1 ? arguments[1] : null));
                return null;
            }));
            members.put("cancel", executable(arguments -> {
                hooks.cancel();
                return null;
            }));
            members.put("isCancelled", executable(arguments -> hooks.isCancelled()));
            members.put("setCancelled", executable(arguments -> {
                Value value = arguments.length > 0 ? arguments[0] : null;
                hooks.setCancelled(value != null && !value.isNull() && value.asBoolean());
                return null;
            }));
            return context.asValue(org.graalvm.polyglot.proxy.ProxyObject.fromMap(members));
        }

        private Value asGuestValue(Object result) {
            if (result instanceof ink.tuanzi.neomacros.core.event.EventPayloadEnvelope envelope) {
                return eventValue(envelope);
            }
            Object plain = snapshotValue(result);
            if (plain == null) {
                // ES surface rule: an absent value reads as undefined, never
                // host null (missing handles/properties per the API spec).
                return undefinedValue();
            }
            if (plain instanceof ink.tuanzi.neomacros.core.api.java.JavaInterop interop) {
                return interopProxy(interop);
            }
            if (plain instanceof ink.tuanzi.neomacros.core.event.EventPriority priority) {
                return glueMember("__neoPriorityCell")
                        .execute(context.asValue(priority), priority.name());
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

        /** Converts guest call arguments to the declared parameter types. */
        private Object[] convertArguments(Value[] arguments, Class<?>[] parameterTypes) {
            Object[] out = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Value argument = i < arguments.length ? arguments[i] : null;
                out[i] = argument == null || argument.isNull() ? null
                        : convertArgument(argument, parameterTypes[i]);
            }
            return out;
        }

        /** Converts one guest value to the declared parameter type (cells via probe). */
        private Object convertArgument(Value argument, Class<?> type) {
            Object host = hostCellOf(argument);
            if (host != null && type.isInstance(host)) {
                return host;
            }
            if (type == String.class) {
                return argument.asString();
            }
            if (type == double.class || type == Double.class) {
                return argument.asDouble();
            }
            if (type == float.class || type == Float.class) {
                return (float) argument.asDouble();
            }
            if (type == int.class || type == Integer.class) {
                return argument.asInt();
            }
            if (type == long.class || type == Long.class) {
                return argument.asLong();
            }
            if (type == boolean.class || type == Boolean.class) {
                return argument.asBoolean();
            }
            return asNeutral(argument);
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
         * Renders a live API handle for the guest. Annotated handles
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
            boolean selfConstructing =
                    ink.tuanzi.neomacros.core.api.SelfConstructing.class.isAssignableFrom(type);
            if (selfConstructing) {
                try {
                    var factory = (ink.tuanzi.neomacros.core.api.SelfConstructing)
                            type.getDeclaredConstructor().newInstance();
                    cellFactories.put(name, factory);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "self-constructing cell needs a no-arg constructor: " + name, e);
                }
                installCellBuilderOnce();
            }
            Value constructor = glueMember("__neoDefineClass").execute(
                    name,
                    parent != null ? guestNameOf(parent) : null,
                    org.graalvm.polyglot.proxy.ProxyArray.fromList(
                            new ArrayList<Object>(shape.properties())),
                    org.graalvm.polyglot.proxy.ProxyArray.fromList(
                            new ArrayList<Object>(shape.methods())),
                    selfConstructing);
            guestClasses.put(name, constructor);
            return constructor;
        }

        /** Installs the host-side cell builder (once per session). */
        private void installCellBuilderOnce() {
            if (cellBuilderInstalled) {
                return;
            }
            cellBuilderInstalled = true;
            glueMember("__neoBuildCellInstall").execute(executable(arguments -> {
                String name = arguments.length > 0 ? arguments[0].asString() : "";
                var factory = cellFactories.get(name);
                if (factory == null) {
                    throw new IllegalStateException("no self-constructing cell: " + name);
                }
                // The glue calls __neoBuildCell(name, ctorArgsArray): flatten
                // the tail array into the argument list (a direct spread form
                // stays supported).
                List<Value> call = new ArrayList<>();
                call.add(arguments.length > 0 ? arguments[0] : null);
                if (arguments.length == 2 && arguments[1] != null && arguments[1].hasArrayElements()) {
                    long size = arguments[1].getArraySize();
                    for (long i = 0; i < size; i++) {
                        call.add(arguments[1].getArrayElement(i));
                    }
                } else {
                    for (int i = 1; i < arguments.length; i++) {
                        call.add(arguments[i]);
                    }
                }
                Object[] converted = new Object[call.size() - 1];
                for (int i = 1; i < call.size(); i++) {
                    Value argument = call.get(i);
                    if (argument == null || argument.isNull()) {
                        converted[i - 1] = null;
                    } else if (argument.isNumber()) {
                        converted[i - 1] = argument.as(Number.class);
                    } else if (argument.isString()) {
                        converted[i - 1] = argument.asString();
                    } else if (argument.isBoolean()) {
                        converted[i - 1] = argument.asBoolean();
                    } else {
                        converted[i - 1] = null;
                    }
                }
                return handleProxy(factory.construct(converted));
            }));
        }

        private boolean cellBuilderInstalled;

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
         * Renders a live API handle as a guest-readable member proxy: bean
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
                    if (name.equals("__neoCell")) {
                        // Hidden probe: the raw cell for host-side conversion.
                        return handle;
                    }
                    java.lang.reflect.Executable member = shape.members().get(name);
                    if (member == null) {
                        return undefinedValue();
                    }
                    java.lang.reflect.Method method = (java.lang.reflect.Method) member;
                    if (shape.properties().contains(name) && method.getParameterCount() == 0) {
                        // Bean getter: read live on every property access.
                        return invokeHandleMember(handle, method, name);
                    }
                    // Action/method: cross as a callable so the guest invokes
                    // it explicitly; arguments convert via declared types.
                    // Futures settle as guest Promises (the same wrapper the
                    // module bindings use).
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (java.util.concurrent.CompletableFuture.class
                            .isAssignableFrom(method.getReturnType())) {
                        return glueMember("__neoMakeAsync").execute(executable(
                                arguments -> invokeHandleAsync(handle, method, name,
                                        parameterTypes, arguments)));
                    }
                    return executable(arguments -> invokeHandleMember(handle, method, name,
                            convertArguments(arguments, parameterTypes)));
                }

                @Override
                public boolean hasMember(String name) {
                    // "__neoCell" answers through getMember for host-side
                    // conversion probes; it stays hidden from getMemberKeys.
                    return name.equals("__neoCell") || shape.members().containsKey(name);
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

        /** Invokes one zero-arg handle member and renders the result for the guest. */
        private Value invokeHandleMember(Object handle, java.lang.reflect.Method method,
                                         String name) {
            return invokeHandleMember(handle, method, name, new Object[0]);
        }

        /** Invokes one handle member with converted arguments. */
        private Value invokeHandleMember(Object handle, java.lang.reflect.Method method,
                                         String name, Object[] arguments) {
            try {
                return asGuestValue(method.invoke(handle, arguments));
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

        /** Invokes one async handle member and settles the promise on the loop. */
        private Object invokeHandleAsync(Object handle, java.lang.reflect.Method method,
                String name, Class<?>[] parameterTypes, Value[] arguments) {
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
                future = (CompletableFuture<?>) method.invoke(handle,
                        convertArguments(unpacked, parameterTypes));
            } catch (PolyglotException e) {
                throw e;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                reject.execute(newGuestErrorHere(String.valueOf(
                        cause != null ? cause.getMessage() : "handle member failed: " + name)));
                return null;
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
                    resolve.execute(guestReadable(value));
                }
            }));
            return null;
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
                    if (method.getAnnotation(ink.tuanzi.neomacros.doc.api.ApiInternal.class) != null) {
                        // Internal surface: host-side helpers never cross to the
                        // guest (the docs pipeline filters the same members).
                        continue;
                    }
                    if (ink.tuanzi.neomacros.core.api.SelfConstructing.class
                            == method.getDeclaringClass()) {
                        // The factory hook runs host-side only.
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
                    } else if (!name.equals("getClass")) {
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

        /** Converts guest arguments to the API method's declared Java types. */
        private Object[] convertArgs(ModuleBinding.FunctionMember function, Value[] arguments) {
            Class<?>[] types = function.parameterTypes();
            Object[] out = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                Value argument = i < arguments.length ? arguments[i] : null;
                out[i] = argument == null ? null : asJava(argument, types[i]);
            }
            return out;
        }

        /**
         * Unwraps a guest handle to its host cell: raw handle proxies answer
         * the {@code __neoCell} probe directly, while guest class instances
         * carry the member proxy under {@code __cell} — that proxy answers
         * {@code __neoCell} with the raw handle, so unwrap one extra level.
         * Returns null for plain guest values.
         */
        private Object hostCellOf(Value value) {
            try {
                Value level1 = probeValue(value);
                if (level1 == null) {
                    return null;
                }
                if (level1.isHostObject()) {
                    Object host = level1.asHostObject();
                    if (host != null && !(host instanceof org.graalvm.polyglot.proxy.Proxy)) {
                        return host;
                    }
                }
                // The probe returned the member proxy itself (guest-instance
                // path): ask the proxy for its raw cell one level down.
                Value level2 = probeValue(level1);
                if (level2 != null && level2.isHostObject()) {
                    Object host = level2.asHostObject();
                    if (host != null && !(host instanceof org.graalvm.polyglot.proxy.Proxy)) {
                        return host;
                    }
                }
            } catch (RuntimeException ignored) {
                // plain guest values without the probe
            }
            return null;
        }

        /** The value under the first present cell probe, or null. */
        private Value probeValue(Value value) {
            for (String probe : new String[]{"__neoCell", "__cell"}) {
                if (value.hasMember(probe)) {
                    Value cell = value.getMember(probe);
                    if (cell != null && !cell.isNull()) {
                        return cell;
                    }
                }
            }
            return null;
        }

        /** Converts one guest value to the declared Java type. */
        private Object asJava(Value value, Class<?> type) {
            // Handles (guest class instances / member proxies) unwrap to
            // their host cell first: @ApiClass-typed and union Object
            // parameters receive the live Java value instead of failing
            // scalar conversion.
            Object host = hostCellOf(value);
            if (host != null && (type == Object.class || type.isInstance(host))) {
                return host;
            }
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
         * Supported return types are void, boolean, and object (neutralized:
         * scalars pass through, plain objects become maps, script-type
         * proxies surface their host object).
         */
        private Object adaptFunction(Value function, Class<?> type) {
            ClassLoader loader = type.getClassLoader() != null
                    ? type.getClassLoader() : GraalJsEngine.class.getClassLoader();
            return java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{type},
                    (proxy, method, args) -> {
                        Value[] callArgs = new Value[args == null ? 0 : args.length];
                        for (int i = 0; i < callArgs.length; i++) {
                            callArgs[i] = guestValueOrEvent(args[i]);
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
                        return snapshotValue(asNeutral(result));
                    });
        }

        /** Invokes an async API function and settles the promise on the loop. */
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
