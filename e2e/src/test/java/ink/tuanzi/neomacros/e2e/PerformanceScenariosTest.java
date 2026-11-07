package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E-A scenarios for the performance module: real scripts through the real
 * engine exercising the {@code neo.performance} global across the User
 * Timing surface — marks, measures in every accepted form, entry queries,
 * clears, and the session origin.
 */
class PerformanceScenariosTest {

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
    void userTimingSurfaceThroughTheGlobalChannel() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "perf-user-timing.js", String.join("\n",
                    "const results = {};",
                    "const t0 = neo.performance.now();",
                    "results['now'] = typeof t0 === 'number' && t0 >= 0;",
                    "neo.performance.mark('a');",
                    "const m = neo.performance.measure('span', 'a');",
                    "results['measureFromMark'] = m.name === 'span' && m.entryType === 'measure'",
                    "    && m.startTime >= 0 && m.duration >= 0;",
                    "const raw = neo.performance.measure('raw', 1.5, 3.5);",
                    "results['rawBounds'] = raw.startTime === 1.5 && raw.duration === 2;",
                    "results['byName'] = neo.performance.entriesByName('span').length === 1",
                    "    && neo.performance.entriesByName('span', 'measure').length === 1",
                    "    && neo.performance.entriesByName('span', 'mark').length === 0;",
                    "results['byType'] = neo.performance.entriesByType('mark').length === 1",
                    "    && neo.performance.entriesByType('measure').length === 2",
                    "    && neo.performance.entriesByType('paint').length === 0;",
                    "const json = JSON.stringify(neo.performance.entriesByName('a')[0]);",
                    "results['jsonShape'] = json.includes('\"name\":\"a\"')",
                    "    && json.includes('\"entryType\":\"mark\"')",
                    "    && json.includes('\"startTime\"') && json.includes('\"duration\"');",
                    "neo.performance.clearMarks();",
                    "results['clearMarks'] = neo.performance.entriesByType('mark').length === 0;",
                    "neo.performance.clearMeasures();",
                    "results['clearMeasures'] = neo.performance.entries.length === 0;",
                    "neo.__test.report('perf-user-timing', Object.values(results).every(Boolean),",
                    "    JSON.stringify(results));",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("perf-user-timing.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("perf-user-timing");
            assertNotNull(report, "performance report missing");
            assertTrue(report.pass(), "performance scenario failed: " + report.detail());
        }
    }

    @Test
    void globalFaceAnchorsTimeOriginToSessionStart() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "perf-global.js", String.join("\n",
                    "const results = {};",
                    "const perf = neo.performance;",
                    "results['globalFace'] = typeof perf.now === 'function'",
                    "    && typeof perf.timeOrigin === 'number';",
                    "const t1 = perf.now(), t2 = perf.now();",
                    "results['nowMonotonic'] = t1 >= 0 && t2 >= t1;",
                    "results['timeOriginSane'] = perf.timeOrigin > 0",
                    "    && perf.timeOrigin <= Date.now() + 50;",
                    "results['comparableToDateNow'] = Date.now() - perf.timeOrigin < 60_000;",
                    "results['isolatedTimeline'] = perf.entries.length === 0;",
                    "neo.__test.report('perf-global', Object.values(results).every(Boolean),",
                    "    JSON.stringify(results));",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("perf-global.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("perf-global");
            assertNotNull(report, "performance report missing");
            assertTrue(report.pass(), "performance global scenario failed: " + report.detail());
            assertEquals(true, report.pass());
        }
    }
}
