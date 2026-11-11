package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;

/**
 * Payload of the entity attack event; synchronous subscribers may cancel
 * the attack.
 *
 * @zh 玩家攻击实体事件载荷；同步订阅可取消攻击。
 * @zh-param target 被攻击实体的时点快照
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:attackEntity", direction = ApiEvent.Direction.C2S, cancellable = true)
public record PlayerAttackEntityEvent(EntitySnapshot target) {
}
