package ink.tuanzi.neomacros.core;

/**
 * 全局常量（mod id / 版本元信息）。
 *
 * <p>本类同时充当版本无关模块 Java 17 字节码基线的编译验证锚点
 * （T-000 冒烟断言 major=61）。</p>
 */
public final class NeoMacros {
    /** mod id（与 fabric.mod.json、目录名一致）。 */
    public static final String MOD_ID = "neomacros";

    /** 当前 NeoMacros API 版本（由构建注入前的占位；正式版本化由 T-001/T-106 接管）。 */
    public static final String VERSION = "0.1.0";

    private NeoMacros() {
    }
}
