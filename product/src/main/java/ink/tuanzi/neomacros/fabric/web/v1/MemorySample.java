package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.List;

/**
 * One memory sample as the panel sees it: the JVM heap block plus the
 * per-script allocation signals. Scripts whose loop thread had no reading
 * (unsupported JVM, dead thread) are omitted. The panel merges SSE
 * {@code memory:sample} events over the replay baseline
 * ({@code GET /api/v1/memory?after=}).
 *
 * @param seq              monotonically increasing sample sequence
 * @param ts               epoch millis of the sample
 * @param jvm              the JVM heap snapshot
 * @param graaljsAllocated the sum of the script {@link ScriptMemory#bytes()}
 *                         values (the script threads' cumulative allocation)
 * @param scripts          per-script signals, ordered by task id
 */
public record MemorySample(long seq, long ts, JvmMemory jvm, long graaljsAllocated,
                           List<ScriptMemory> scripts) {
}
