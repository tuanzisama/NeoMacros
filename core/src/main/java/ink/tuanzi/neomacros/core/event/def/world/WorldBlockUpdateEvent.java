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
 * @zh-param x 方块 X 坐标
 * @zh-param y 方块 Y 坐标
 * @zh-param z 方块 Z 坐标
 * @zh-param blockId 新方块标识（如 minecraft:stone）
 */
@ApiSince("0.1.0")
@ApiEvent(name = "world:blockUpdate")
public record WorldBlockUpdateEvent(int x, int y, int z, String blockId) {
}
