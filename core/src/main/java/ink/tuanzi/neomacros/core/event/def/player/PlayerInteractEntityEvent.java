package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;

/**
 * Payload of the entity interaction event; synchronous subscribers may
 * cancel the interaction.
 *
 * @zh 玩家与实体交互事件载荷；同步订阅可取消交互。
 * @zh-param target 被交互实体的时点快照
 */
@ApiSince("0.1.0")
@ApiEvent(name = "player:interactEntity", cancellable = true)
public record PlayerInteractEntityEvent(EntitySnapshot target) {
}
