package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the experience change event; fires when the level or the total
 * experience points move. The {@code prev} fields hold the values of the
 * previous sample, so a level-up reads as a {@code prevLevel} gap and a
 * plain gain as a {@code prevTotal} gap.
 *
 * @zh 经验值变化事件载荷；等级或总经验值变化时触发。{@code prev*} 字段为上一次采样值：升级体现为 {@code prevLevel} 差，普通增长体现为 {@code prevTotal} 差。
 * @zh-param level 当前经验等级
 * @zh-param progress 当前等级进度（0.0-1.0）
 * @zh-param change 等级增量
 * @zh-param total 当前总经验值
 * @zh-param prevProgress 上一次采样的等级进度
 * @zh-param prevTotal 上一次采样的总经验值
 * @zh-param prevLevel 上一次采样的经验等级
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:exp", direction = ApiEvent.Direction.LOCAL)
public record PlayerExpEvent(int level, float progress, int change,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") int total,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") float prevProgress,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") int prevTotal,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") int prevLevel) {
}
