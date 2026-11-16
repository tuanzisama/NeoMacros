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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Handle over one host class: the enumeration surface of
 * {@code java.lang.Class} (fields, methods, constructors, superclass,
 * interfaces), exact-signature member lookup, and annotation queries.
 * Obtained from {@code reflect.forName} / {@code reflect.classOf} or a
 * parent handle; rides the {@code java} permission gate with the module.
 *
 * @zh 宿主类句柄：{@code java.lang.Class} 的枚举面（字段、方法、构造器、父类、接口）、精确签名成员解析与注解查询。经 {@code reflect.forName} / {@code reflect.classOf} 或父句柄取得；随模块走 {@code java} 权限闸。
 */
@ApiClass(name = "ReflectClass", kind = ApiClassKind.HANDLE)
@ApiSince("0.7.0")
@ApiMC("*")
@ApiLoader("*")
public final class ReflectClass implements ScriptHandle {

    private final Class<?> target;
    private final ReflectApi.ClassResolver resolver;

    ReflectClass(Class<?> target, ReflectApi.ClassResolver resolver) {
        this.target = target;
        this.resolver = resolver;
    }

    /** The wrapped class (host-side only). */
    Class<?> target() {
        return target;
    }

    /**
     * The fully-qualified name of the wrapped class.
     *
     * @zh 被包类的全限定名。
     */
    @ApiReadOnly
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getClassName() {
        return target.getName();
    }

    /**
     * All public fields of the class, inherited ones included.
     *
     * @zh 类的全部公共字段（含继承）。
     * @zh-return 字段句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectField>")
    public List<ReflectField> fields() {
        List<ReflectField> out = new ArrayList<>();
        for (Field field : target.getFields()) {
            out.add(ReflectSupport.fieldHandle(field, resolver));
        }
        return out;
    }

    /**
     * All fields declared on the class itself, private ones included.
     *
     * @zh 类自身声明的全部字段（含私有）。
     * @zh-return 字段句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectField>")
    public List<ReflectField> declaredFields() {
        List<ReflectField> out = new ArrayList<>();
        for (Field field : target.getDeclaredFields()) {
            out.add(ReflectSupport.fieldHandle(field, resolver));
        }
        return out;
    }

    /**
     * All public methods of the class, inherited ones included.
     *
     * @zh 类的全部公共方法（含继承）。
     * @zh-return 方法句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectMethod>")
    public List<ReflectMethod> methods() {
        List<ReflectMethod> out = new ArrayList<>();
        for (Method method : target.getMethods()) {
            out.add(ReflectSupport.methodHandle(method, resolver));
        }
        return out;
    }

    /**
     * All methods declared on the class itself, private ones included.
     *
     * @zh 类自身声明的全部方法（含私有）。
     * @zh-return 方法句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectMethod>")
    public List<ReflectMethod> declaredMethods() {
        List<ReflectMethod> out = new ArrayList<>();
        for (Method method : target.getDeclaredMethods()) {
            out.add(ReflectSupport.methodHandle(method, resolver));
        }
        return out;
    }

    /**
     * All public constructors of the class.
     *
     * @zh 类的全部公共构造器。
     * @zh-return 构造器句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectConstructor>")
    public List<ReflectConstructor> constructors() {
        List<ReflectConstructor> out = new ArrayList<>();
        for (Constructor<?> constructor : target.getConstructors()) {
            out.add(ReflectSupport.constructorHandle(constructor, resolver));
        }
        return out;
    }

    /**
     * All constructors declared on the class itself, private ones included.
     *
     * @zh 类自身声明的全部构造器（含私有）。
     * @zh-return 构造器句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectConstructor>")
    public List<ReflectConstructor> declaredConstructors() {
        List<ReflectConstructor> out = new ArrayList<>();
        for (Constructor<?> constructor : target.getDeclaredConstructors()) {
            out.add(ReflectSupport.constructorHandle(constructor, resolver));
        }
        return out;
    }

    /**
     * The superclass of the class; an interface or {@code Object} reads
     * undefined.
     *
     * @zh 父类；接口或 {@code Object} 读为 undefined。
     * @zh-return 父类句柄或 undefined
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ReflectClass superclass() {
        Class<?> parent = target.getSuperclass();
        return parent == null ? null : new ReflectClass(parent, resolver);
    }

    /**
     * All interfaces implemented by the class.
     *
     * @zh 类实现的全部接口。
     * @zh-return 接口句柄数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<ReflectClass>")
    public List<ReflectClass> interfaces() {
        List<ReflectClass> out = new ArrayList<>();
        for (Class<?> iface : target.getInterfaces()) {
            out.add(new ReflectClass(iface, resolver));
        }
        return out;
    }

    /**
     * Resolves one declared field by name, walking the superclass chain;
     * private fields included. Undefined on a miss.
     *
     * @zh 按名解析已声明字段（沿父类链查找，含私有）；未命中为 undefined。
     * @zh-param name 字段名
     * @zh-return 字段句柄或 undefined
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ReflectField field(String name) {
        for (Class<?> type = target; type != null; type = type.getSuperclass()) {
            try {
                return ReflectSupport.fieldHandle(type.getDeclaredField(name), resolver);
            } catch (NoSuchFieldException ignored) {
                // walk the superclass chain
            }
        }
        return null;
    }

    /**
     * Resolves one declared method with an exact parameter signature,
     * walking the superclass chain; private overloads included. Parameter
     * types arrive as class-name strings or {@code ReflectClass} values.
     * Undefined on a miss.
     *
     * @zh 按精确参数签名解析已声明方法（沿父类链查找，含私有重载）。参数类型为类名字符串或 {@code ReflectClass} 数组；未命中为 undefined。
     * @zh-param name 方法名
     * @zh-param paramTypes 参数类型数组（可省略）
     * @zh-return 方法句柄或 undefined
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ReflectMethod method(String name,
            @ApiOptional @ApiType("Array<string | JavaClass | ReflectClass>") Object paramTypes) {
        List<Class<?>> signature = ReflectSupport.signatureOf(paramTypes, resolver);
        for (Class<?> type = target; type != null; type = type.getSuperclass()) {
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
     * Resolves one declared constructor with an exact parameter signature;
     * private constructors included. Parameter types arrive as class-name
     * strings or {@code ReflectClass} values. Undefined on a miss.
     *
     * @zh 按精确参数签名解析已声明构造器（含私有）。参数类型为类名字符串或 {@code ReflectClass} 数组；未命中为 undefined。
     * @zh-param paramTypes 参数类型数组（可省略）
     * @zh-return 构造器句柄或 undefined
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ReflectConstructor constructor(
            @ApiOptional @ApiType("Array<string | JavaClass | ReflectClass>") Object paramTypes) {
        List<Class<?>> signature = ReflectSupport.signatureOf(paramTypes, resolver);
        for (Constructor<?> candidate : target.getDeclaredConstructors()) {
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
     * The annotation of the given class on this class, or undefined when
     * absent. Only runtime-retained annotations are visible.
     *
     * @zh 该注解类在本类上的注解实例；不存在为 undefined。仅运行时保留的注解可见。
     * @zh-param type 注解类（{@code ReflectClass} / {@code JavaClass} / 类名字符串）
     * @zh-return 注解代理或 undefined
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("JavaObject")
    public Object annotation(@ApiType("ReflectClass | JavaClass | string") Object type) {
        return ReflectSupport.wrapAnnotation(
                target.getAnnotation(ReflectSupport.annotationType(type, resolver)));
    }

    /**
     * All annotations present on this class (inherited ones included).
     *
     * @zh 本类上的全部注解（含继承）。
     * @zh-return 注解代理数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<JavaObject>")
    public List<Object> annotations() {
        return ReflectSupport.wrapAll(target.getAnnotations());
    }

    /**
     * All annotations declared on this class itself (inherited ones excluded).
     *
     * @zh 类自身声明的全部注解（不含继承）。
     * @zh-return 注解代理数组
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Array<JavaObject>")
    public List<Object> declaredAnnotations() {
        return ReflectSupport.wrapAll(target.getDeclaredAnnotations());
    }

    /**
     * Whether the given annotation class is present on this class. Only
     * runtime-retained annotations are visible.
     *
     * @zh 该注解类是否存在于本类。仅运行时保留的注解可见。
     * @zh-param type 注解类（{@code ReflectClass} / {@code JavaClass} / 类名字符串）
     * @zh-return 是否存在
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isAnnotationPresent(@ApiType("ReflectClass | JavaClass | string") Object type) {
        return target.isAnnotationPresent(ReflectSupport.annotationType(type, resolver));
    }

    /**
     * The raw host {@code Class} object as a {@code JavaObject} proxy.
     * Requires the {@code java} permission.
     *
     * @zh 原始宿主 {@code Class} 对象（{@code JavaObject} 代理）。需 {@code java} 权限。
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiPermission("java")
    @ApiType("JavaObject")
    public Object getRaw() {
        return target;
    }

    @Override
    public String toString() {
        return "ReflectClass(" + target.getName() + ")";
    }
}
