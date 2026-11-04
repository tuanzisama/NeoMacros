package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.Objects;
import java.util.UUID;

/**
 * Payload of the local player leaving the world event.
 *
 * @zh 本地玩家离开世界事件载荷。
 * @zh-param uuid 玩家的 UUID
 * @zh-param name 玩家名称
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:leave")
public record PlayerLeaveEvent(UUID uuid, String name) {
    public PlayerLeaveEvent {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}
