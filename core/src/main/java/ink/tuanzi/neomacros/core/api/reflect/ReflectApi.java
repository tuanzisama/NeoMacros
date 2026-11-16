package ink.tuanzi.neomacros.core.api.reflect;

import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * The {@code neo.reflect} module: meta-level tools for the escape layer.
 * Where {@code neo.java} proxies objects, this module resolves exact
 * signatures (including private members) into {@code ReflectClass} /
 * {@code ReflectField} / {@code ReflectMethod} / {@code ReflectConstructor}
 * handles — the {@code java.lang.reflect} surface of fields, methods,
 * constructors, modifiers and annotations — and invokes reflectively. The
 * whole module rides the {@code java} permission gate.
 *
 * @zh {@code neo.reflect}：穿透层的元级工具箱。{@code neo.java} 代理对象，本模块把精确签名（含私有成员）解析为 {@code ReflectClass} / {@code ReflectField} / {@code ReflectMethod} / {@code ReflectConstructor} 句柄——字段、方法、构造器、修饰符与注解的 {@code java.lang.reflect} 面——并以反射方式调用。整模块走 {@code java} 权限闸。
 */
@ApiModule(name = "reflect", category = "系统")
@ApiPermission("java")
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class ReflectApi {

    /** Translates a script-supplied class name into the runtime binary name. */
    @FunctionalInterface
    public interface ClassResolver {
        String resolve(String name) throws ClassNotFoundException;
    }

    private final ClassResolver resolver;

    /** Creates the API with a mapping-layer name resolver. */
    public ReflectApi(ClassResolver resolver) {
        this.resolver = resolver;
    }

    /** Creates the API (harness use; exact class names required). */
    public ReflectApi() {
        this(name -> name);
    }

    /**
     * Resolves one declared method with an exact parameter signature,
     * including private overloads. Parameter types arrive as an array of
     * class-name strings or {@code ReflectClass} values. Returns the
     * {@code ReflectMethod} handle, or undefined on a miss.
     *
     * @zh 按精确参数签名解析已声明方法（含私有重载）。参数类型为数组（类名字符串或 {@code ReflectClass}）。返回 {@code ReflectMethod} 句柄；未命中为 undefined。
     * @zh-param target 实例或类
     * @zh-param name 方法名
     * @zh-param paramTypes 参数类型数组（可省略）
     * @zh-return 方法句柄或 undefined
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ReflectMethod method(
            @ApiType("ReflectClass | JavaObject | JavaClass") Object target, String name,
            @ApiOptional @ApiType("Array<string | JavaClass | ReflectClass>") Object paramTypes) {
        Class<?> owner = ownerOf(target);
        List<Class<?>> signature = ReflectSupport.signatureOf(paramTypes, resolver);
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method candidate : type.getDeclaredMethods()) {
                if (!candidate.getName().equals(name)
                        || candidate.getParameterCount() != signature.size()) {
                    continue;
                }
                if (ReflectSupport.matches(candidate.getParameterTypes(), signature)) {
                    return ReflectSupport.methodHandle(candidate, resolver);
                }
            }
        }
        return null;
    }

    /**
     * Resolves one declared field, including private fields. Returns the
     * {@code ReflectField} handle, or undefined on a miss.
     *
     * @zh 解析已声明字段（含私有）。返回 {@code ReflectField} 句柄；未命中为 undefined。
     * @zh-param target 实例或类
     * @zh-param name 字段名
     * @zh-return 字段句柄或 undefined
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ReflectField field(
            @ApiType("ReflectClass | JavaObject | JavaClass") Object target, String name) {
        Class<?> owner = ownerOf(target);
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                return ReflectSupport.fieldHandle(type.getDeclaredField(name), resolver);
            } catch (NoSuchFieldException ignored) {
                // walk the superclass chain
            }
        }
        return null;
    }

    /**
     * Resolves one declared constructor with an exact parameter signature,
     * including private constructors. Parameter types arrive as an array of
     * class-name strings or {@code ReflectClass} values. Returns the
     * {@code ReflectConstructor} handle, or undefined on a miss.
     *
     * @zh 按精确参数签名解析已声明构造器（含私有）。参数类型为数组（类名字符串或 {@code ReflectClass}）。返回 {@code ReflectConstructor} 句柄；未命中为 undefined。
     * @zh-param target 实例或类
     * @zh-param paramTypes 参数类型数组（可省略）
     * @zh-return 构造器句柄或 undefined
     */
    @ApiSince("0.7.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ReflectConstructor constructor(
            @ApiType("ReflectClass | JavaObject | JavaClass") Object target,
            @ApiOptional @ApiType("Array<string | JavaClass | ReflectClass>") Object paramTypes) {
        Class<?> owner = ownerOf(target);
        List<Class<?>> signature = ReflectSupport.signatureOf(paramTypes, resolver);
        for (Constructor<?> candidate : owner.getDeclaredConstructors()) {
            if (candidate.getParameterCount() != signature.size()) {
                continue;
            }
            if (ReflectSupport.matches(candidate.getParameterTypes(), signature)) {
                return ReflectSupport.constructorHandle(candidate, resolver);
            }
        }
        return null;
    }

    /**
     * Invokes one resolved reflect method. Omitting the target invokes a
     * static method; numeric arguments convert to the declared parameter
     * types.
     *
     * @zh 反射调用已解析的方法。target 省略（undefined）即静态调用；数字参数按声明类型自动转换。
     * @zh-param method {@code method()} 返回的 ReflectMethod（或原始 Method 代理）
     * @zh-param target 接收者实例；静态调用省略
     * @zh-param args 参数数组（可省略）
     * @zh-return 调用结果
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiType("JsonValue | JavaObject")
    public Object invoke(@ApiType("JavaObject") Object method,
            @ApiOptional @ApiType("JavaObject") Object target,
            @ApiOptional @ApiType("Array<JsonValue | JavaObject>") Object args) throws Exception {
        Method handle;
        if (method instanceof ReflectMethod resolved) {
            handle = resolved.member();
        } else if (method instanceof Method raw) {
            handle = raw;
        } else {
            throw new IllegalArgumentException("method must come from reflect.method()");
        }
        Object[] argv = ReflectSupport.positional(args, handle.getParameterCount());
        Object[] converted = new Object[argv.length];
        for (int i = 0; i < argv.length; i++) {
            converted[i] = ReflectSupport.coerce(argv[i], handle.getParameterTypes()[i]);
        }
        return ReflectSupport.marshal(handle.invoke(ReflectSupport.isStatic(handle) ? null
                : ReflectSupport.unwrap(target), converted));
    }

    /**
     * Reflectively constructs one instance of the class with the matching
     * constructor — or, given a {@code ReflectConstructor} handle from
     * {@code constructor()}, that exact constructor.
     *
     * @zh 反射构造类实例（按参数匹配构造器）；传入 {@code constructor()} 得到的 {@code ReflectConstructor} 句柄时使用该精确构造器。
     * @zh-param cls {@code ReflectClass} / {@code JavaClass} / 类名字符串 / {@code ReflectConstructor} 句柄
     * @zh-param args 构造参数数组（可省略）
     * @zh-return 新实例
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiType("JavaObject")
    public Object newInstance(@ApiType("ReflectClass | JavaClass | string | ReflectConstructor")
            Object cls,
            @ApiOptional @ApiType("Array<JsonValue | JavaObject>") Object args) throws Exception {
        if (cls instanceof ReflectConstructor resolved) {
            return ReflectSupport.marshal(resolved.newInstance(args));
        }
        Class<?> type = ReflectSupport.classOf(cls, resolver);
        Object[] argv = ReflectSupport.positional(args, -1);
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            if (candidate.getParameterCount() != argv.length) {
                continue;
            }
            Object[] converted = new Object[argv.length];
            boolean usable = true;
            for (int i = 0; i < argv.length; i++) {
                try {
                    converted[i] = ReflectSupport.coerce(argv[i], candidate.getParameterTypes()[i]);
                } catch (IllegalArgumentException e) {
                    usable = false;
                    break;
                }
            }
            if (usable) {
                candidate.setAccessible(true);
                return ReflectSupport.marshal(candidate.newInstance(converted));
            }
        }
        throw new IllegalArgumentException(
                "no matching constructor on " + type.getName() + " for " + argv.length + " args");
    }

    /**
     * Loads one class by name through the mapping layer as a
     * {@code ReflectClass} handle — the {@code Class.forName} entry point
     * of the reflection surface.
     *
     * @zh 按类名经映射层加载类，返回 {@code ReflectClass} 句柄——反射面的 {@code Class.forName} 入口。
     * @zh-param name 类名（基本类型名如 {@code int} 同样接受）
     * @zh-return 类句柄
     */
    @ApiSince("0.7.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ReflectClass forName(String name) throws ClassNotFoundException {
        return new ReflectClass(ReflectSupport.byName(name, resolver), resolver);
    }

    /**
     * The class handle of any Java value ({@code obj.getClass()}
     * equivalent) — a class value answers itself. Undefined for
     * undefined/null.
     *
     * @zh 任意 Java 值的类句柄（{@code obj.getClass()} 等价）；传入类值返回自身。undefined/null 返回 undefined。
     * @zh-param value 任意值
     * @zh-return 类句柄或 undefined
     */
    @ApiSince("0.7.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ReflectClass classOf(@ApiType("JsonValue | JavaObject") Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ReflectClass handle) {
            return handle;
        }
        Object raw = ReflectSupport.unwrap(value);
        if (raw instanceof Class<?> type) {
            return new ReflectClass(type, resolver);
        }
        return new ReflectClass(raw.getClass(), resolver);
    }

    /**
     * The fully-qualified class name of any Java value (works on non-proxied
     * values too); reflect handles unwrap to what they wrap. Undefined for
     * undefined/null.
     *
     * @zh 任意 Java 值的全限定类名（非代理值同样可用）；反射句柄解包为其包装的值。undefined/null 返回 undefined。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String className(@ApiType("JsonValue | JavaObject") Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ReflectClass handle) {
            return handle.target().getName();
        }
        if (value instanceof ReflectField handle) {
            return handle.member().getClass().getName();
        }
        if (value instanceof ReflectMethod handle) {
            return handle.member().getClass().getName();
        }
        if (value instanceof ReflectConstructor handle) {
            return handle.member().getClass().getName();
        }
        return value.getClass().getName();
    }

    /** The owner class of a target (the class itself, or the instance's). */
    private static Class<?> ownerOf(Object target) {
        Object raw = ReflectSupport.unwrap(target);
        if (raw instanceof Class<?> type) {
            return type;
        }
        if (raw == null) {
            throw new IllegalArgumentException("reflect target is required");
        }
        return raw.getClass();
    }
}
