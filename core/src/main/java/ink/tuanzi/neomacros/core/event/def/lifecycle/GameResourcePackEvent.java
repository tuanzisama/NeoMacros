package ink.tuanzi.neomacros.core.event.def.lifecycle;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.List;

/**
 * Payload of the resource pack reload event; fires when a client resource
 * reload completes and the loading overlay begins fading out. The overlay
 * is still visible when the event fires.
 * {@code isGameStart} marks the initial startup load (the boot overlay
 * runs without a fade-in); every later reload reports {@code false}.
 * {@code loadedPacks} copies the repository's selected pack identifiers
 * at fire time; later selection changes do not update the list.
 *
 * @zh 资源包重载事件载荷；客户端资源重载完成、加载遮罩开始淡出时触发（事件触发时遮罩仍可见）。{@code isGameStart} 标记启动时的初始加载（启动遮罩无淡入），之后的重载均为 false。{@code loadedPacks} 在触发时复制仓库已选中的资源包标识，之后的选择变更不会更新该列表。
 * @zh-param isGameStart 是否为启动时的初始加载
 * @zh-param loadedPacks 触发时已选中的资源包标识列表
 */
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "game:resourcePack", direction = ApiEvent.Direction.LOCAL)
public record GameResourcePackEvent(
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") boolean isGameStart,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") List<String> loadedPacks) {
}
