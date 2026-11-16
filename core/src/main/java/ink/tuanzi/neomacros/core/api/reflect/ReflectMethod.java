package ink.tuanzi.neomacros.core.api.reflect;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Handle over one declared method: invoke through
 * {@code java.lang.reflect.Method}, read its return and parameter types
 * and modifiers, plus annotation queries. The method was resolved with
 * accessibility opened, so private methods invoke like public ones;
 * numeric arguments convert to the declared parameter types.
 *
 * @zh 已声明方法句柄：经 {@code java.lang.reflect.Method} 调用方法，读取返回类型、参数类型与修饰符，并支持注解查询。解析时已开启可访问性，私有方法与公共方法一样调用；数字参数按声明类型转换。
 */
@ApiClass(name = "ReflectMethod", kind = ApiClassKind.HANDLE)
@ApiSince("0.7.0")
@ApiMC("*")
@ApiLoader("*")
public final class ReflectMethod implements ScriptHandle {

    private final Method method;
    private final ReflectApi.ClassResolver resolver;

    ReflectMethod(Method method, ReflectApi.ClassResolver resolver) {
        this.method = method;
        this.resolver = resolver;
    }

    /** The wrapped method (host-side only). */
    Method member() {
        return method;
    }

    /**
     * The name of the method.
     *
     * @zh 方法名。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getName() {
        return method.getName();
    }

    /**
     * Invokes the method on the target instance. Pass null/undefined as
     * the target for a static method; arguments arrive as an array and
     * convert to the declared parameter types.
     *
     * @zh 在目标实例上调用方法。静态方法的 target 传 null/undefined；参数以数组传入并按声明类型转换。
     * @zh-param target 接收者实例（静态调用传 null）
     * @zh-param args 参数数组（可省略）
     * @zh-return 调用结果
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("JsonValue | JavaObject")
    public Object invoke(@ApiType("JavaObject") Object target,
            @ApiOptional @ApiType("JsonValue | JavaObject") Object args) {
        Object[] argv = ReflectSupport.positional(args, method.getParameterCount());
        Object[] converted = new Object[argv.length];
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < argv.length; i++) {
            converted[i] = ReflectSupport.coerce(argv[i], parameters[i]);
        }
        try {
            return ReflectSupport.marshal(method.invoke(ReflectSupport.isStatic(method) ? null
                    : ReflectSupport.unwrap(target), converted));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(String.valueOf(cause), cause);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new IllegalStateException("method not invocable: " + method.getName(), e);
        }
    }

    /**
     * The declared return type of the method as a class handle.
     *
     * @zh 方法声明返回类型的类句柄。
     * @zh-return 类句柄
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ReflectClass returnType() {
        return new ReflectClass(method.getReturnType(), resolver);
    }

    /**
     * The declared parameter types of the method as class handles.
     *
     * @zh 方法声明参数类型的类句柄数组。
     * @zh-return 类句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectClass>")
    public List<ReflectClass> parameterTypes() {
        List<ReflectClass> out = new ArrayList<>();
        for (Class<?> parameter : method.getParameterTypes()) {
            out.add(new ReflectClass(parameter, resolver));
        }
        return out;
    }

    /**
     * The modifier bits of the method (the raw
     * {@code java.lang.reflect.Modifier} mask).
     *
     * @zh 方法的修饰符位掩码（原始 {@code java.lang.reflect.Modifier} 值）。
     * @zh-return 修饰符整数
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int modifiers() {
        return method.getModifiers();
    }

    /**
     * Whether the method is public.
     *
     * @zh 方法是否为 public。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isPublic() {
        return java.lang.reflect.Modifier.isPublic(method.getModifiers());
    }

    /**
     * Whether the method is private.
     *
     * @zh 方法是否为 private。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isPrivate() {
        return java.lang.reflect.Modifier.isPrivate(method.getModifiers());
    }

    /**
     * Whether the method is protected.
     *
     * @zh 方法是否为 protected。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isProtected() {
        return java.lang.reflect.Modifier.isProtected(method.getModifiers());
    }

    /**
     * Whether the method is static.
     *
     * @zh 方法是否为 static。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isStatic() {
        return java.lang.reflect.Modifier.isStatic(method.getModifiers());
    }

    /**
     * Whether the method is final.
     *
     * @zh 方法是否为 final。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isFinal() {
        return java.lang.reflect.Modifier.isFinal(method.getModifiers());
    }

    /**
     * Whether the method is synchronized.
     *
     * @zh 方法是否为 synchronized。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isSynchronized() {
        return java.lang.reflect.Modifier.isSynchronized(method.getModifiers());
    }

    /**
     * Whether the method is volatile.
     *
     * @zh 方法是否为 volatile。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isVolatile() {
        return java.lang.reflect.Modifier.isVolatile(method.getModifiers());
    }

    /**
     * Whether the method is transient.
     *
     * @zh 方法是否为 transient。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isTransient() {
        return java.lang.reflect.Modifier.isTransient(method.getModifiers());
    }

    /**
     * Whether the method is native.
     *
     * @zh 方法是否为 native。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isNative() {
        return java.lang.reflect.Modifier.isNative(method.getModifiers());
    }

    /**
     * Whether the method is an interface method.
     *
     * @zh 方法是否为接口方法。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isInterface() {
        return java.lang.reflect.Modifier.isInterface(method.getModifiers());
    }

    /**
     * Whether the method is abstract.
     *
     * @zh 方法是否为 abstract。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isAbstract() {
        return java.lang.reflect.Modifier.isAbstract(method.getModifiers());
    }

    /**
     * Whether the method is strict (strictfp).
     *
     * @zh 方法是否为 strict（strictfp）。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isStrict() {
        return java.lang.reflect.Modifier.isStrict(method.getModifiers());
    }

    /**
     * The annotation of the given class on this method, or undefined when
     * absent. Only runtime-retained annotations are visible.
     *
     * @zh 该注解类在本方法上的注解实例；不存在为 undefined。仅运行时保留的注解可见。
     * @zh-param type 注解类（{@code ReflectClass} / {@code JavaClass} / 类名字符串）
     * @zh-return 注解代理或 undefined
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("JavaObject")
    public Object annotation(@ApiType("ReflectClass | JavaClass | string") Object type) {
        return ReflectSupport.wrapAnnotation(
                method.getAnnotation(ReflectSupport.annotationType(type, resolver)));
    }

    /**
     * All annotations present on this method.
     *
     * @zh 本方法上的全部注解。
     * @zh-return 注解代理数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<JavaObject>")
    public List<Object> annotations() {
        return ReflectSupport.wrapAll(method.getAnnotations());
    }

    /**
     * All annotations declared on this method itself.
     *
     * @zh 本方法自身声明的全部注解。
     * @zh-return 注解代理数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<JavaObject>")
    public List<Object> declaredAnnotations() {
        return ReflectSupport.wrapAll(method.getDeclaredAnnotations());
    }

    /**
     * Whether the given annotation class is present on this method. Only
     * runtime-retained annotations are visible.
     *
     * @zh 该注解类是否存在于本方法。仅运行时保留的注解可见。
     * @zh-param type 注解类（{@code ReflectClass} / {@code JavaClass} / 类名字符串）
     * @zh-return 是否存在
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isAnnotationPresent(@ApiType("ReflectClass | JavaClass | string") Object type) {
        return method.isAnnotationPresent(ReflectSupport.annotationType(type, resolver));
    }

    /**
     * The raw host {@code Method} object as a {@code JavaObject} proxy.
     * Requires the {@code java} permission.
     *
     * @zh 原始宿主 {@code Method} 对象（{@code JavaObject} 代理）。需 {@code java} 权限。
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiPermission("java")
    @ApiType("JavaObject")
    public Object getRaw() {
        return method;
    }

    @Override
    public String toString() {
        return "ReflectMethod(" + method.getName() + ")";
    }
}
