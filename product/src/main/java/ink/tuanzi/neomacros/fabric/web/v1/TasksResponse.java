package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.List;

/**
 * The task-manager snapshot: currently active tasks plus the bounded
 * in-memory history (oldest first). The panel merges SSE {@code task:state}
 * events over this baseline.
 *
 * @param active  the currently running tasks, ordered by task id
 * @param history the finished tasks, oldest first (bounded ring)
 */
public record TasksResponse(List<TaskEntry> active, List<TaskEntry> history) {
}
