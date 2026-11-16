package ink.tuanzi.neomacros.core.api.reflect;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.java.JavaInterop;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers behind the {@code neo.reflect} module and its member
 * handles: class-name resolution through the mapping layer, exact-signature
 * matching, argument coercion, and guest-value unwrapping.
 *
 * @zh {@code neo.reflect} 模块与成员句柄共用的辅助：映射层类名解析、精确签名匹配、参数转换、guest 值解包。
 */
final class ReflectSupport {

    private ReflectSupport() {
    }

    /** The primitive class behind a primitive name ({@code int}, ...), or null. */
    static Class<?> primitiveClass(String name) {
        if (name == null) {
            return null;
        }
        return switch (name) {
            case "int" -> int.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "boolean" -> boolean.class;
            case "void" -> void.class;
            default -> null;
        };
    }

    /** Resolves one class name through primitives and the mapping layer. */
    static Class<?> byName(String name, ReflectApi.ClassResolver resolver)
            throws ClassNotFoundException {
        Class<?> primitive = primitiveClass(name);
        if (primitive != null) {
            return primitive;
        }
        return Class.forName(resolver.resolve(name), true, ReflectApi.class.getClassLoader());
    }

    /** Resolves one class argument: a class, a reflect handle, or a class name. */
    static Class<?> classOf(Object value, ReflectApi.ClassResolver resolver)
            throws ClassNotFoundException {
        if (value instanceof Class<?> type) {
            return type;
        }
        if (value instanceof ReflectClass handle) {
            return handle.target();
        }
        if (value instanceof String name) {
            return byName(name, resolver);
        }
        throw new IllegalArgumentException(
                "class must be a ReflectClass or a class-name string: " + value);
    }

    /** Resolves one annotation class argument; unknown or non-annotation names reject. */
    static Class<? extends Annotation> annotationType(Object value,
            ReflectApi.ClassResolver resolver) {
        try {
            Class<?> resolved = classOf(value, resolver);
            if (!Annotation.class.isAssignableFrom(resolved)) {
                throw new IllegalArgumentException("not an annotation class: " + value);
            }
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> annotation = (Class<? extends Annotation>) resolved;
            return annotation;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("unknown annotation class: " + value, e);
        }
    }

    /** Parses the parameter-type array into classes (null stays empty). */
    static List<Class<?>> signatureOf(Object paramTypes, ReflectApi.ClassResolver resolver) {
        List<Class<?>> out = new ArrayList<>();
        if (paramTypes instanceof List<?> list) {
            for (Object entry : list) {
                if (entry == null) {
                    out.add(Object.class);
                } else {
                    try {
                        out.add(classOf(entry, resolver));
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException("unknown parameter type: " + entry, e);
                    }
                }
            }
        }
        return out;
    }

    /** Exact-type signature match with primitive widening compatibility. */
    static boolean matches(Class<?>[] declared, List<Class<?>> wanted) {
        for (int i = 0; i < declared.length; i++) {
            Class<?> w = wanted.get(i);
            if (declared[i] == w || declared[i].isAssignableFrom(w)) {
                continue;
            }
            if (w.isPrimitive() && unwrapPrimitive(declared[i]) == w) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** The primitive type behind a wrapper class, or null. */
    static Class<?> unwrapPrimitive(Class<?> type) {
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

    /**
     * The raw host value behind one guest value: reflect handles unwrap to
     * the wrapped member or class, interop proxies to their raw value, and
     * other script handles through their {@code raw} escape hatch.
     *
     * @zh guest 值背后的原始宿主值：反射句柄解包为被包成员或类，interop 代理解包为 raw 值，其余脚本句柄经 raw 穿透口解包。
     */
    static Object unwrap(Object value) {
        if (value instanceof ReflectClass handle) {
            return handle.target();
        }
        if (value instanceof ReflectField handle) {
            return handle.member();
        }
        if (value instanceof ReflectMethod handle) {
            return handle.member();
        }
        if (value instanceof ReflectConstructor handle) {
            return handle.member();
        }
        if (value instanceof JavaInterop interop) {
            return interop.rawValue();
        }
        if (value instanceof ScriptHandle handle) {
            return rawMember(handle);
        }
        return value;
    }

    /** The {@code getRaw()} value of a handle that exposes one, else the handle. */
    private static Object rawMember(Object handle) {
        try {
            return handle.getClass().getMethod("getRaw").invoke(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return handle;
        }
    }

    /** Coerces one argument value to the declared parameter type. */
    static Object coerce(Object value, Class<?> type) {
        value = unwrap(value);
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
    static Object[] positional(Object args, int expected) {
        List<?> list = args instanceof List<?> l ? l : List.of();
        Object[] out = new Object[expected < 0 ? list.size() : Math.max(expected, list.size())];
        for (int i = 0; i < out.length; i++) {
            out[i] = i < list.size() ? list.get(i) : null;
        }
        return out;
    }

    /** Whether the method is static. */
    static boolean isStatic(Method method) {
        return java.lang.reflect.Modifier.isStatic(method.getModifiers());
    }

    /**
     * Wraps one declared field as a handle. Accessibility opens best-effort:
     * a denied member surfaces later at read/write with its own error.
     */
    static ReflectField fieldHandle(Field field, ReflectApi.ClassResolver resolver) {
        open(field);
        return new ReflectField(field, resolver);
    }

    /** Wraps one declared method as a handle (accessibility best-effort). */
    static ReflectMethod methodHandle(Method method, ReflectApi.ClassResolver resolver) {
        open(method);
        return new ReflectMethod(method, resolver);
    }

    /** Wraps one declared constructor as a handle (accessibility best-effort). */
    static ReflectConstructor constructorHandle(Constructor<?> constructor,
            ReflectApi.ClassResolver resolver) {
        open(constructor);
        return new ReflectConstructor(constructor, resolver);
    }

    /** Opens one member for reflective access, tolerating module boundaries. */
    private static void open(AccessibleObject object) {
        try {
            object.setAccessible(true);
        } catch (RuntimeException ignored) {
            // a denied member fails later at use with its own message
        }
    }

    /** Wraps one annotation array into a script array of interop proxies. */
    static List<Object> wrapAll(Annotation[] annotations) {
        List<Object> out = new ArrayList<>(annotations.length);
        for (Annotation annotation : annotations) {
            out.add(JavaInterop.wrap(annotation));
        }
        return out;
    }

    /** Wraps one annotation (or null) into its script-visible proxy. */
    static Object wrapAnnotation(Annotation annotation) {
        return JavaInterop.wrap(annotation);
    }

    /**
     * Marshals one reflection result for the guest. Lists and maps keep
     * object identity as {@code JavaObject} proxies — the engine's default
     * rendering would flatten them into detached guest copies — while
     * handles, interop values, scalars, byte arrays and event envelopes
     * pass through for the engine's own rules.
     *
     * @zh 反射结果的 guest 编组：List/Map 以 {@code JavaObject} 代理保持对象身份（默认渲染会拍平成脱离身份的 guest 副本）；句柄、interop 值、标量、字节数组与事件信封原样交由引擎规则处理。
     */
    static Object marshal(Object value) {
        if (value instanceof java.util.List<?> || value instanceof java.util.Map<?, ?>) {
            return JavaInterop.wrap(value);
        }
        return value;
    }
}
