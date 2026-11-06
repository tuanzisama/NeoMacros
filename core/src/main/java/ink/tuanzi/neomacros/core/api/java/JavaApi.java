package ink.tuanzi.neomacros.core.api.java;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The {@code neo.java} facade: the escape layer over the host JVM. Class
 * lookup uses binary names (Mojmap for Minecraft classes, translated at the
 * call site by the mapping layer); every capability of this module requires
 * the {@code java} permission — granting it hands the script full host
 * access with no class filtering. Construction happens through the class
 * proxy's {@code new(...)} member; results wrap as object proxies whose
 * members reach unwrapped Java fields and methods.
 *
 * @zh {@code neo.java} 门面：宿主 JVM 的穿透层。类查找使用二进制名（Minecraft 类为 Mojmap 名，调用点经映射层翻译）；本模块全部能力需 java 权限——授予权限即交给脚本完整宿主访问，无类过滤。构造经类代理的 {@code new(...)} 成员完成，结果包装为对象代理，其成员直达未包装的 Java 字段与方法。
 */
@ApiModule(name = "java", category = "系统")
@ApiPermission("java")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class JavaApi {

    /** Translates a script-supplied class name into the runtime binary name. */
    @FunctionalInterface
    public interface ClassResolver {
        String resolve(String name) throws ClassNotFoundException;
    }

    /** Maps one raw host object onto its registered wrapper handle, or null. */
    @FunctionalInterface
    public interface RawWrapper {
        Object wrapRaw(Object raw);
    }

    private final ClassResolver resolver;
    private final RawWrapper wrapper;

    /** Creates the facade with identity name resolution (tests, unobf nodes). */
    public JavaApi() {
        this(name -> name);
    }

    /** Creates the facade with a mapping-layer name resolver. */
    public JavaApi(ClassResolver resolver) {
        this(resolver, null);
    }

    /** Creates the facade with a name resolver and a raw-to-handle wrapper. */
    public JavaApi(ClassResolver resolver, RawWrapper wrapper) {
        this.resolver = resolver;
        this.wrapper = wrapper;
    }

    /**
     * Wraps one raw host object into its registered handle (entity handles,
     * block state data); unregistered types return {@code null}. The inverse
     * of the handles' {@code raw} escape hatch.
     *
     * @zh 把原始宿主对象包装为已注册的句柄（实体句柄、方块状态数据等）；未注册类型返回 null。与各句柄的 {@code raw} 穿透口互逆。
     * @zh-param raw 宿主原始对象
     * @zh-return 包装句柄或 null
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Object wrap(Object raw) {
        if (raw == null || wrapper == null) {
            return null;
        }
        return wrapper.wrapRaw(raw);
    }

    /**
     * Looks up one host class by its binary name, e.g.
     * {@code java.util.UUID}. The returned class proxy exposes static fields
     * and methods directly, and {@code new(...)} constructs instances.
     *
     * @zh 按二进制名查找宿主类；返回的类代理可直接读静态成员并以 new(...) 构造实例。
     * @zh-param name 类的二进制名（Minecraft 类用 Mojmap 名，由映射层翻译）
     * @zh-return 类代理
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    /**
     * Loads one class as a {@code JavaClass} proxy. The name goes through
     * the mapping-layer translation; a primitive name ({@code int},
     * {@code boolean}, ...) resolves to its class directly. With two names,
     * the first miss automatically tries the alternate.
     *
     * @zh 加载类为 {@code JavaClass} 代理。类名经映射层翻译；基本类型名（{@code int}、{@code boolean} 等）直接解析。给两个名字时，首名未命中自动尝试次名。
     * @zh-param className Mojmap 类名或基本类型名
     * @zh-param alternate 备用类名（可省略）
     */
    public JavaClass type(String className, String alternate) throws ClassNotFoundException {
        Class<?> primitive = primitiveClass(className);
        if (primitive != null) {
            return new JavaClass(primitive);
        }
        if (alternate != null && !alternate.isBlank()) {
            try {
                return byName(className);
            } catch (ClassNotFoundException firstMiss) {
                Class<?> altPrimitive = primitiveClass(alternate);
                if (altPrimitive != null) {
                    return new JavaClass(altPrimitive);
                }
                return byName(alternate);
            }
        }
        return byName(className);
    }

    /** Loads one class through the mapping resolver. */
    private JavaClass byName(String name) throws ClassNotFoundException {
        return new JavaClass(Class.forName(resolver.resolve(name), true,
                JavaApi.class.getClassLoader()));
    }

    /** The primitive class behind a primitive name, or null. */
    private static Class<?> primitiveClass(String name) {
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

    /**
     * Builds one host object implementing the named interfaces, dispatching
     * method calls to the handler map's functions (void-return dispatch;
     * unhandled methods raise).
     *
     * @zh 构造实现指定接口的宿主对象，方法调用派发给处理表中的函数（void 派发；未处理的方法抛错）。
     * @zh-param interfaces 接口二进制名列表
     * @zh-param handlers 方法名到函数的映射
     * @zh-return 宿主代理对象
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Object proxy(Object interfaces, Object handlers) throws ClassNotFoundException {
        List<String> names = new ArrayList<>();
        if (interfaces instanceof List<?> list) {
            for (Object entry : list) {
                names.add(String.valueOf(entry));
            }
        } else if (interfaces != null) {
            names.add(String.valueOf(interfaces));
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("proxy needs at least one interface name");
        }
        if (!(handlers instanceof Map<?, ?> handlerMap)) {
            throw new IllegalArgumentException("proxy handlers must be a member map");
        }
        Class<?>[] types = new Class<?>[names.size()];
        for (int i = 0; i < names.size(); i++) {
            Class<?> type = Class.forName(resolver.resolve(names.get(i)), true,
                    JavaApi.class.getClassLoader());
            if (!type.isInterface()) {
                throw new IllegalArgumentException("proxy target is not an interface: "
                        + names.get(i));
            }
            types[i] = type;
        }
        return Proxy.newProxyInstance(JavaApi.class.getClassLoader(), types,
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "JavaProxy" + names;
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    Object handler = handlerMap.get(method.getName());
                    if (handler instanceof java.util.function.Consumer<?> consumer) {
                        ((java.util.function.Consumer<Object[]>) consumer)
                                .accept(args == null ? new Object[0] : args);
                        return defaultValue(method.getReturnType());
                    }
                    throw new IllegalStateException("proxy has no handler for "
                            + method.getName());
                });
    }

    /** The value a primitive-returning proxy method observes when handlers return nothing. */
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type.isPrimitive()) {
            return Integer.valueOf(0);
        }
        return null;
    }

    /**
     * Converts one script array into a host list wrapped as an interop
     * object, so it crosses back into Java APIs as a live java.util.List.
     *
     * @zh 把脚本数组包装为宿主 List 对象，可传回 Java API。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Object toList(Object scriptArray) {
        if (scriptArray instanceof List<?> list) {
            return JavaInterop.wrap(new ArrayList<>(list));
        }
        throw new IllegalArgumentException("toList expects an array, got "
                + (scriptArray == null ? "null" : scriptArray.getClass().getName()));
    }

    /**
     * Converts one host collection into a script array (element by element).
     *
     * @zh 把宿主集合逐元素转为脚本数组。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Object fromList(Object javaCollection) {
        if (javaCollection instanceof java.util.Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        throw new IllegalArgumentException("fromList expects a Java collection, got "
                + (javaCollection == null ? "null" : javaCollection.getClass().getName()));
    }

    /**
     * Converts one script array into a host array of the named component
     * type (empty name → Object[]).
     *
     * @zh 把脚本数组转为指定组件类型的宿主数组（空名 → Object[]）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Object toArray(Object scriptArray, String componentTypeName)
            throws ClassNotFoundException {
        if (!(scriptArray instanceof List<?> list)) {
            throw new IllegalArgumentException("toArray expects an array");
        }
        Class<?> component = switch (componentTypeName == null ? "" : componentTypeName) {
            case "", "java.lang.Object" -> Object.class;
            case "int" -> Integer.TYPE;
            case "long" -> Long.TYPE;
            case "boolean" -> Boolean.TYPE;
            case "double" -> Double.TYPE;
            case "float" -> Float.TYPE;
            case "short" -> Short.TYPE;
            case "byte" -> Byte.TYPE;
            case "char" -> Character.TYPE;
            default -> Class.forName(resolver.resolve(componentTypeName), true,
                    JavaApi.class.getClassLoader());
        };
        Object out = Array.newInstance(component, list.size());
        for (int i = 0; i < list.size(); i++) {
            Object adapted = JavaInteropResolver.adapt(component, list.get(i));
            if (adapted == null && list.get(i) != null) {
                throw new IllegalArgumentException("element " + i + " is not a "
                        + component.getName());
            }
            Array.set(out, i, adapted);
        }
        return out;
    }
}
