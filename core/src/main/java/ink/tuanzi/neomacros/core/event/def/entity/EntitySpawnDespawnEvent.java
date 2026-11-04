package ink.tuanzi.neomacros.core.event.def.entity;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the entity spawn/despawn edge events derived from per-tick
 * entity tracking. These are distinct from entity:load/unload which track
 * the vanilla entity lifecycle.
 *
 * @zh 实体进入/离开跟踪范围时触发。
 * @zh-param uuid 实体 UUID
 * @zh-param entityTypeId 实体类型标识
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "entity:spawn")
public record EntitySpawnDespawnEvent(java.util.UUID uuid, String entityTypeId) {
}
