package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the block update event; fires for each block state change the
 * client receives. High-traffic event: the payload carries only the fields
 * needed to locate the change, and it is excluded from wildcard matching by
 * default.
 *
 * @zh 方块更新时触发（高流量事件）。
 * @zh-param pos 方块网格坐标（{@code BlockPos} 实例）
 * @zh-param id 新方块标识（如 minecraft:stone）
 */
@ApiSince("0.1.0")
@ApiEvent(name = "world:blockUpdate")
public record WorldBlockUpdateEvent(ink.tuanzi.neomacros.core.api.types.BlockPos pos, String id) {
}
