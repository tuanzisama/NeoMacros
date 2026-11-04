package ink.tuanzi.neomacros.core.api.java;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

/**
 * Proxy over one host object: public fields read and write as values,
 * methods dispatch as callables bound to the instance.
 */
public final class JavaObject implements JavaInterop {

    private final Object raw;

    JavaObject(Object raw) {
        this.raw = raw;
    }

    /** The wrapped host object (interop argument adaptation unwraps it). */
    public Object raw() {
        return raw;
    }

    @Override
    public String className() {
        return raw.getClass().getName();
    }

    @Override
    public boolean hasMember(String name) {
        return !JavaInteropResolver.fields(raw.getClass(), name, false).isEmpty()
                || !JavaInteropResolver.methods(raw.getClass(), name, false).isEmpty();
    }

    @Override
    public Function<Object[], Object> readCallable(String name) {
        List<Method> methods = JavaInteropResolver.methods(raw.getClass(), name, false);
        if (methods.isEmpty()) {
            return null;
        }
        return args -> JavaInteropResolver.dispatch(methods, raw, args);
    }

    @Override
    public Object readValue(String name) {
        List<Field> fields = JavaInteropResolver.fields(raw.getClass(), name, false);
        if (fields.isEmpty()) {
            return null;
        }
        try {
            return fields.get(0).get(raw);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("field not readable: " + name, e);
        }
    }

    @Override
    public Object invoke(String name, Object[] args) {
        return JavaInteropResolver.dispatch(JavaInteropResolver.methods(raw.getClass(), name, false),
                raw, args);
    }

    @Override
    public void write(String name, Object value) {
        List<Field> fields = JavaInteropResolver.fields(raw.getClass(), name, false);
        if (fields.isEmpty()) {
            throw new IllegalStateException("no writable field: " + name);
        }
        Field field = fields.get(0);
        Object adapted = JavaInteropResolver.adapt(field.getType(), value);
        if (adapted == null && value != null) {
            throw new IllegalStateException("cannot store value in field: " + name);
        }
        try {
            field.set(raw, adapted);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("field not writable: " + name, e);
        }
    }

    @Override
    public boolean instanceOf(String className) {
        try {
            return Class.forName(className, false, JavaObject.class.getClassLoader())
                    .isInstance(raw);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
