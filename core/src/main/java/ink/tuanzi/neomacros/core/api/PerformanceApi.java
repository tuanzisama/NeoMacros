package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The {@code neo.performance} module: Web-Performance-style macro profiling —
 * a high-resolution monotonic clock anchored at the script's session origin,
 * plus User Timing marks and measures kept in a bounded per-script buffer.
 * Wall-clock timestamps remain the domain of the ES-native
 * {@code Date.now()}; this module measures durations. Each script session
 * owns an isolated timeline, so {@code timeOrigin} and buffered entries
 * reset when the script reloads.
 *
 * @zh {@code neo.performance}：Web Performance 风格的宏性能分析——锚定脚本会话起点的高精度单调时钟，外加保存在每脚本有界缓冲中的 User Timing 标记与测量。墙钟时间仍用 ES 原生 {@code Date.now()}；本模块负责耗时测量。每脚本会话持有独立时间线，脚本重载后 timeOrigin 与缓冲随之重置。
 */
@ApiModule(name = "performance", category = "核心")
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
public final class PerformanceApi {

    private static final ConcurrentMap<String, PerformanceTimeline> TIMELINES = new ConcurrentHashMap<>();

    /**
     * A recorded mark or measure: {@code entryType} is {@code "mark"} or
     * {@code "measure"}, {@code startTime} and {@code duration} are
     * milliseconds on the script's timeline.
     *
     * @zh 一条已记录的 mark 或 measure：entryType 为 "mark" 或 "measure"，startTime 与 duration 为脚本时间线上的毫秒值。
     */
    public record PerformanceEntry(String name, String entryType, double startTime, double duration) {
    }

    /**
     * Wall-clock time, in milliseconds, at which this script's session
     * started; {@code performance.timeOrigin + performance.now()} stays
     * comparable to {@code Date.now()}.
     *
     * @zh 本脚本会话启动的墙钟毫秒时刻；performance.timeOrigin + performance.now() 与 Date.now() 可比。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public double getTimeOrigin() {
        return timeline().originMillis();
    }

    /**
     * High-resolution monotonic timestamp in milliseconds (microsecond
     * precision) since this script's session origin; unlike
     * {@code Date.now()} it never steps backward with wall-clock changes.
     *
     * @zh 距本脚本会话起点的高精度单调毫秒时间戳（微秒精度）；与 Date.now() 不同，不随墙钟调整回退。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public double now() {
        return timeline().now();
    }

    /**
     * Records a zero-duration timestamp under {@code name} and returns the
     * stored entry; the name must be non-empty.
     *
     * @zh 以 name 记录一个零时长时间戳并返回条目；name 必须非空。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public PerformanceEntry mark(String name) {
        return timeline().mark(name);
    }

    /**
     * Records a duration under {@code name} spanning {@code start} to
     * {@code end} and returns the stored entry. Each bound accepts a mark
     * name (that mark's timestamp) or a raw {@code now()} timestamp;
     * omitted bounds default to the session origin and the current time.
     *
     * @zh 以 name 记录一段从 start 到 end 的耗时并返回条目。两端各自接受 mark 名（取其时间戳）或 now() 原始时间戳；缺省时起点为会话原点、终点为当前时刻。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public PerformanceEntry measure(String name,
            @ApiOptional @ApiType("string | number") Object start,
            @ApiOptional @ApiType("string | number") Object end) {
        return timeline().measure(name, start, end);
    }

    /**
     * Removes recorded marks — all of them when {@code name} is omitted,
     * otherwise only those under that name.
     *
     * @zh 移除已记录的 mark；缺省 name 时全部移除，否则仅移除同名条目。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void clearMarks(@ApiOptional String name) {
        timeline().clearMarks(name);
    }

    /**
     * Removes recorded measures — all of them when {@code name} is omitted,
     * otherwise only those under that name.
     *
     * @zh 移除已记录的 measure；缺省 name 时全部移除，否则仅移除同名条目。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void clearMeasures(@ApiOptional String name) {
        timeline().clearMeasures(name);
    }

    /**
     * All kept entries in insertion order; the buffer keeps the newest 4096
     * and evicts older ones. (The MDN {@code getEntries()} query lands as a
     * read-only property under the house no-argument-property rule.)
     *
     * @zh 按录入顺序返回全部保留条目；缓冲保留最新 4096 条，更早的已被挤出。（MDN 的 getEntries() 按无参状态属性化铁律落地为只读属性。）
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<PerformanceEntry> getEntries() {
        return timeline().entries();
    }

    /**
     * Kept entries under {@code name}, optionally restricted to one
     * {@code entryType} ({@code "mark"} or {@code "measure"}).
     *
     * @zh 返回名为 name 的保留条目；entryType 可选（"mark" 或 "measure"），用于再过滤。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<PerformanceEntry> entriesByName(String name,
            @ApiOptional String entryType) {
        return timeline().entriesByName(name, entryType);
    }

    /**
     * Kept entries of one {@code entryType} ({@code "mark"} or
     * {@code "measure"}); unknown types return an empty array.
     *
     * @zh 返回指定 entryType（"mark" 或 "measure"）的保留条目；未知类型返回空数组。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<PerformanceEntry> entriesByType(String entryType) {
        return timeline().entriesByType(entryType);
    }

    /**
     * Seeds the timeline for a script so its origin anchors at load time
     * even before the first {@code performance} call; the bootstrap calls
     * this when a script loads.
     *
     * @zh 预置脚本的时间线，使起点锚定在加载时刻（早于首次调用）；bootstrap 在脚本加载时调用。
     */
    @ApiInternal
    public static void touch(String scriptName) {
        TIMELINES.computeIfAbsent(scriptName, key -> new PerformanceTimeline());
    }

    /**
     * Drops the script's timeline (bootstrap unload hook); a later reload
     * starts a fresh session.
     *
     * @zh 丢弃脚本的时间线（bootstrap 卸载钩子）；此后重载将开启全新会话。
     */
    @ApiInternal
    public static void drop(String scriptName) {
        TIMELINES.remove(scriptName);
    }

    private PerformanceTimeline timeline() {
        LoadedModule owner = LoadTracker.current();
        if (owner == null) {
            throw new IllegalStateException(
                    "performance requires a script context (timelines are isolated per script)");
        }
        return TIMELINES.computeIfAbsent(owner.meta().name(), key -> new PerformanceTimeline());
    }
}
