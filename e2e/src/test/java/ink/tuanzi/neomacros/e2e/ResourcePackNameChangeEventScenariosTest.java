package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-016 E2E scenarios: the resource-pack reload event carries the boot flag
 * and the selected pack list, and the entity custom-name event carries the
 * uuid plus the old/new name edges (set, rename, clear), driven through the
 * real engine over the stub adapter.
 */
class ResourcePackNameChangeEventScenariosTest {

    private static final UUID ZOMBIE = UUID.randomUUID();

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
                key + " report: " + (report == null ? "missing" : report.detail())
                        + " | script errors: " + e2e.scriptErrors());
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(UUID.randomUUID(), "tester", new Pos3d(0, 64, 0),
                new Rot2d(0, 0), 20.0, 20, 300);
    }

    @Test
    void resourcePackAndCustomNamePayloadsCarryTheTriagedMembers() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "resource-pack-name-change.js", String.join("\n",
                    "neo.events.on('game:resourcePack', (ev) => {",
                    "  neo.__test.report('resource-pack',",
                    "      ev.isGameStart === true && Array.isArray(ev.loadedPacks)",
                    "          && ev.loadedPacks.length === 2",
                    "          && ev.loadedPacks[0] === 'vanilla'",
                    "          && ev.loadedPacks[1] === 'file/g016',",
                    "      JSON.stringify({ isGameStart: ev.isGameStart,",
                    "        packs: ev.loadedPacks }));",
                    "});",
                    "neo.events.on('entity:customName', (ev) => {",
                    "  if (ev.oldName === undefined && ev.newName === 'Bob') {",
                    "    neo.__test.report('name-set',",
                    "        String(ev.uuid) === '" + ZOMBIE + "',",
                    "        JSON.stringify({ uuid: ev.uuid }));",
                    "  } else if (ev.oldName === 'Bob' && ev.newName === 'Robert') {",
                    "    neo.__test.report('name-rename',",
                    "        String(ev.uuid) === '" + ZOMBIE + "',",
                    "        JSON.stringify({ uuid: ev.uuid, oldName: ev.oldName,",
                    "          newName: ev.newName }));",
                    "  } else if (ev.oldName === 'Robert' && ev.newName === undefined) {",
                    "    neo.__test.report('name-clear', true, JSON.stringify({}));",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("resource-pack-name-change.js"));

            var adapter = e2e.adapter();

            // Baseline: player in world, one tracked entity without a custom
            // name; the first sample never publishes a name change.
            adapter.playerPort().setSelf(player());
            adapter.entityPort().setEntities(List.of(new EntitySnapshot(ZOMBIE,
                    "minecraft:zombie", "Zombie", new Pos3d(1, 64, 1), 20.0)));
            adapter.entityPort().setEntityState(Map.of());
            adapter.lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            // The resource-pack reload completes through the lifecycle sink.
            adapter.lifecyclePort().fireResourcePackLoaded(true,
                    List.of("vanilla", "file/g016"));

            // Custom name: set, rename, then clear — one edge per tick.
            Map<String, Object> named = new LinkedHashMap<>();
            named.put("customName", "Bob");
            adapter.entityPort().setEntityState(named);
            adapter.lifecyclePort().fireTick(2);
            e2e.awaitSettle();

            named.put("customName", "Robert");
            adapter.entityPort().setEntityState(named);
            adapter.lifecyclePort().fireTick(3);
            e2e.awaitSettle();

            adapter.entityPort().setEntityState(new LinkedHashMap<>());
            adapter.lifecyclePort().fireTick(4);
            e2e.awaitSettle();

            assertReport(e2e, "resource-pack");
            assertReport(e2e, "name-set");
            assertReport(e2e, "name-rename");
            assertReport(e2e, "name-clear");
            assertTrue(e2e.scriptErrors().isEmpty(),
                    "script errors: " + e2e.scriptErrors());
        }
    }
}
