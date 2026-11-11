package ink.tuanzi.neomacros.core.event;

/**
 * Synchronous-channel listener priority, mirroring the Bukkit ordering:
 * handlers run LOWEST first, HIGHEST last — later priorities observe and
 * override earlier modifications, and the final cancellation decision is
 * the last write. MONITOR is observation-only: the engine surface drops its
 * writes and cancellation changes.
 *
 * @zh 同步通道订阅优先级，对齐 Bukkit 排序：LOWEST 先执行、HIGHEST 后执行——后执行者能看到并覆盖先前的修改，取消与否同样由最后一次写入决定。MONITOR 仅作观测：引擎面丢弃其修改与取消。
 */
public enum EventPriority {

    /** Runs first; the weakest claim on the final result.
     * @zh 最先执行；对最终结果的话语权最弱。 */
    LOWEST(0),

    /** Runs early.
     * @zh 较早执行。 */
    LOW(1),

    /** The default priority.
     * @zh 默认优先级。 */
    NORMAL(2),

    /** Runs late; sees earlier modifications.
     * @zh 较晚执行；能看到先前优先级的修改。 */
    HIGH(3),

    /** Runs last before monitoring; the final say on the result.
     * @zh 观测级之前最后执行；对结果有最终决定权。 */
    HIGHEST(4),

    /** Observation only: modifications and cancellation changes are dropped.
     * @zh 仅观测：修改与取消变更会被丢弃。 */
    MONITOR(5);

    private final int slot;

    EventPriority(int slot) {
        this.slot = slot;
    }

    /** The stable ordering slot (LOWEST = 0 … MONITOR = 5).
     * @zh 稳定排序槽位（LOWEST = 0 … MONITOR = 5）。 */
    public int slot() {
        return slot;
    }
}
