package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the sound event; fires when the client plays a sound.
 * High-traffic, so wildcard dispatch skips it by default.
 *
 * @zh 声音播放事件；高流量事件，默认不进通配分发。
 * @zh-param soundId 声音标识（如 minecraft:block.note_block.pling）
 * @zh-param pos 声音源位置（{@code Pos3D} 实例）
 * @zh-param volume 音量
 * @zh-param pitch 音调
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "world:sound", direction = ApiEvent.Direction.LOCAL)
public record WorldSoundEvent(String soundId,
                              ink.tuanzi.neomacros.core.api.types.Pos3D pos,
                              float volume, float pitch) {
}
