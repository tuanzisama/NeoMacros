package ink.tuanzi.neomacros.core.task;

import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Task lifecycle: ids, trigger counting, history bounds, dispatchers. */
class TaskRegistryTest {

    @Test
    void tasksGetSequentialIdsAndSnapshots() {
        TaskRegistry registry = new TaskRegistry();
        ScriptTask first = registry.start("alpha", ScriptCategory.KEYPRESS, "lifecycle");
        ScriptTask second = registry.start("beta", ScriptCategory.EVENT, "manual");

        assertEquals(1, first.taskId());
        assertEquals(2, second.taskId());
        assertEquals(ScriptTask.Status.RUNNING, first.status());
        assertEquals("alpha", first.macro());
        assertEquals(ScriptCategory.KEYPRESS, first.category());
        assertEquals("lifecycle", first.startedBy());
        assertEquals(0, first.endedAtMs());
        assertTrue(first.startedAtMs() > 0);

        assertEquals(2, registry.activeTasks().size());
        assertEquals(Optional.of(first), registry.task(1));
    }

    @Test
    void stopMovesTheTaskIntoHistory() {
        TaskRegistry registry = new TaskRegistry();
        ScriptTask task = registry.start("svc", ScriptCategory.SERVICES, "lifecycle");
        registry.countTrigger(task.taskId());
        registry.countTrigger(task.taskId());
        registry.stop(task.taskId());

        assertEquals(0, registry.activeTasks().size());
        assertEquals(1, registry.historyTasks().size());
        ScriptTask stopped = registry.historyTasks().get(0);
        assertEquals(ScriptTask.Status.STOPPED, stopped.status());
        assertEquals(2, stopped.triggerCount());
        assertTrue(stopped.endedAtMs() >= stopped.startedAtMs());
        // A stop after history eviction of the id is a safe no-op.
        registry.stop(task.taskId());
        assertEquals(1, registry.historyTasks().size());
    }

    @Test
    void historyIsBoundedOldestFirst() {
        TaskRegistry registry = new TaskRegistry();
        for (int i = 0; i < TaskRegistry.HISTORY_CAPACITY + 20; i++) {
            registry.stop(registry.start("m" + i, ScriptCategory.EVENT, "autostart").taskId());
        }
        List<ScriptTask> history = registry.historyTasks();
        assertEquals(TaskRegistry.HISTORY_CAPACITY, history.size());
        assertEquals("m20", history.get(0).macro(), "the oldest entries fall off");
        assertEquals("m" + (TaskRegistry.HISTORY_CAPACITY + 19),
                history.get(history.size() - 1).macro());
    }

    @Test
    void dispatchersSeeStartedUpdatedEnded() {
        TaskRegistry registry = new TaskRegistry();
        List<String> events = new ArrayList<>();
        registry.onTaskEvent((event, snapshot) -> events.add(event + ":" + snapshot.macro()));

        ScriptTask task = registry.start("demo", ScriptCategory.KEYPRESS, "manual");
        registry.countTrigger(task.taskId());
        registry.stop(task.taskId());

        assertEquals(List.of("STARTED:demo", "UPDATED:demo", "ENDED:demo"), events);

        // Uninstalling the dispatcher silences further events.
        Runnable uninstall = registry.onTaskEvent((event, snapshot) -> events.add("leak"));
        uninstall.run();
        registry.clear();
        assertEquals(List.of("STARTED:demo", "UPDATED:demo", "ENDED:demo"), events);
    }

    @Test
    void metadataProviderFeedsTaskFieldsAndToleratesFailures() {
        TaskRegistry registry = new TaskRegistry();
        registry.metadataProvider(() -> new TaskRegistry.Metadata(
                "uuid-1", "tester", "multi", "mc.example.com:25565", "26.3"));
        ScriptTask task = registry.start("meta", ScriptCategory.EVENT, "manual");
        assertEquals("uuid-1", task.playerUuid());
        assertEquals("tester", task.playerName());
        assertEquals("multi", task.gameType());
        assertEquals("mc.example.com:25565", task.serverAddress());
        assertEquals("26.3", task.mcVersion());

        registry.metadataProvider(() -> { throw new IllegalStateException("boom"); });
        ScriptTask fallback = registry.start("meta2", ScriptCategory.EVENT, "manual");
        assertEquals("", fallback.playerUuid());
        assertEquals("", fallback.serverAddress());
    }

    @Test
    void memoryUpdatesStayOutOfDispatch() {
        TaskRegistry registry = new TaskRegistry();
        List<String> events = new ArrayList<>();
        registry.onTaskEvent((event, snapshot) -> events.add(event.name()));
        ScriptTask task = registry.start("mem", ScriptCategory.EVENT, "manual");

        assertEquals(-1, task.memBytes());
        assertNull(ScriptTask.Snapshot.of(task).memBytes());

        registry.updateTaskMemory(task.taskId(), 4096);
        registry.updateTaskMemory(task.taskId(), 1024);
        assertEquals(1024, task.memBytes());
        // Negative estimates mean "unknown" and must not clobber the last value.
        registry.updateTaskMemory(task.taskId(), -1);
        assertEquals(1024, task.memBytes());

        ScriptTask.Snapshot snapshot = ScriptTask.Snapshot.of(task);
        assertEquals(1024L, snapshot.memBytes());

        // Sampling never floods the task event stream (the value rides
        // the memory:sample SSE event instead).
        assertEquals(List.of("STARTED"), events);

        registry.stop(task.taskId());
        ScriptTask stopped = registry.task(task.taskId()).orElseThrow();
        assertEquals(1024, stopped.memBytes());
    }

    @Test
    void clearStopsEverything() {
        TaskRegistry registry = new TaskRegistry();
        registry.start("a", ScriptCategory.EVENT, "manual");
        registry.start("b", ScriptCategory.SERVICES, "lifecycle");
        // Session shutdown: active tasks end and the in-memory history resets.
        registry.clear();
        assertEquals(0, registry.activeTasks().size());
        assertEquals(0, registry.historyTasks().size());
    }
}
