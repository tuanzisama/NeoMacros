package ink.tuanzi.neomacros.engine;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
     * binding wrapper, guest Error construction for rejections, and the
     * deeply-frozen {@code neo} global installation. The binding tree itself
     * is built from Java through the polyglot Value API (host maps are not
     * enumerable from guest code when host access is disabled).
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
            globalThis.__neoInstall = function (tree) {
                const deepFreeze = function (value) {
                    Object.freeze(value);
                    for (const key of Object.keys(value)) {
                        const child = value[key];
                        if (child !== null && typeof child === 'object') {
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
            """;

    private final ModuleResolver resolver;
    private final List<GraalJsSession> sessions = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    /** Creates the engine; the resolver feeds virtual modules and script files. */
    public GraalJsEngine(ModuleResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public EngineSession openSession(ScriptContext context) throws ScriptError {
        if (closed) {
            throw new ScriptError("engine is closed");
        }
        GraalJsSession session = new GraalJsSession(context);
        session.onLoop(() -> {
            try {
                session.context.eval(Source.newBuilder("js", GLUE, "neomacros-engine-glue")
                        .mimeType("application/javascript")
                        .buildLiteral());
            } catch (PolyglotException e) {
                throw translate("neomacros-engine-glue", e);
            }
            session.glue = session.context.getBindings("js");
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
        private Value glue;

        private GraalJsSession(ScriptContext owner) {
            this.owner = owner;
            this.context = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.NONE)
                    .allowIO(IOAccess.newBuilder()
                            .fileSystem(new ScriptSandboxFileSystem(resolver))
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

        /** Reads a global left behind by executed modules (observation surface). */
        Value global(String name) throws ScriptError {
            return onLoop(() -> context.getBindings("js").getMember(name));
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
        public void close() {
            context.close();
        }

        /** Runs one piece of guest work on the context loop, blocking the caller. */
        private <T> T onLoop(Supplier<T> work) throws ScriptError {
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

        private Value glueMember(String name) {
            Value member = glue.getMember(name);
            if (member == null) {
                throw new IllegalStateException("engine glue member missing: " + name);
            }
            return member;
        }

        private String guestMessage(Value guestValue) {
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
