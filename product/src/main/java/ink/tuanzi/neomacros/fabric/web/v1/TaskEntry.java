package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.core.task.ScriptTask;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

/**
 * One macro task as the panel sees it: the activation identity, live or
 * final status, and the session metadata captured at start. {@code endedAt}
 * is 0 while the task is running; the nullable fields are empty when the
 * session context was unknown at start.
 *
 * @param id          the unique task id (sequential per game session)
 * @param macro       the macro manifest name
 * @param category    the macro category (keypress/event/services)
 * @param status      running or stopped
 * @param startedBy   how the activation happened (autostart/lifecycle/manual/reload)
 * @param startedAt   epoch millis of the activation
 * @param endedAt     epoch millis of the stop, 0 while running
 * @param triggers    how many startup invocations the macro received
 * @param playerUuid  the player UUID at start, empty when unknown
 * @param playerName  the player name at start, empty when unknown
 * @param gameType    single or multi, empty when unknown
 * @param server      Integrated or the server address, empty when unknown
 * @param mcVersion   the Minecraft version, empty when unknown
 */
public record TaskEntry(long id, String macro, String category, String status,
                        String startedBy, long startedAt, long endedAt, long triggers,
                        @NmNullable String playerUuid, @NmNullable String playerName,
                        @NmNullable String gameType, @NmNullable String server,
                        @NmNullable String mcVersion) {

    /** Captures one task snapshot for the wire. */
    public static TaskEntry of(ScriptTask.Snapshot snapshot) {
        return new TaskEntry(
                snapshot.taskId(),
                snapshot.macro(),
                snapshot.category().dirName(),
                snapshot.status().name().toLowerCase(java.util.Locale.ROOT),
                snapshot.startedBy(),
                snapshot.startedAtMs(),
                snapshot.endedAtMs(),
                snapshot.triggerCount(),
                snapshot.playerUuid(),
                snapshot.playerName(),
                snapshot.gameType(),
                snapshot.serverAddress(),
                snapshot.mcVersion());
    }
}
