package ink.tuanzi.neomacros.core.api.java;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

/**
 * Proxy over one host object: public fields read and write as values,
 * methods dispatch as callables bound to the instance.
 * @zh 宿主对象的代理：公共字段按值读写，方法按绑定到实例的可调用分发。
 */
@ink.tuanzi.neomacros.doc.api.ApiClass(name = "JavaObject", kind = ink.tuanzi.neomacros.doc.api.ApiClassKind.HANDLE)
public final class JavaObject implements JavaInterop {

    private final Object raw;

    JavaObject(Object raw) {
        this.raw = raw;
    }

    /** The wrapped host object (interop argument adaptation unwraps it).
     * @zh 被包装的宿主对象（interop 参数适配会解包它）。 */
    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @ink.tuanzi.neomacros.doc.api.ApiType("JavaObject")
    public Object raw() {
        return raw;
    }

    /** The raw host value behind this proxy.
     * @zh 代理背后的原始宿主值。 */
    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @ink.tuanzi.neomacros.doc.api.ApiType("JavaObject")
    @Override
    public Object rawValue() {
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

    /** Reads a plain-value member (fields only), or null when absent.
     * @zh 读取纯值成员（仅字段）；缺失为 null。 */
    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @ink.tuanzi.neomacros.doc.api.ApiType("JsonValue | JavaObject")
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

    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @ink.tuanzi.neomacros.doc.api.ApiType("JsonValue | JavaObject")
    @Override
    public Object invoke(String name,
            @ink.tuanzi.neomacros.doc.api.ApiType("Array<JsonValue | JavaObject>") Object[] args) {
        return JavaInteropResolver.dispatch(JavaInteropResolver.methods(raw.getClass(), name, false),
                raw, args);
    }

    /** Writes a public field; throws when no writable field carries the name.
     * @zh 写公共字段；无可写字段时抛错。 */
    @ink.tuanzi.neomacros.doc.api.ApiSince("0.2.0")
    @ink.tuanzi.neomacros.doc.api.ApiMC("*")
    @ink.tuanzi.neomacros.doc.api.ApiLoader("*")
    @Override
    public void write(String name,
            @ink.tuanzi.neomacros.doc.api.ApiType("JsonValue | JavaObject") Object value) {
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
