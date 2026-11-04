package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.core.api.types.ChunkPos;
import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the client chunk unload event; fires when the server drops a
 * chunk from the client's view.
 *
 * @zh 客户端区块卸载时触发。
 * @zh-param chunk 区块坐标（{@code ChunkPos} 实例）
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "world:chunkUnload")
public record WorldChunkUnloadEvent(ChunkPos chunk) {
}
