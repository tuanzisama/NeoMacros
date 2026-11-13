package ink.tuanzi.neomacros.port.model;

import java.util.List;

/**
 * Neutral snapshot of one completed client resource reload: the boot-load
 * flag and the selected resource-pack identifiers copied when the reload
 * finished. Later changes to the selection do not update this list.
 *
 * <p>Neutral value object carried across port signatures; never a live
 * game reference.</p>
 *
 * @zh 一次已完成的客户端资源重载的中性快照：启动加载标记与重载完成时复制的已选中资源包标识。之后的选择变更不会更新该列表。
 */
public record ResourcePackLoadData(boolean isGameStart, List<String> loadedPacks) {
}
