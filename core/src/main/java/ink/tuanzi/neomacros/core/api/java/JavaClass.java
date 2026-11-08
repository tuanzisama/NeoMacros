package ink.tuanzi.neomacros.core.api.java;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

/**
 * Proxy over one host class: static fields read as values, static methods
 * dispatch as callables, and the {@code new} member constructs instances.
 * @zh 宿主类的代理：静态字段按值读取，静态方法按可调用分发，{@code new} 成员构造实例。
 */
@ink.tuanzi.neomacros.doc.api.ApiClass(name = "JavaClass", kind = ink.tuanzi.neomacros.doc.api.ApiClassKind.HANDLE)
public final class JavaClass implements JavaInterop {

    private final Class<?> target;

    JavaClass(Class<?> target) {
        this.target = target;
    }

    @Override
    public String className() {
        return target.getName();
    }

    @Override
    public boolean hasMember(String name) {
        return name.equals("new")
                || !JavaInteropResolver.fields(target, name, true).isEmpty()
                || !JavaInteropResolver.methods(target, name, true).isEmpty();
    }

    @Override
    public Function<Object[], Object> readCallable(String name) {
        if (name.equals("new")) {
            return args -> JavaInterop.wrap(constructInstance(args));
        }
        List<Method> methods = JavaInteropResolver.methods(target, name, true);
        if (methods.isEmpty()) {
            return null;
        }
        return args -> JavaInteropResolver.dispatch(methods, null, args);
    }

    /** Reads a plain-value member (fields only), or null when absent.
     * @zh 读取纯值成员（仅字段）；缺失为 null。 */
    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @ink.tuanzi.neomacros.doc.api.ApiType("JsonValue | JavaObject")
    @Override
    public Object readValue(String name) {
        List<Field> fields = JavaInteropResolver.fields(target, name, true);
        if (fields.isEmpty()) {
            return null;
        }
        try {
            return fields.get(0).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("field not readable: " + name, e);
        }
    }

    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @ink.tuanzi.neomacros.doc.api.ApiType("JsonValue | JavaObject")
    @Override
    public Object invoke(String name,
            @ink.tuanzi.neomacros.doc.api.ApiType("Array<JsonValue | JavaObject>") Object[] args) {
        return JavaInteropResolver.dispatch(JavaInteropResolver.methods(target, name, true),
                null, args);
    }

    /** Writes a public field; throws when no writable field carries the name.
     * @zh 写公共字段；无可写字段时抛错。 */
    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @Override
    public void write(String name,
            @ink.tuanzi.neomacros.doc.api.ApiType("JsonValue | JavaObject") Object value) {
        List<Field> fields = JavaInteropResolver.fields(target, name, true);
        if (fields.isEmpty()) {
            throw new IllegalStateException("no writable static field: " + name);
        }
        Field field = fields.get(0);
        Object adapted = JavaInteropResolver.adapt(field.getType(), value);
        if (adapted == null && value != null) {
            throw new IllegalStateException("cannot store value in field: " + name);
        }
        try {
            field.set(null, adapted);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("field not writable: " + name, e);
        }
    }

    @Override
    public boolean instanceOf(String className) {
        return target.getName().equals(className);
    }

    /** Constructs one instance through any matching constructor. */
    Object constructInstance(Object[] args) {
        for (var constructor : target.getConstructors()) {
            Object[] adapted = adaptConstructorArgs(constructor.getParameterTypes(), args);
            if (adapted != null) {
                try {
                    return constructor.newInstance(adapted);
                } catch (ReflectiveOperationException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new IllegalStateException(String.valueOf(cause), cause);
                }
            }
        }
        throw new IllegalStateException("no matching constructor on "
                + target.getName() + " for " + (args == null ? 0 : args.length) + " argument(s)");
    }

    /** Reuses the member argument adaptation for constructor matching. */
    private static Object[] adaptConstructorArgs(Class<?>[] parameters, Object[] args) {
        if (parameters.length != (args == null ? 0 : args.length)) {
            return null;
        }
        Object[] out = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Object arg = args == null ? null : args[i];
            Object adapted = JavaInteropResolver.adapt(parameters[i], arg);
            if (adapted == null && arg != null) {
                return null;
            }
            out[i] = adapted;
        }
        return out;
    }

    /** The wrapped class (interop results reuse it for auto-wrapping checks). */
    Class<?> target() {
        return target;
    }

    /** The raw host value behind this proxy.
     * @zh 代理背后的原始宿主值。 */
    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @ink.tuanzi.neomacros.doc.api.ApiType("JavaObject")
    @Override
    public Object rawValue() {
        return target;
    }
}
