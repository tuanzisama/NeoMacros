package ink.tuanzi.neomacros.core.task;

import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry of macro tasks: one {@link ScriptTask} per macro activation.
 * Active tasks live in a concurrent map; finished ones move into a bounded
 * history (newest last, oldest evicted). State changes fan out to
 * {@code started/updated/ended} dispatchers (the web panel mirrors them over
 * SSE). Metadata (player, server, game type) is captured through the
 * {@link MetadataProvider} the product installs at bootstrap; without one the
 * fields stay empty.
 */
public final class TaskRegistry {

    /** Upper bound of the in-memory history; the oldest entries fall off. */
    public static final int HISTORY_CAPACITY = 500;

    /**
     * Session metadata captured at task start: player identity, game type,
     * server address, and the Minecraft version.
     */
    public record Metadata(String playerUuid, String playerName, String gameType,
                           String serverAddress, String mcVersion) {

        /** A metadata snapshot with every field unknown. */
        public static final Metadata EMPTY = new Metadata("", "", "", "", "");
    }

    /** Supplies the per-start metadata; implemented by the product layer. */
    public interface MetadataProvider {
        Metadata capture();
    }

    /** The task event kinds dispatched to listeners. */
    public enum TaskEvent {
        STARTED,
        UPDATED,
        ENDED
    }

    private final AtomicLong SEQUENCE = new AtomicLong();

    private final Map<Long, ScriptTask> active = new ConcurrentHashMap<>();
    private final ArrayDeque<ScriptTask> history = new ArrayDeque<>();
    private final CopyOnWriteArrayList<java.util.function.BiConsumer<TaskEvent, ScriptTask.Snapshot>> dispatchers =
            new CopyOnWriteArrayList<>();
    private volatile MetadataProvider metadataProvider;
    private final Object historyLock = new Object();

    /** Installs the metadata provider (bootstrap-time, product side). */
    public void metadataProvider(MetadataProvider provider) {
        this.metadataProvider = provider;
    }

    /** Registers a dispatcher receiving task state changes; returns an uninstall handle. */
    public Runnable onTaskEvent(java.util.function.BiConsumer<TaskEvent, ScriptTask.Snapshot> dispatcher) {
        dispatchers.add(dispatcher);
        return () -> dispatchers.remove(dispatcher);
    }

    /**
     * Starts a task for a macro activation. Returns the task; it is already
     * visible to dispatchers.
     */
    public ScriptTask start(String macro, ScriptCategory category, String startedBy) {
        Metadata metadata = captureMetadata();
        long id = SEQUENCE.incrementAndGet();
        long now = System.currentTimeMillis();
        ScriptTask task = new ScriptTask(id, macro, category, startedBy, now,
                metadata.playerUuid(), metadata.playerName(), metadata.gameType(),
                metadata.serverAddress(), metadata.mcVersion());
        active.put(id, task);
        dispatch(TaskEvent.STARTED, task);
        return task;
    }

    /** Counts one startup trigger delivered to the task; unknown ids are ignored. */
    public void countTrigger(long taskId) {
        ScriptTask task = active.get(taskId);
        if (task != null) {
            task.countTrigger();
            dispatch(TaskEvent.UPDATED, task);
        }
    }

    /**
     * Updates the last-known memory estimate of an active task (the web
     * memory sampler). Unknown ids are ignored; the update is deliberately
     * not dispatched so per-second sampling never floods the SSE stream —
     * the value rides the {@code memory:sample} event instead.
     */
    public void updateTaskMemory(long taskId, long bytes) {
        ScriptTask task = active.get(taskId);
        if (task != null) {
            task.updateMemory(bytes);
        }
    }

    /**
     * Marks the task stopped and moves it into the history ring. Unknown
     * task ids are ignored (double-stop safety).
     */
    public void stop(long taskId) {
        ScriptTask task = active.remove(taskId);
        if (task == null) {
            return;
        }
        task.markStopped(System.currentTimeMillis());
        synchronized (historyLock) {
            history.addLast(task);
            while (history.size() > HISTORY_CAPACITY) {
                history.pollFirst();
            }
        }
        dispatch(TaskEvent.ENDED, task);
    }

    /** The currently active tasks (by task id). */
    public List<ScriptTask> activeTasks() {
        List<ScriptTask> out = new ArrayList<>(active.values());
        out.sort(java.util.Comparator.comparingLong(ScriptTask::taskId));
        return List.copyOf(out);
    }

    /** The finished tasks, oldest first (bounded by {@link #HISTORY_CAPACITY}). */
    public List<ScriptTask> historyTasks() {
        synchronized (historyLock) {
            return List.copyOf(history);
        }
    }

    /** The task with the given id, active or in history. */
    public java.util.Optional<ScriptTask> task(long taskId) {
        ScriptTask live = active.get(taskId);
        if (live != null) {
            return java.util.Optional.of(live);
        }
        synchronized (historyLock) {
            for (ScriptTask task : history) {
                if (task.taskId() == taskId) {
                    return java.util.Optional.of(task);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /** Clears everything (runtime shutdown / game quit). */
    public void clear() {
        for (Long id : List.copyOf(active.keySet())) {
            stop(id);
        }
        synchronized (historyLock) {
            history.clear();
        }
    }

    private Metadata captureMetadata() {
        MetadataProvider provider = metadataProvider;
        if (provider == null) {
            return Metadata.EMPTY;
        }
        try {
            Metadata metadata = provider.capture();
            return metadata != null ? metadata : Metadata.EMPTY;
        } catch (RuntimeException e) {
            return Metadata.EMPTY;
        }
    }

    private void dispatch(TaskEvent event, ScriptTask task) {
        if (dispatchers.isEmpty()) {
            return;
        }
        ScriptTask.Snapshot snapshot = ScriptTask.Snapshot.of(task);
        for (java.util.function.BiConsumer<TaskEvent, ScriptTask.Snapshot> dispatcher : dispatchers) {
            try {
                dispatcher.accept(event, snapshot);
            } catch (RuntimeException ignored) {
                // A failing sink must not take the registry down.
            }
        }
    }
}
