package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the client chunk unload event; fires when the server drops a
 * chunk from the client's view.
 *
 * @zh 客户端区块卸载时触发。
 * @zh-param chunkX 区块 X 坐标
 * @zh-param chunkZ 区块 Z 坐标
 */
@ApiSince("0.1.0")
@ApiEvent(name = "world:chunkUnload")
public record WorldChunkUnloadEvent(int chunkX, int chunkZ) {
}
