package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.Objects;

/**
 * Payload of the client world changing dimension event.
 *
 * @zh 客户端世界切换维度事件载荷。
 * @zh-param from 原维度标识
 * @zh-param to 新维度标识
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "world:dimensionChange")
public record WorldDimensionChangeEvent(String from, String to) {
    public WorldDimensionChangeEvent {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
    }
}
