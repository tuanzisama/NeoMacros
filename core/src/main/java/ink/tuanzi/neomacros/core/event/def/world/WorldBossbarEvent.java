package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the boss bar event; fires for each boss bar operation the
 * client receives: {@code add}, {@code remove}, {@code updateProgress},
 * {@code updateName}, {@code updateStyle}, {@code updateProperties}. Fields
 * the operation does not carry are {@code null}.
 *
 * @zh Boss 血条更新时触发。
 * @zh-param uuid 血条标识
 * @zh-param action 操作类型（add/remove/updateProgress/updateName/updateStyle/updateProperties）
 * @zh-param name 血条标题（仅 add/updateName 携带，其余为 null）
 * @zh-param progress 血条进度 0.0~1.0（仅 add/updateProgress 携带，其余为 null）
 */
@ApiSince("0.1.0")
@ApiEvent(name = "world:bossbar")
public record WorldBossbarEvent(String uuid, String action, String name, Float progress) {
}
