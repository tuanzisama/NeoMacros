package ink.tuanzi.neomacros.core.api.screen;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code neo.screen} module: metadata about the currently open screen.
 * The script-built GUI surface stays a later-phase design.
 *
 * @zh {@code neo.screen}：当前打开屏幕的元信息。脚本自建 GUI 属后续阶段设计。
 */
@ApiModule(name = "screen", category = "界面")
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class ScreenApi {

    /**
     * The currently open screen as {@code {title, kind}}, or {@code null}
     * when no screen is open. {@code kind} carries the container screen kind
     * ({@code chest}, {@code crafting}, ...) for container screens and
     * {@code screen} otherwise; the container itself is reachable through
     * {@code player.opened}.
     *
     * @zh 当前打开的屏幕 {@code {title, kind}}；无屏幕时为 null。容器屏幕的 {@code kind} 为容器类别（{@code chest}、{@code crafting} 等），其余为 {@code screen}；容器本体经 {@code player.opened} 获取。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public Map<String, Object> getOpened() {
        var lifecycle = ScriptApiEnvironment.adapter().lifecycle();
        if (!lifecycle.isScreenOpen()) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("title", lifecycle.screenTitle().orElse(""));
        view.put("kind", ScriptApiEnvironment.adapter().inventory().openedContainer()
                .map(ink.tuanzi.neomacros.port.InventoryPort.ContainerInfo::kind)
                .orElse("screen"));
        return view;
    }
}
