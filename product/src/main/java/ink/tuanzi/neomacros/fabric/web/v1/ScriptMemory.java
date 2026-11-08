package ink.tuanzi.neomacros.fabric.web.v1;

/**
 * One script's memory signal at sample time: the cumulative bytes its
 * dedicated event-loop thread allocated since the script loaded (the JDK
 * per-thread allocation counter; every GraalJS operation of a script runs
 * on that thread). It is a growth/leak signal, not a live-heap number — GC
 * reclamation is not attributed per script.
 *
 * @param taskId    the owning task id; 0 marks the shared DevTools session
 * @param name      the script manifest name, or {@code devtools} for the
 *                  shared DevTools session
 * @param category  the script category dir name ({@code devtools} for the
 *                  shared session)
 * @param bytes     cumulative allocated bytes on the script's loop thread
 * @param sampledAt epoch millis of this sample (samples are taken per script,
 *                  so the value may lag the envelope timestamp slightly)
 */
public record ScriptMemory(long taskId, String name, String category, long bytes, long sampledAt) {
}
