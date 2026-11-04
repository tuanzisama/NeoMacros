package ink.tuanzi.neomacros.core.event.def.entity;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the entity unload event; fires when an entity leaves the
 * client's tracked range.
 *
 * @zh 实体离开跟踪范围时触发。
 * @zh-param entity 实体快照
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "entity:unload")
public record EntityUnloadEvent(ink.tuanzi.neomacros.port.model.EntitySnapshot entity) {
}
