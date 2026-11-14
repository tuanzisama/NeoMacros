package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockTypeData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-029 E2E scenarios: the typed state-property query face reaches the
 * guest with the designed shapes over the stub adapter — typed convenience
 * members, the generic {@code getProp}/{@code hasProp} lookup with
 * undefined-for-unknown, the vocabulary-gated {@code type}/{@code shape}
 * families, and the {@code getPropRange} numeric range channel.
 */
class BlockStateTypedQueryScenariosTest {

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
    void typedStatePropertyQueryReachesTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "statequery.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const block = neo.world.blockAt(1, 64, 2);",
                    "  results['block.handle'] = block !== undefined;",
                    "  if (block) {",
                    "    const state = block.state;",
                    "    results['state.handle'] = state !== undefined;",
                    "    results['state.facing'] = state.facing === 'east';",
                    "    results['state.axis'] = state.axis === 'x';",
                    "    results['state.half'] = state.half === 'bottom';",
                    "    results['state.waterlogged'] = state.waterlogged === true;",
                    "    results['state.level'] = state.level === 7;",
                    "    results['state.power'] = state.power === 15;",
                    "    results['state.doorHinge'] = state.doorHinge === 'left'",
                    "        && state.instrument === 'harp';",
                    "    results['state.getProp'] = state.getProp('shape') === 'straight'",
                    "        && state.getProp('SHAPE') === 'straight';",
                    "    results['state.getPropUnknown'] = state.getProp('no_such_prop') === undefined;",
                    "    results['state.hasProp'] = state.hasProp('facing') === true",
                    "        && state.hasProp('no_such_prop') === false;",
                    "    results['state.absentUndefined'] = state.snowy === undefined",
                    "        && state.enabled === undefined && state.note === undefined;",
                    "    results['state.vocabGate'] = state.slabType === 'double'",
                    "        && state.chestType === undefined && state.pistonType === undefined;",
                    "    results['state.shapeGate'] = state.stairShape === 'straight'",
                    "        && state.railShape === undefined;",
                    "    const range = state.getPropRange('level');",
                    "    results['state.range'] = Array.isArray(range)",
                    "        && range.length === 2 && range[0] === 0 && range[1] === 7;",
                    "    results['state.rangeUndefined'] =",
                    "        state.getPropRange('facing') === undefined",
                    "        && state.getPropRange('no_such_prop') === undefined;",
                    "  }",
                    "  const pass = Object.values(results).every(Boolean);",
                    "  for (const [key, value] of Object.entries(results)) {",
                    "    neo.chat.log('[E2E] ' + key + '=' + value);",
                    "  }",
                    "  neo.__test.report('state-typed-query', pass, JSON.stringify(results));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("statequery.js"));

            var adapter = e2e.adapter();
            adapter.worldPort().setBlock(stairsBlock());
            adapter.worldPort().setBlockType(stairsType());

            adapter.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "state-typed-query");
            assertTrue(report != null && report.pass(),
                    "state-typed-query report: "
                            + (report == null ? "missing" : report.detail())
                            + " script-errors: " + e2e.scriptErrors());
        }
    }

    /** The canned stair state: typed convenience keys plus a gated {@code type} key. */
    private static BlockData stairsBlock() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("facing", "east");
        props.put("axis", "x");
        props.put("half", "bottom");
        props.put("shape", "straight");
        props.put("type", "double");
        props.put("waterlogged", "true");
        props.put("level", "7");
        props.put("power", "15");
        props.put("hinge", "left");
        props.put("instrument", "harp");
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("luminance", 0);
        extras.put("opaque", true);
        return new BlockData("minecraft:oak_stairs", new Pos3d(1, 64, 2), props, extras);
    }

    /** The canned stair type: eight defined states carrying level 0-7. */
    private static BlockTypeData stairsType() {
        List<Map<String, String>> states = new ArrayList<>();
        for (int i = 0; i <= 7; i++) {
            states.add(Map.of("level", String.valueOf(i)));
        }
        return new BlockTypeData("minecraft:oak_stairs", "Oak Stairs",
                List.of("minecraft:stairs"), 1.5f, 0.6f, 0.6f, 1.0f, false, true,
                Map.of("facing", "east"), states,
                new ItemStackData("minecraft:oak_stairs", 1, "Oak Stairs"));
    }
}
