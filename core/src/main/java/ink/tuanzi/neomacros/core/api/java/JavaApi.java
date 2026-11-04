package ink.tuanzi.neomacros.core.api.java;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * The {@code neo.java} facade: the escape layer over the host JVM. Class
 * lookup uses binary names (Mojmap for Minecraft classes, translated at the
 * call site by the mapping layer); every capability of this module requires
 * the {@code java} permission — granting it hands the script full host
 * access with no class filtering. Construction happens through the class
 * proxy's {@code new(...)} member; results wrap as object proxies whose
 * members reach unwrapped Java fields and methods.
 */
@ApiModule(name = "java", category = "系统")
@ApiPermission("java")
@ApiSince("0.1.0")
public final class JavaApi {

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
    public JavaClass type(String name) throws ClassNotFoundException {
        return new JavaClass(Class.forName(name, true, JavaApi.class.getClassLoader()));
    }
}
