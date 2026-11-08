package ink.tuanzi.neomacros.core.api.reflect;

import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code neo.reflect} module: meta-level tools for the escape layer.
 * Where {@code neo.java} proxies objects, this module resolves exact
 * signatures (including private members), invokes reflectively, and
 * constructs instances. The whole module rides the {@code java} permission
 * gate.
 *
 * @zh {@code neo.reflect}：穿透层的元级工具箱。{@code neo.java} 代理对象，本模块解析精确签名（含私有成员）、反射调用与构造实例。整模块走 {@code java} 权限闸。
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
     * class-name strings or {@code JavaClass} values. Returns the
     * {@code java.lang.reflect.Method} proxy, or undefined on a miss.
     *
     * @zh 按精确参数签名解析已声明方法（含私有重载）。参数类型为数组（类名字符串或 {@code JavaClass}）。返回 {@code java.lang.reflect.Method} 代理；未命中为 undefined。
     * @zh-param target 实例或类（{@code JavaClass}）
     * @zh-param name 方法名
     * @zh-param paramTypes 参数类型数组（可省略）
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiType("JavaObject")
    public Object method(@ApiType("JavaObject | JavaClass") Object target, String name,
            @ApiType("Array<string | JavaClass>") Object paramTypes) {
        Class<?> owner = ownerOf(target);
        List<Class<?>> signature = signatureOf(paramTypes);
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method candidate : type.getDeclaredMethods()) {
                if (!candidate.getName().equals(name)
                        || candidate.getParameterCount() != signature.size()) {
                    continue;
                }
                if (matches(candidate.getParameterTypes(), signature)) {
                    candidate.setAccessible(true);
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Resolves one declared field, including private fields. Returns the
     * {@code java.lang.reflect.Field} proxy, or undefined on a miss.
     *
     * @zh 解析已声明字段（含私有）。返回 {@code java.lang.reflect.Field} 代理；未命中为 undefined。
     * @zh-param target 实例或类（{@code JavaClass}）
     * @zh-param name 字段名
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiType("JavaObject")
    public Object field(@ApiType("JavaObject | JavaClass") Object target, String name) {
        Class<?> owner = ownerOf(target);
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                Field candidate = type.getDeclaredField(name);
                candidate.setAccessible(true);
                return candidate;
            } catch (NoSuchFieldException ignored) {
                // walk the superclass chain
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
     * @zh-param method {@code method()} 返回的 Method
     * @zh-param target 接收者实例；静态调用省略
     * @zh-param args 参数数组（可省略）
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiType("JsonValue | JavaObject")
    public Object invoke(@ApiType("JavaObject") Object method,
            @ApiOptional @ApiType("JavaObject") Object target,
            @ApiOptional @ApiType("Array<JsonValue | JavaObject>") Object args) throws Exception {
        if (!(method instanceof Method handle)) {
            throw new IllegalArgumentException("method must come from reflect.method()");
        }
        Object[] argv = positional(args, handle.getParameterCount());
        Object[] converted = new Object[argv.length];
        for (int i = 0; i < argv.length; i++) {
            converted[i] = coerce(argv[i], handle.getParameterTypes()[i]);
        }
        return handle.invoke(isStatic(handle) ? null : target, converted);
    }

    /**
     * Reflectively constructs one instance of the class with the matching
     * constructor.
     *
     * @zh 反射构造类实例（按参数匹配构造器）。
     * @zh-param cls {@code JavaClass} 或类名字符串
     * @zh-param args 构造参数数组（可省略）
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiType("JavaObject")
    public Object newInstance(@ApiType("JavaClass | string") Object cls,
            @ApiOptional @ApiType("Array<JsonValue | JavaObject>") Object args) throws Exception {
        Class<?> type = classOf(cls);
        Object[] argv = positional(args, -1);
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            if (candidate.getParameterCount() != argv.length) {
                continue;
            }
            Object[] converted = new Object[argv.length];
            boolean usable = true;
            for (int i = 0; i < argv.length; i++) {
                try {
                    converted[i] = coerce(argv[i], candidate.getParameterTypes()[i]);
                } catch (IllegalArgumentException e) {
                    usable = false;
                    break;
                }
            }
            if (usable) {
                candidate.setAccessible(true);
                return candidate.newInstance(converted);
            }
        }
        throw new IllegalArgumentException(
                "no matching constructor on " + type.getName() + " for " + argv.length + " args");
    }

    /**
     * The fully-qualified class name of any Java value (works on non-proxied
     * values too), or undefined for undefined/null.
     *
     * @zh 任意 Java 值的全限定类名（非代理值同样可用）；undefined/null 返回 undefined。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String className(@ApiType("JsonValue | JavaObject") Object value) {
        return value == null ? null : value.getClass().getName();
    }

    /** The owner class of a target (the class itself, or the instance's). */
    private static Class<?> ownerOf(Object target) {
        if (target instanceof Class<?> type) {
            return type;
        }
        if (target == null) {
            throw new IllegalArgumentException("reflect target is required");
        }
        return target.getClass();
    }

    /** Resolves one class argument (JavaClass value, Class, or name string). */
    private Class<?> classOf(Object value) throws ClassNotFoundException {
        if (value instanceof Class<?> type) {
            return type;
        }
        if (value instanceof String name) {
            return Class.forName(resolver.resolve(name), true,
                    ReflectApi.class.getClassLoader());
        }
        throw new IllegalArgumentException(
                "class must be a JavaClass or a class-name string: " + value);
    }

    /** Parses the parameter-type array into classes. */
    private List<Class<?>> signatureOf(Object paramTypes) {
        List<Class<?>> out = new ArrayList<>();
        if (paramTypes instanceof List<?> list) {
            for (Object entry : list) {
                if (entry == null) {
                    out.add(Object.class);
                } else {
                    try {
                        out.add(classOf(entry));
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException("unknown parameter type: " + entry, e);
                    }
                }
            }
        }
        return out;
    }

    /** Exact-type signature match with primitive widening compatibility. */
    private static boolean matches(Class<?>[] declared, List<Class<?>> wanted) {
        for (int i = 0; i < declared.length; i++) {
            Class<?> w = wanted.get(i);
            if (declared[i] == w || declared[i].isAssignableFrom(w)) {
                continue;
            }
            if (w.isPrimitive() && unwrap(declared[i]) == w) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** The primitive type behind a wrapper class, or null. */
    private static Class<?> unwrap(Class<?> type) {
        if (type == Integer.class) {
            return int.class;
        }
        if (type == Long.class) {
            return long.class;
        }
        if (type == Boolean.class) {
            return boolean.class;
        }
        if (type == Double.class) {
            return double.class;
        }
        if (type == Float.class) {
            return float.class;
        }
        if (type == Short.class) {
            return short.class;
        }
        if (type == Byte.class) {
            return byte.class;
        }
        if (type == Character.class) {
            return char.class;
        }
        return null;
    }

    /** Coerces one argument value to the declared parameter type. */
    private static Object coerce(Object value, Class<?> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number) {
            if (type == int.class || type == Integer.class) {
                return number.intValue();
            }
            if (type == long.class || type == Long.class) {
                return number.longValue();
            }
            if (type == double.class || type == Double.class) {
                return number.doubleValue();
            }
            if (type == float.class || type == Float.class) {
                return number.floatValue();
            }
            if (type == short.class || type == Short.class) {
                return number.shortValue();
            }
            if (type == byte.class || type == Byte.class) {
                return number.byteValue();
            }
        }
        if (value instanceof Boolean b && (type == boolean.class || type == Boolean.class)) {
            return b;
        }
        if (value instanceof String s && type == String.class) {
            return s;
        }
        if (value instanceof Character c && (type == char.class || type == Character.class)) {
            return c;
        }
        throw new IllegalArgumentException(
                "cannot convert argument to " + type.getName() + ": " + value);
    }

    /** Reads the positional array (undefined stays empty; -1 keeps the array). */
    private static Object[] positional(Object args, int expected) {
        List<?> list = args instanceof List<?> l ? l : List.of();
        Object[] out = new Object[expected < 0 ? list.size() : Math.max(expected, list.size())];
        for (int i = 0; i < out.length; i++) {
            out[i] = i < list.size() ? list.get(i) : null;
        }
        return out;
    }

    /** Whether the method is static. */
    private static boolean isStatic(Method method) {
        return java.lang.reflect.Modifier.isStatic(method.getModifiers());
    }
}
