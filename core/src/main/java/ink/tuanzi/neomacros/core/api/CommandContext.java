package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.Map;

/**
 * The live value handed to a command handler: typed parsed arguments plus
 * the raw remainder string. Rendered to scripts as a flat handle with the
 * {@code args} property and the {@code raw} property.
 *
 * @zh 交给命令处理器的实时值：类型化解析参数加原始余串。以扁平句柄呈现给脚本，带 {@code args} 与 {@code raw} 属性。
 */
@ApiClass(name = "CommandContext", kind = ApiClassKind.HANDLE)
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class CommandContext implements ScriptHandle {

    private final Map<String, Object> args;
    private final String raw;

    CommandContext(Map<String, Object> args, String raw) {
        this.args = args;
        this.raw = raw;
    }

    /** The parsed, typed argument map (slot name → value).
     * @zh 解析后的类型化参数表（槽名 → 值）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    @ApiType("Record<string, number | string | boolean>")
    public Map<String, Object> getArgs() {
        return args;
    }

    /** The raw remainder string after the command name.
     * @zh 命令名之后的原始余串。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getRaw() {
        return raw;
    }
}
