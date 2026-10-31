package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.Objects;

/**
 * Payload of the local player changing dimension event.
 *
 * @zh 本地玩家切换维度事件载荷。
 * @zh-param from 原维度标识
 * @zh-param to 新维度标识
 */
@ApiSince("0.1.0")
@ApiEvent(name = "player:dimensionChange")
public record PlayerDimensionChangeEvent(String from, String to) {
    public PlayerDimensionChangeEvent {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
    }
}
