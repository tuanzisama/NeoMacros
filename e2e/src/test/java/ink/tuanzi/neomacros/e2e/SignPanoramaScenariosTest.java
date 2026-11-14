package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-037 scenarios over the stub port: {@code writeSign}/{@code writeSignLine}
 * answer the editor-open state and fill the editor buffer (null lines stay),
 * and {@code takePanorama} resolves with the capture message while every
 * failure stays a catchable script path — an invalid folder and a capture
 * that cannot run reject the promise without taking the script down (the
 * acceptance's "打不开也不崩").
 */
class SignPanoramaScenariosTest {

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

    /** Polls the bridge until the scenario report lands or the deadline passes. */
    private static TestBridge.Report awaitReport(E2ERuntime e2e, String key) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            TestBridge.Report report = e2e.bridge().get(key);
            if (report != null) {
                return report;
            }
            Thread.sleep(20);
        }
        return e2e.bridge().get(key);
    }

    /** Asserts one reported key passed, with the script detail on failure. */
    private static void assertReport(E2ERuntime e2e, String key) throws Exception {
        TestBridge.Report report = awaitReport(e2e, key);
        assertTrue(report != null && report.pass(),
                key + " report: " + (report == null ? "missing" : report.detail()));
    }

    @Test
    void writeSignAnswersTheEditorStateAndFillsTheBuffer() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var player = e2e.adapter().playerPort();

            // Closed editor: both writes answer false and nothing lands.
            player.setSignEditorOpen(false);
            fixture(dir.resolve("scripts"), "g037-sign-closed.js", String.join("\n",
                    "const log = {};",
                    "log.fourLine = neo.player.writeSign('a', 'b', 'c', 'd') === false;",
                    "log.line = neo.player.writeSignLine(0, 'x') === false;",
                    // The index check runs even with no editor open.
                    "try { neo.player.writeSignLine(4, 'x'); log.indexRejected = false; }",
                    "  catch (e) { log.indexRejected = true; }",
                    // The single-line form does not accept null.
                    "try { neo.player.writeSignLine(0, null); log.nullRejected = false; }",
                    "  catch (e) { log.nullRejected = true; }",
                    "neo.__test.report('g037-sign-closed',",
                    "    log.fourLine && log.line && log.indexRejected && log.nullRejected,",
                    "    JSON.stringify(log));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g037-sign-closed.js"));
            e2e.awaitSettle();
            assertReport(e2e, "g037-sign-closed");
            assertArrayEquals(new String[] {null, null, null, null}, player.signLines(),
                    "a closed editor keeps its buffer");

            // Open editor: the writes land, null lines stay untouched.
            player.setSignEditorOpen(true);
            fixture(dir.resolve("scripts"), "g037-sign-open.js", String.join("\n",
                    "const log = {};",
                    "log.fourLine = neo.player.writeSign('top', null, 'third', null) === true;",
                    "log.line = neo.player.writeSignLine(3, 'bottom') === true;",
                    "neo.__test.report('g037-sign-open',",
                    "    log.fourLine && log.line, JSON.stringify(log));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g037-sign-open.js"));
            e2e.awaitSettle();
            assertReport(e2e, "g037-sign-open");

            assertArrayEquals(new String[] {"top", null, "third", "bottom"},
                    player.signLines(), "the editor buffer holds every non-null write");
            List<String> actions = player.actions();
            assertTrue(actions.contains("writeSign:top/-/third/-"),
                    "the four-line write reached the port, actions=" + actions);
            assertTrue(actions.contains("writeSign:-/-/-/bottom"),
                    "the indexed write reached the port, actions=" + actions);
        }
    }

    @Test
    void takePanoramaResolvesRejectsAndStaysCatchable() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var player = e2e.adapter().playerPort();

            // Happy path: the promise settles with the capture message.
            player.setPanoramaMessage("Saved panorama");
            fixture(dir.resolve("scripts"), "g037-panorama.js", String.join("\n",
                    "const log = {};",
                    "neo.player.takePanorama('shots').then(msg => {",
                    "  log.resolved = msg === 'Saved panorama';",
                    "  neo.__test.report('g037-panorama', log.resolved, JSON.stringify(log));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g037-panorama.js"));
            e2e.awaitSettle();
            assertReport(e2e, "g037-panorama");
            assertEquals(List.of("shots"), player.panoramaFolders(),
                    "the folder reaches the port unchanged");

            // A folder outside the macro root rejects before the port runs.
            fixture(dir.resolve("scripts"), "g037-panorama-badfolder.js", String.join("\n",
                    "const log = {};",
                    "neo.player.takePanorama('../escape').then(",
                    "  () => { log.settled = 'resolved';",
                    "          neo.__test.report('g037-panorama-badfolder', false,",
                    "              JSON.stringify(log)); },",
                    "  (err) => { log.settled = 'rejected';",
                    "             log.message = String(err && err.message || err);",
                    "             neo.__test.report('g037-panorama-badfolder',",
                    "                 log.message.indexOf('macro root') >= 0,",
                    "                 JSON.stringify(log)); });",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g037-panorama-badfolder.js"));
            e2e.awaitSettle();
            assertReport(e2e, "g037-panorama-badfolder");
            assertEquals(List.of("shots"), player.panoramaFolders(),
                    "an invalid folder never reaches the port");

            // A capture that cannot run rejects with its message and the
            // script keeps running afterwards.
            player.setPanoramaMessage(null);
            player.setPanoramaFailure(
                    new IllegalStateException("panorama capture requires an in-world player"));
            fixture(dir.resolve("scripts"), "g037-panorama-failure.js", String.join("\n",
                    "const log = {};",
                    "neo.player.takePanorama('shots').then(",
                    "  () => { log.settled = 'resolved';",
                    "          neo.__test.report('g037-panorama-failure', false,",
                    "              JSON.stringify(log)); },",
                    "  (err) => { log.settled = 'rejected';",
                    "             log.message = String(err && err.message || err);",
                    "             log.survived = neo.player.writeSignLine(0, 'still-alive')",
                    "                 === false;",
                    "             neo.__test.report('g037-panorama-failure',",
                    "                 log.message.indexOf('in-world') >= 0",
                    "                     && log.survived === true,",
                    "                 JSON.stringify(log)); });",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g037-panorama-failure.js"));
            e2e.awaitSettle();
            assertReport(e2e, "g037-panorama-failure");
            assertEquals("", e2e.scriptErrors(),
                    "a rejected capture never takes the script down");
        }
    }
}
