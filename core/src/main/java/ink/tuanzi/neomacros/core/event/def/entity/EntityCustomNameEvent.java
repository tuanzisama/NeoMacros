package ink.tuanzi.neomacros.core.event.def.entity;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.Objects;
import java.util.UUID;

/**
 * Payload of the entity custom-name change event; fires when a tracked
 * entity's custom name differs from the previous sample. {@code oldName}
 * and {@code newName} are {@code null} when no custom name was set (a
 * cleared name reports {@code null} in {@code newName}). The JsMacros
 * {@code entity} live handle is not part of the frozen payload; the
 * identity face is {@code uuid}, and the live handle re-fetches through
 * the entity API lookup.
 *
 * @zh 实体自定义名变更事件载荷；受跟踪实体的自定义名与上次采样不同时触发。{@code oldName} 与 {@code newName} 为 null 表示当时没有自定义名（清除名字时 {@code newName} 为 null）。JsMacros 的 {@code entity} 活句柄不进冻结载荷；标识面为 {@code uuid}，活句柄经实体 API 查询重新获取。
 * @zh-param uuid 实体的 UUID
 * @zh-param oldName 变更前的自定义名；无自定义名时为 null
 * @zh-param newName 变更后的自定义名；清除名字时为 null
 */
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "entity:customName", direction = ApiEvent.Direction.LOCAL)
public record EntityCustomNameEvent(
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") UUID uuid,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String oldName,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String newName) {
    public EntityCustomNameEvent {
        Objects.requireNonNull(uuid, "uuid cannot be null");
    }
}
