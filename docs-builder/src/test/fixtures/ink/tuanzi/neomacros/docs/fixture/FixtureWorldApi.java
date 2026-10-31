package ink.tuanzi.neomacros.docs.fixture;

import ink.tuanzi.neomacros.doc.api.ApiExperimental;
import ink.tuanzi.neomacros.doc.api.ApiHandle;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Fixture module exercising the full annotation set with complete bilingual
 * documentation: class and member annotations, a handle type, a referenced
 * record type, and an overridden parameter type.
 *
 * @zh 练习完整注解集与完整双语文档的 fixture 模块：类级与方法级注解、句柄类型、签名引用的 record 类型与覆写的参数类型。
 */
@ApiModule(name = "world", category = "test")
@ApiSince("0.1.0")
@ApiMC(">=1.20.6")
public final class FixtureWorldApi {

    /**
     * Returns the world time of day in ticks.
     *
     * @return ticks since dawn
     * @zh 返回当前世界时间（tick）。
     * @zh-return 自黎明起的 tick 数
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public long getTime() {
        return 0L;
    }

    /**
     * Marks a block highlight at the given position.
     *
     * @param pos the block position to mark
     * @return a promise completing once the highlight is placed
     * @zh 在指定位置放置方块高亮。
     * @zh-param pos 要标记的方块位置
     * @zh-return 高亮放置完成后兑现的 Promise
     * @zh-example world.mark(new Pos3d(1, 64, 1));
     */
    @ApiSince("0.1.0")
    @ApiMC(">=1.20.6")
    public CompletableFuture<Void> mark(FixturePos pos) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Watches chat messages through the given listener.
     *
     * @param listener callback invoked for each message
     * @zh 通过监听器观察聊天消息。
     * @zh-param listener 每条消息触发的回调
     */
    @ApiSince("0.1.0")
    @ApiPermission("command")
    @ApiExperimental
    public void watch(@ApiType("(message: string) => void") Consumer<String> listener) {
    }

    /** Internal helper hidden from every generated artifact. */
    @ApiInternal
    public void hiddenInternal() {
    }

    /**
     * Live handle for the fixture world.
     *
     * @zh fixture 世界的活引用句柄。
     */
    @ApiHandle("WorldHandle")
    public static final class WorldHandle {

        /**
         * Returns the handle display name.
         *
         * @return the display name
         * @zh 返回句柄显示名称。
         * @zh-return 显示名称
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public String getName() {
            return "world";
        }

        /**
         * Refreshes the handle against the live world.
         *
         * @zh 让句柄对实时世界状态重新求值。
         */
        @ApiSince("0.1.0")
        public void refresh() {
        }
    }
}
