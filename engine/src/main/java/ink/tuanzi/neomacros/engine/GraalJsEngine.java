package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.bindgen.ModuleBinding;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionError;
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
        private Value lastNamespace;
        private Value cachedUndefined;

        private GraalJsSession(ScriptContext owner, Path moduleRoot) {
            this.owner = owner;
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
            if (plain instanceof ink.tuanzi.neomacros.core.api.ScriptHandle handle) {
                return handleProxy(handle);
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
            return context.asValue(plain);
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
         * Renders a live facade handle as a guest-readable member proxy: bean
         * getters read as properties on every access (real-time per the
         * handle contract), zero-argument public methods cross as functions,
         * writes throw. Raw host objects are unreadable under the closed
         * host-access policy, so the proxy is the only readable surface.
         */
        private Value handleProxy(Object handle) {
            var members = handleMembers(handle.getClass());
            return context.asValue(new org.graalvm.polyglot.proxy.ProxyObject() {
                @Override
                public Object getMember(String name) {
                    java.lang.reflect.Executable member = members.get(name);
                    if (member == null) {
                        return undefinedValue();
                    }
                    try {
                        Object result;
                        if (member instanceof java.lang.reflect.Method method) {
                            result = method.invoke(handle);
                        } else {
                            result = null;
                        }
                        return asGuestValue(result);
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

                @Override
                public boolean hasMember(String name) {
                    return members.containsKey(name);
                }

                @Override
                public Object getMemberKeys() {
                    String[] keys = members.keySet().toArray(new String[0]);
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

        /** Bean getters (properties) and zero-arg public methods of one handle class, by script member name. */
        private static final Map<Class<?>, Map<String, java.lang.reflect.Executable>> HANDLE_MEMBERS =
                new ConcurrentHashMap<>();

        private static Map<String, java.lang.reflect.Executable> handleMembers(Class<?> type) {
            return HANDLE_MEMBERS.computeIfAbsent(type, cls -> {
                Map<String, java.lang.reflect.Executable> members = new LinkedHashMap<>();
                for (java.lang.reflect.Method method : cls.getMethods()) {
                    if (method.getDeclaringClass() == Object.class) {
                        continue;
                    }
                    String name = method.getName();
                    int params = method.getParameterCount();
                    if (params == 0 && (name.startsWith("get") || name.startsWith("is"))
                            && !name.equals("getClass")) {
                        String property = name.startsWith("is")
                                ? decapitalize(name.substring(2))
                                : decapitalize(name.substring(3));
                        members.putIfAbsent(property, method);
                    } else if (params == 0 && !name.equals("getClass")) {
                        members.putIfAbsent(name, method);
                    }
                }
                return Map.copyOf(members);
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
