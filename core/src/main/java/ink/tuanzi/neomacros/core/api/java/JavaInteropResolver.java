package ink.tuanzi.neomacros.core.api.java;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection helpers shared by the interop objects: member lookup and
 * best-effort argument adaptation. Matching is pragmatic — same name and
 * arity first, then per-argument numeric widening — so scripts call common
 * overloads without declaring types.
 */
final class JavaInteropResolver {

    private JavaInteropResolver() {
    }

    /** All public methods carrying the name (static only when requested). */
    static List<Method> methods(Class<?> owner, String name, boolean statik) {
        List<Method> found = new ArrayList<>();
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name)
                    || Modifier.isStatic(method.getModifiers()) != statik) {
                continue;
            }
            method.setAccessible(true);
            found.add(method);
        }
        return found;
    }

    /** All public fields carrying the name (static only when requested). */
    static List<Field> fields(Class<?> owner, String name, boolean statik) {
        List<Field> found = new ArrayList<>();
        for (Class<?> type = owner; type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals(name)
                        && Modifier.isPublic(field.getModifiers())
                        && Modifier.isStatic(field.getModifiers()) == statik) {
                    field.setAccessible(true);
                    found.add(field);
                }
            }
        }
        return found;
    }

    /** Dispatches the first method whose parameters accept the arguments. */
    static Object dispatch(List<Method> candidates, Object receiver, Object[] args) {
        for (Method method : candidates) {
            Object[] adapted = adaptAll(method.getParameterTypes(), args);
            if (adapted != null) {
                try {
                    return method.invoke(receiver, adapted);
                } catch (ReflectiveOperationException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new IllegalStateException(String.valueOf(cause), cause);
                }
            }
        }
        throw new IllegalStateException("no matching overload for "
                + (receiver != null ? receiver.getClass().getName() : candidates.isEmpty()
                ? "?" : candidates.get(0).getDeclaringClass().getName())
                + " with " + (args == null ? 0 : args.length) + " argument(s)");
    }

    /** Adapts the arguments to the parameter types, or null when impossible. */
    private static Object[] adaptAll(Class<?>[] parameters, Object[] args) {
        if (parameters.length != (args == null ? 0 : args.length)) {
            return null;
        }
        Object[] out = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Object adapted = adapt(parameters[i], args == null ? null : args[i]);
            if (adapted == null && args != null && args[i] != null) {
                return null;
            }
            out[i] = adapted;
        }
        return out;
    }

    /** Adapts one argument to one parameter type, or null when impossible. */
    static Object adapt(Class<?> parameter, Object arg) {
        if (arg == null) {
            return parameter.isPrimitive() ? null : null;
        }
        if (arg instanceof JavaInterop interop) {
            arg = interop instanceof JavaObject object ? object.raw() : arg;
        }
        if (parameter.isInstance(arg)) {
            return arg;
        }
        if (arg instanceof Number number) {
            if (parameter == int.class || parameter == Integer.class) {
                return number.intValue();
            }
            if (parameter == long.class || parameter == Long.class) {
                return number.longValue();
            }
            if (parameter == double.class || parameter == Double.class) {
                return number.doubleValue();
            }
            if (parameter == float.class || parameter == Float.class) {
                return number.floatValue();
            }
            if (parameter == short.class || parameter == Short.class) {
                return number.shortValue();
            }
            if (parameter == byte.class || parameter == Byte.class) {
                return number.byteValue();
            }
            return null;
        }
        if (arg instanceof String string
                && (parameter == String.class || parameter == CharSequence.class)) {
            return string;
        }
        if (arg instanceof Boolean flag
                && (parameter == boolean.class || parameter == Boolean.class)) {
            return flag;
        }
        if (parameter == CharSequence.class && arg instanceof Character c) {
            return String.valueOf(c);
        }
        return null;
    }
}
