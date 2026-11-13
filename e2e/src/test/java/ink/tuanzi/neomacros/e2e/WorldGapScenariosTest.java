package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.LightData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-010 E2E scenarios: the world queries for the respawn position, the sky
 * and block light levels, and sound-file playback reach the guest with the
 * designed shapes (handle, {@code {sky, block}}, promise), driven through the
 * real engine over the stub adapter.
 */
class WorldGapScenariosTest {

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

    @Test
    void respawnLightAndSoundFileReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "worldgap.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const respawn = neo.world.respawnPos;",
                    "  results['world.respawnPos'] = respawn !== undefined",
                    "      && respawn.x === 12 && respawn.y === 64 && respawn.z === -8;",
                    "  const light = neo.world.lightAt(0, 64, 0);",
                    "  results['world.lightAt'] = light !== undefined",
                    "      && light.sky === 15 && light.block === 7;",
                    "  neo.world.playSoundFile('sfx/e2e-gap.wav', 0.75).then(() => {",
                    "    results['world.playSoundFile'] = true;",
                    "    neo.__test.report('world-gap', Object.values(results).every(Boolean),",
                    "        JSON.stringify(results));",
                    "  }, (err) => {",
                    "    results['world.playSoundFile'] = false;",
                    "    neo.__test.report('world-gap', false,",
                    "        JSON.stringify(results) + ':' + String(err));",
                    "  });",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("worldgap.js"));

            var adapter = e2e.adapter();
            adapter.worldPort().setRespawnPos(new Pos3d(12, 64, -8));
            adapter.worldPort().setLight(new LightData(15, 7));
            adapter.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "world-gap");
            assertTrue(report != null && report.pass(),
                    "world-gap report: " + (report == null ? "missing" : report.detail()));
            assertEquals(java.util.List.of("sfx/e2e-gap.wav"),
                    adapter.worldPort().playedSoundFiles,
                    "the stub records the sound-file request");
            assertEquals(0.75, adapter.worldPort().lastSoundFileVolume);
        }
    }

    @Test
    void worldQueriesAreAbsentWithoutAWorld() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "worldgap-absent.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const respawn = neo.world.respawnPos;",
                    "  const light = neo.world.lightAt(0, 64, 0);",
                    "  neo.__test.report('world-gap-absent',",
                    "      respawn === undefined && light === undefined,",
                    "      JSON.stringify({ respawn: respawn === undefined,",
                    "        light: light === undefined }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("worldgap-absent.js"));

            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "world-gap-absent");
            assertTrue(report != null && report.pass(),
                    "world-gap-absent report: "
                            + (report == null ? "missing" : report.detail()));
        }
    }
}
