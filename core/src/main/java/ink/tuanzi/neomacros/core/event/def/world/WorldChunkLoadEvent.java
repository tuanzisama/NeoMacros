package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.core.api.types.ChunkPos;
import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the client chunk load event; fires when a chunk arrives from
 * the server.
 *
 * @zh 客户端区块加载时触发。
 * @zh-param chunk 区块坐标（{@code ChunkPos} 实例）
 */
@ApiSince("0.1.0")
@ApiEvent(name = "world:chunkLoad")
public record WorldChunkLoadEvent(ChunkPos chunk) {
}
