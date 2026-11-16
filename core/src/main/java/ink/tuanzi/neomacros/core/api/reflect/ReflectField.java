package ink.tuanzi.neomacros.core.api.reflect;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Handle over one declared field: read and write through
 * {@code java.lang.reflect.Field}, the field's type and modifiers, plus
 * annotation queries. The field was resolved with accessibility opened, so
 * private members read and write like public ones.
 *
 * @zh 已声明字段句柄：经 {@code java.lang.reflect.Field} 读写字段值，查询字段类型与修饰符，并支持注解查询。解析时已开启可访问性，私有成员与公共成员一样读写。
 */
@ApiClass(name = "ReflectField", kind = ApiClassKind.HANDLE)
@ApiSince("0.7.0")
@ApiMC("*")
@ApiLoader("*")
public final class ReflectField implements ScriptHandle {

    private final Field field;
    private final ReflectApi.ClassResolver resolver;

    ReflectField(Field field, ReflectApi.ClassResolver resolver) {
        this.field = field;
        this.resolver = resolver;
    }

    /** The wrapped field (host-side only). */
    Field member() {
        return field;
    }

    /**
     * The name of the field.
     *
     * @zh 字段名。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getName() {
        return field.getName();
    }

    /**
     * Reads the field value. Omitting the target reads a static field;
     * instance fields need their instance.
     *
     * @zh 读取字段值。省略 target 读静态字段；实例字段须传实例。
     * @zh-param target 实例（静态字段省略）
     * @zh-return 字段值
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("JsonValue | JavaObject")
    public Object get(@ApiType("JavaObject") Object target) {
        try {
            return ReflectSupport.marshal(field.get(ReflectSupport.unwrap(target)));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("field not readable: " + field.getName(), e);
        }
    }

    /**
     * Writes the field value (numbers convert to the declared type). Pass
     * null/undefined as the target for a static field.
     *
     * @zh 写入字段值（数字按声明类型转换）。静态字段的 target 传 null/undefined。
     * @zh-param target 实例（静态字段传 null）
     * @zh-param value 字段值
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void set(@ApiType("JavaObject") Object target,
            @ApiType("JsonValue | JavaObject") Object value) {
        Object coerced = ReflectSupport.coerce(value, field.getType());
        try {
            field.set(ReflectSupport.unwrap(target), coerced);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("field not writable: " + field.getName(), e);
        }
    }

    /**
     * The declared type of the field as a class handle.
     *
     * @zh 字段声明类型的类句柄。
     * @zh-return 类句柄
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ReflectClass type() {
        return new ReflectClass(field.getType(), resolver);
    }

    /**
     * The modifier bits of the field (the raw
     * {@code java.lang.reflect.Modifier} mask).
     *
     * @zh 字段的修饰符位掩码（原始 {@code java.lang.reflect.Modifier} 值）。
     * @zh-return 修饰符整数
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int modifiers() {
        return field.getModifiers();
    }

    /**
     * Whether the field is public.
     *
     * @zh 字段是否为 public。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isPublic() {
        return java.lang.reflect.Modifier.isPublic(field.getModifiers());
    }

    /**
     * Whether the field is private.
     *
     * @zh 字段是否为 private。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isPrivate() {
        return java.lang.reflect.Modifier.isPrivate(field.getModifiers());
    }

    /**
     * Whether the field is protected.
     *
     * @zh 字段是否为 protected。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isProtected() {
        return java.lang.reflect.Modifier.isProtected(field.getModifiers());
    }

    /**
     * Whether the field is static.
     *
     * @zh 字段是否为 static。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isStatic() {
        return java.lang.reflect.Modifier.isStatic(field.getModifiers());
    }

    /**
     * Whether the field is final.
     *
     * @zh 字段是否为 final。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isFinal() {
        return java.lang.reflect.Modifier.isFinal(field.getModifiers());
    }

    /**
     * Whether the field is synchronized.
     *
     * @zh 字段是否为 synchronized。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isSynchronized() {
        return java.lang.reflect.Modifier.isSynchronized(field.getModifiers());
    }

    /**
     * Whether the field is volatile.
     *
     * @zh 字段是否为 volatile。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isVolatile() {
        return java.lang.reflect.Modifier.isVolatile(field.getModifiers());
    }

    /**
     * Whether the field is transient.
     *
     * @zh 字段是否为 transient。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isTransient() {
        return java.lang.reflect.Modifier.isTransient(field.getModifiers());
    }

    /**
     * Whether the field is native.
     *
     * @zh 字段是否为 native。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isNative() {
        return java.lang.reflect.Modifier.isNative(field.getModifiers());
    }

    /**
     * Whether the field is an interface field.
     *
     * @zh 字段是否为接口字段。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isInterface() {
        return java.lang.reflect.Modifier.isInterface(field.getModifiers());
    }

    /**
     * Whether the field is abstract.
     *
     * @zh 字段是否为 abstract。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isAbstract() {
        return java.lang.reflect.Modifier.isAbstract(field.getModifiers());
    }

    /**
     * Whether the field is strict (strictfp).
     *
     * @zh 字段是否为 strict（strictfp）。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isStrict() {
        return java.lang.reflect.Modifier.isStrict(field.getModifiers());
    }

    /**
     * The annotation of the given class on this field, or undefined when
     * absent. Only runtime-retained annotations are visible.
     *
     * @zh 该注解类在本字段上的注解实例；不存在为 undefined。仅运行时保留的注解可见。
     * @zh-param type 注解类（{@code ReflectClass} / {@code JavaClass} / 类名字符串）
     * @zh-return 注解代理或 undefined
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("JavaObject")
    public Object annotation(@ApiType("ReflectClass | JavaClass | string") Object type) {
        return ReflectSupport.wrapAnnotation(
                field.getAnnotation(ReflectSupport.annotationType(type, resolver)));
    }

    /**
     * All annotations present on this field.
     *
     * @zh 本字段上的全部注解。
     * @zh-return 注解代理数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<JavaObject>")
    public List<Object> annotations() {
        return ReflectSupport.wrapAll(field.getAnnotations());
    }

    /**
     * All annotations declared on this field itself.
     *
     * @zh 本字段自身声明的全部注解。
     * @zh-return 注解代理数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<JavaObject>")
    public List<Object> declaredAnnotations() {
        return ReflectSupport.wrapAll(field.getDeclaredAnnotations());
    }

    /**
     * Whether the given annotation class is present on this field. Only
     * runtime-retained annotations are visible.
     *
     * @zh 该注解类是否存在于本字段。仅运行时保留的注解可见。
     * @zh-param type 注解类（{@code ReflectClass} / {@code JavaClass} / 类名字符串）
     * @zh-return 是否存在
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isAnnotationPresent(@ApiType("ReflectClass | JavaClass | string") Object type) {
        return field.isAnnotationPresent(ReflectSupport.annotationType(type, resolver));
    }

    /**
     * The raw host {@code Field} object as a {@code JavaObject} proxy.
     * Requires the {@code java} permission.
     *
     * @zh 原始宿主 {@code Field} 对象（{@code JavaObject} 代理）。需 {@code java} 权限。
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiPermission("java")
    @ApiType("JavaObject")
    public Object getRaw() {
        return field;
    }

    @Override
    public String toString() {
        return "ReflectField(" + field.getName() + ")";
    }
}
