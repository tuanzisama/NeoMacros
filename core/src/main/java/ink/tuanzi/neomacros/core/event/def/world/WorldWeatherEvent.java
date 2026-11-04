package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the weather change event.
 *
 * @zh 天气变化事件载荷。
 * @zh-param raining 是否在下雨
 * @zh-param thundering 是否在打雷
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "world:weather")
public record WorldWeatherEvent(boolean raining, boolean thundering) {
}
