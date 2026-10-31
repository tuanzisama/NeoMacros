package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the sound event; fires when the client plays a sound.
 * High-traffic, so wildcard dispatch skips it by default.
 *
 * @zh 声音播放事件；高流量事件，默认不进通配分发。
 * @zh-param soundId 声音标识（如 minecraft:block.note_block.pling）
 * @zh-param x 声音源 X 坐标
 * @zh-param y 声音源 Y 坐标
 * @zh-param z 声音源 Z 坐标
 * @zh-param volume 音量
 * @zh-param pitch 音调
 */
@ApiSince("0.1.0")
@ApiEvent(name = "world:sound")
public record WorldSoundEvent(String soundId,
                              double x, double y, double z,
                              float volume, float pitch) {
}
