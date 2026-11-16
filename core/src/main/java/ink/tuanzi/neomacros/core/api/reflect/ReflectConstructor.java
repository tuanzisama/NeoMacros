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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handle over one declared constructor: create instances through
 * {@code java.lang.reflect.Constructor}, read its parameter types and
 * modifiers, plus annotation queries. The constructor was resolved with
 * accessibility opened, so private constructors instantiate like public
 * ones; numeric arguments convert to the declared parameter types.
 *
 * @zh 已声明构造器句柄：经 {@code java.lang.reflect.Constructor} 构造实例，读取参数类型与修饰符，并支持注解查询。解析时已开启可访问性，私有构造器与公共构造器一样实例化；数字参数按声明类型转换。
 */
@ApiClass(name = "ReflectConstructor", kind = ApiClassKind.HANDLE)
@ApiSince("0.7.0")
@ApiMC("*")
@ApiLoader("*")
public final class ReflectConstructor implements ScriptHandle {

    private final Constructor<?> constructor;
    private final ReflectApi.ClassResolver resolver;

    ReflectConstructor(Constructor<?> constructor, ReflectApi.ClassResolver resolver) {
        this.constructor = constructor;
        this.resolver = resolver;
    }

    /** The wrapped constructor (host-side only). */
    Constructor<?> member() {
        return constructor;
    }

    /**
     * Creates one instance with the given constructor arguments; arguments
     * arrive as an array and convert to the declared parameter types.
     *
     * @zh 以给定构造参数创建一个实例；参数以数组传入并按声明类型转换。
     * @zh-param args 构造参数数组（可省略）
     * @zh-return 新实例
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("JavaObject")
    public Object newInstance(
            @ApiOptional @ApiType("JsonValue | JavaObject") Object args) {
        Object[] argv = ReflectSupport.positional(args, constructor.getParameterCount());
        Object[] converted = new Object[argv.length];
        Class<?>[] parameters = constructor.getParameterTypes();
        for (int i = 0; i < argv.length; i++) {
            converted[i] = ReflectSupport.coerce(argv[i], parameters[i]);
        }
        try {
            return ReflectSupport.marshal(constructor.newInstance(converted));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(String.valueOf(cause), cause);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new IllegalStateException("constructor failed on "
                    + constructor.getDeclaringClass().getName(), e);
        }
    }

    /**
     * The declared parameter types of the constructor as class handles.
     *
     * @zh 构造器声明参数类型的类句柄数组。
     * @zh-return 类句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectClass>")
    public List<ReflectClass> parameterTypes() {
        List<ReflectClass> out = new ArrayList<>();
        for (Class<?> parameter : constructor.getParameterTypes()) {
            out.add(new ReflectClass(parameter, resolver));
        }
        return out;
    }

    /**
     * The modifier bits of the constructor (the raw
     * {@code java.lang.reflect.Modifier} mask).
     *
     * @zh 构造器的修饰符位掩码（原始 {@code java.lang.reflect.Modifier} 值）。
     * @zh-return 修饰符整数
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int modifiers() {
        return constructor.getModifiers();
    }

    /**
     * Whether the constructor is public.
     *
     * @zh 构造器是否为 public。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isPublic() {
        return java.lang.reflect.Modifier.isPublic(constructor.getModifiers());
    }

    /**
     * Whether the constructor is private.
     *
     * @zh 构造器是否为 private。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isPrivate() {
        return java.lang.reflect.Modifier.isPrivate(constructor.getModifiers());
    }

    /**
     * Whether the constructor is protected.
     *
     * @zh 构造器是否为 protected。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isProtected() {
        return java.lang.reflect.Modifier.isProtected(constructor.getModifiers());
    }

    /**
     * Whether the constructor is static.
     *
     * @zh 构造器是否为 static。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isStatic() {
        return java.lang.reflect.Modifier.isStatic(constructor.getModifiers());
    }

    /**
     * Whether the constructor is final.
     *
     * @zh 构造器是否为 final。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isFinal() {
        return java.lang.reflect.Modifier.isFinal(constructor.getModifiers());
    }

    /**
     * Whether the constructor is synchronized.
     *
     * @zh 构造器是否为 synchronized。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isSynchronized() {
        return java.lang.reflect.Modifier.isSynchronized(constructor.getModifiers());
    }

    /**
     * Whether the constructor is volatile.
     *
     * @zh 构造器是否为 volatile。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isVolatile() {
        return java.lang.reflect.Modifier.isVolatile(constructor.getModifiers());
    }

    /**
     * Whether the constructor is transient.
     *
     * @zh 构造器是否为 transient。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isTransient() {
        return java.lang.reflect.Modifier.isTransient(constructor.getModifiers());
    }

    /**
     * Whether the constructor is native.
     *
     * @zh 构造器是否为 native。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isNative() {
        return java.lang.reflect.Modifier.isNative(constructor.getModifiers());
    }

    /**
     * Whether the constructor is an interface constructor.
     *
     * @zh 构造器是否为接口构造器。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isInterface() {
        return java.lang.reflect.Modifier.isInterface(constructor.getModifiers());
    }

    /**
     * Whether the constructor is abstract.
     *
     * @zh 构造器是否为 abstract。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isAbstract() {
        return java.lang.reflect.Modifier.isAbstract(constructor.getModifiers());
    }

    /**
     * Whether the constructor is strict (strictfp).
     *
     * @zh 构造器是否为 strict（strictfp）。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isStrict() {
        return java.lang.reflect.Modifier.isStrict(constructor.getModifiers());
    }

    /**
     * The annotation of the given class on this constructor, or undefined
     * when absent. Only runtime-retained annotations are visible.
     *
     * @zh 该注解类在本构造器上的注解实例；不存在为 undefined。仅运行时保留的注解可见。
     * @zh-param type 注解类（{@code ReflectClass} / {@code JavaClass} / 类名字符串）
     * @zh-return 注解代理或 undefined
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("JavaObject")
    public Object annotation(@ApiType("ReflectClass | JavaClass | string") Object type) {
        return ReflectSupport.wrapAnnotation(
                constructor.getAnnotation(ReflectSupport.annotationType(type, resolver)));
    }

    /**
     * All annotations present on this constructor.
     *
     * @zh 本构造器上的全部注解。
     * @zh-return 注解代理数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<JavaObject>")
    public List<Object> annotations() {
        return ReflectSupport.wrapAll(constructor.getAnnotations());
    }

    /**
     * All annotations declared on this constructor itself.
     *
     * @zh 本构造器自身声明的全部注解。
     * @zh-return 注解代理数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<JavaObject>")
    public List<Object> declaredAnnotations() {
        return ReflectSupport.wrapAll(constructor.getDeclaredAnnotations());
    }

    /**
     * Whether the given annotation class is present on this constructor.
     * Only runtime-retained annotations are visible.
     *
     * @zh 该注解类是否存在于本构造器。仅运行时保留的注解可见。
     * @zh-param type 注解类（{@code ReflectClass} / {@code JavaClass} / 类名字符串）
     * @zh-return 是否存在
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isAnnotationPresent(@ApiType("ReflectClass | JavaClass | string") Object type) {
        return constructor.isAnnotationPresent(
                ReflectSupport.annotationType(type, resolver));
    }

    /**
     * The raw host {@code Constructor} object as a {@code JavaObject}
     * proxy. Requires the {@code java} permission.
     *
     * @zh 原始宿主 {@code Constructor} 对象（{@code JavaObject} 代理）。需 {@code java} 权限。
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiPermission("java")
    @ApiType("JavaObject")
    public Object getRaw() {
        return constructor;
    }

    @Override
    public String toString() {
        return "ReflectConstructor(" + constructor.getDeclaringClass().getName() + ")";
    }
}
