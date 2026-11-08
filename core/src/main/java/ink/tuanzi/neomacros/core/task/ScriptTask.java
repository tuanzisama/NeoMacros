package ink.tuanzi.neomacros.core.task;

import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;

/**
 * One macro activation tracked by the task manager: a loaded module from its
 * startup invocation to its unload. Each activation receives a unique task
 * id; finished tasks are kept in the registry's bounded history.
 */
public final class ScriptTask {

    /** Task lifecycle states. */
    public enum Status {
        RUNNING,
        STOPPED
    }

    private final long taskId;
    private final String macro;
    private final ScriptCategory category;
    private final String startedBy;
    private final long startedAtMs;
    private final String playerUuid;
    private final String playerName;
    private final String gameType;
    private final String serverAddress;
    private final String mcVersion;
    private volatile Status status = Status.RUNNING;
    private volatile long endedAtMs;
    private final java.util.concurrent.atomic.AtomicLong triggerCount = new java.util.concurrent.atomic.AtomicLong();
    private volatile long memBytes = -1;

    ScriptTask(long taskId, String macro, ScriptCategory category, String startedBy,
               long startedAtMs, String playerUuid, String playerName, String gameType,
               String serverAddress, String mcVersion) {
        this.taskId = taskId;
        this.macro = macro;
        this.category = category;
        this.startedBy = startedBy;
        this.startedAtMs = startedAtMs;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.gameType = gameType;
        this.serverAddress = serverAddress;
        this.mcVersion = mcVersion;
    }

    /** The unique task id (sequential within this game session). */
    public long taskId() {
        return taskId;
    }

    /** The macro manifest name. */
    public String macro() {
        return macro;
    }

    /** The macro category (keypress/event/services). */
    public ScriptCategory category() {
        return category;
    }

    /** How the task was started (autostart / lifecycle / manual / web). */
    public String startedBy() {
        return startedBy;
    }

    /** Epoch millis of the activation. */
    public long startedAtMs() {
        return startedAtMs;
    }

    /** Epoch millis of the stop; 0 while running. */
    public long endedAtMs() {
        return endedAtMs;
    }

    /** The current status. */
    public Status status() {
        return status;
    }

    /** How many startup triggers were delivered to this macro. */
    public long triggerCount() {
        return triggerCount.get();
    }

    /** The player UUID captured at task start; may be empty. */
    public String playerUuid() {
        return playerUuid;
    }

    /** The player name captured at task start; may be empty. */
    public String playerName() {
        return playerName;
    }

    /** "single" or "multi"; may be empty when unknown. */
    public String gameType() {
        return gameType;
    }

    /** "Integrated" or the server address; may be empty. */
    public String serverAddress() {
        return serverAddress;
    }

    /** The Minecraft version captured at task start; may be empty. */
    public String mcVersion() {
        return mcVersion;
    }

    /** Counts one delivered startup trigger. */
    void countTrigger() {
        triggerCount.incrementAndGet();
    }

    /** Marks the task stopped at the given time (registry-internal). */
    void markStopped(long atMs) {
        this.status = Status.STOPPED;
        this.endedAtMs = atMs;
    }

    /** The last-known memory estimate in bytes; -1 when never sampled. */
    public long memBytes() {
        return memBytes;
    }

    /** Updates the last-known memory estimate (memory sampler, registry-internal). */
    void updateMemory(long bytes) {
        if (bytes < 0) {
            return;
        }
        this.memBytes = bytes;
    }

    /** An immutable point-in-time snapshot handed to dispatchers and DTOs. */
    public record Snapshot(long taskId, String macro, ScriptCategory category, Status status,
                           String startedBy, long startedAtMs, long endedAtMs, long triggerCount,
                           String playerUuid, String playerName, String gameType,
                           String serverAddress, String mcVersion,
                           Long memBytes) {

        /** Captures the current state of the task. */
        public static Snapshot of(ScriptTask task) {
            return new Snapshot(task.taskId, task.macro, task.category, task.status,
                    task.startedBy, task.startedAtMs, task.endedAtMs, task.triggerCount.get(),
                    task.playerUuid, task.playerName, task.gameType, task.serverAddress,
                    task.mcVersion,
                    task.memBytes < 0 ? null : task.memBytes);
        }
    }
}
