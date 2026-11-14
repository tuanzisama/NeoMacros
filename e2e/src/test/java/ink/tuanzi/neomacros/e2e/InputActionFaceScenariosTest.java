package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-028B scenarios: the PlayerInput/KeyTracker members map onto the existing
 * input faces — press/release drive {@code pressedKeys} (the KeyTracker
 * pressed-name ledger), {@code set}/{@code movement} drive
 * {@code movementState} (the movementForward/Sideways/jumping/sneaking/
 * sprinting read face), and the subscription releases every hold.
 */
class InputActionFaceScenariosTest {

    @TempDir
    Path dir;

    /** Writes one script folder (manifest + index.js) below the event category. */
    private static void fixture(Path scriptsDir, String name, String source) throws Exception {
        String stem = name.endsWith(".js") ? name.substring(0, name.length() - 3) : name;
        Path folder = scriptsDir.resolve("event").resolve(stem);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("neomacro.yml"), String.join("\n",
                "name: " + stem,
                "version: 1.0.0",
                "author: e2e",
                "config:",
                "  event:",
                "    on: [game:start]",
                ""));
        Files.writeString(folder.resolve("index.js"), source + "\nexport function startup() {}\n");
    }

    @Test
    void pressReleaseAndMovementCoverThePlayerInputFace() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "input-actions.js", String.join("\n",
                    "function contains(list, name) {",
                    "  for (let i = 0; i < list.length; i++) {",
                    "    if (list[i] === name) { return true; }",
                    "  }",
                    "  return false;",
                    "}",
                    "const log = {};",
                    // KeyTracker.press/unpress(Key) → input.press/input.release:
                    // the raw key lands in (and leaves) the pressed-name set.
                    "neo.input.press('key.keyboard.w');",
                    "log.press = contains(neo.input.pressedKeys, 'key.forward');",
                    "neo.input.release('key.keyboard.w');",
                    "log.release = !contains(neo.input.pressedKeys, 'key.forward');",
                    // KeyTracker.unpress(KeyMapping) → input.set(bind, false).
                    "neo.input.set('key.jump', true);",
                    "log.setDown = neo.input.movementState.jump === true;",
                    "neo.input.set('key.jump', false);",
                    "log.setUp = neo.input.movementState.jump === false;",
                    // movementForward/Sideways/jumping/sneaking/sprinting read
                    // back through movementState while the hold is active.
                    "const sub = neo.input.movement({ forward: true, sneak: true, sprint: true });",
                    "const ms = neo.input.movementState;",
                    "log.held = ms.forward === true && ms.sneak === true && ms.sprint === true",
                    "    && ms.backward === false && ms.left === false && ms.right === false;",
                    "log.shape = typeof ms.forward === 'boolean'",
                    "    && typeof ms.backward === 'boolean'",
                    "    && typeof ms.left === 'boolean'",
                    "    && typeof ms.right === 'boolean'",
                    "    && typeof ms.jump === 'boolean'",
                    "    && typeof ms.sneak === 'boolean'",
                    "    && typeof ms.sprint === 'boolean';",
                    "sub.cancel();",
                    // movementState is a fresh snapshot per read.
                    "const after = neo.input.movementState;",
                    "log.cancelled = after.forward === false && after.sneak === false",
                    "    && after.sprint === false;",
                    "neo.__test.report('g028b-input-actions',",
                    "    log.press && log.release && log.setDown && log.setUp",
                    "        && log.held && log.shape && log.cancelled,",
                    "    JSON.stringify(log));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("input-actions.js"));
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g028b-input-actions");
            assertTrue(report != null && report.pass(),
                    "input actions report: " + (report == null ? "missing" : report.detail()));

            // The port sees the exact injection sequence the faces imply.
            assertEquals(List.of(
                    "key:key.keyboard.w=true",
                    "key:key.keyboard.w=false",
                    "bind:key.jump=true",
                    "bind:key.jump=false",
                    "bind:key.forward=true",
                    "bind:key.sneak=true",
                    "bind:key.sprint=true",
                    "bind:key.forward=false",
                    "bind:key.sneak=false",
                    "bind:key.sprint=false"), e2e.adapter().inputPort().injections(),
                    "injection log: " + e2e.adapter().inputPort().injections());
        }
    }
}
