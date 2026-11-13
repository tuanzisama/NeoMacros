package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.BlockTypeData;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
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
 * G-017 E2E scenarios: the block type face (default state, item form,
 * physics multipliers, tags, states, name), the state physics flags and
 * position queries, the chunk geometry/heightmap/entity/containment
 * members, and the raycast inside flag all reach the guest with the
 * designed shapes over the stub adapter.
 */
class BlockChunkScenariosTest {

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
                "permissions: [java]",
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
    void blockChunkAndRayMembersReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "blockchunk.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const block = neo.world.blockAt(1, 64, 2);",
                    "  results['block.handle'] = block !== undefined;",
                    "  if (block) {",
                    "    results['block.state'] = block.state !== undefined",
                    "        && block.state.id === 'minecraft:stone';",
                    "    results['block.defaultState'] = block.defaultState !== undefined",
                    "        && block.defaultState.id === 'minecraft:stone'",
                    "        && block.defaultState.props.facing === 'north';",
                    "    results['block.defaultItemStack'] = block.defaultItemStack !== undefined",
                    "        && block.defaultItemStack.id === 'minecraft:stone'",
                    "        && block.defaultItemStack.count === 1;",
                    "    results['block.physics'] = block.canMobSpawnInside() === false",
                    "        && block.hasDynamicBounds() === true;",
                    "    const near = (a, b) => Math.abs(a - b) < 1e-6;",
                    "    results['block.multipliers'] = near(block.blastResistance, 1.5)",
                    "        && near(block.jumpVelocityMultiplier, 0.4)",
                    "        && near(block.slipperiness, 0.6)",
                    "        && near(block.velocityMultiplier, 1.0);",
                    "    results['block.tags'] = Array.isArray(block.tags)",
                    "        && block.tags.indexOf('minecraft:base_stone_overworld') >= 0;",
                    "    results['block.hasTag'] = block.hasTag('base_stone_overworld') === true",
                    "        && block.hasTag('minecraft:nope') === false;",
                    "    results['block.name'] = block.name === 'Stone';",
                    "    results['block.states'] = Array.isArray(block.states)",
                    "        && block.states.length === 2;",
                    "    results['block.rawBlockEntity'] = block.rawBlockEntity !== undefined;",
                    "    const state = block.state;",
                    "    results['state.flags'] = state.exceedsCube() === true",
                    "        && state.hasComparatorOutput() === false",
                    "        && state.blocksMovement() === true",
                    "        && state.solid === false;",
                    "    results['state.positionQueries'] =",
                    "        state.allowsSpawning(1, 64, 2, 'sheep') === true",
                    "        && state.shouldSuffocate(1, 64, 2) === false;",
                    "    results['state.fluidAbsent'] = state.fluid === undefined;",
                    "  }",
                    "  const chunk = neo.world.chunkAt(8, 64, 8);",
                    "  results['chunk.handle'] = chunk !== undefined;",
                    "  if (chunk) {",
                    "    results['chunk.minBlock'] = chunk.minBlock.x === 0",
                    "        && chunk.minBlock.y === 0 && chunk.minBlock.z === 0;",
                    "    results['chunk.offsetBlock'] = chunk.offsetBlock(5, 100, 7).x === 5;",
                    "    results['chunk.height'] = chunk.height === 384;",
                    "    results['chunk.topYAt'] = chunk.topYAt(3, 4, 'world_surface') === 96",
                    "        && chunk.topYAt(3, 4, 'ocean_floor') === undefined",
                    "        && chunk.topYAt(99, 4, 'world_surface') === undefined;",
                    "    results['chunk.entities'] = Array.isArray(chunk.entities)",
                    "        && chunk.entities.length === 1;",
                    "    results['chunk.containsAny'] =",
                    "        chunk.containsAny(['minecraft:stone']) === true;",
                    "    results['chunk.containsAll'] =",
                    "        chunk.containsAll(['minecraft:stone', 'minecraft:dirt']) === false;",
                    "    results['chunk.containsEmpty'] = chunk.containsAny([]) === false",
                    "        && chunk.containsAll([]) === true;",
                    "    results['chunk.blockEntities'] = Array.isArray(chunk.blockEntities);",
                    "  }",
                    "  const hit = neo.world.raycast(0, 64, 0, 0, 64, 5);",
                    "  results['ray.handle'] = hit !== undefined;",
                    "  if (hit) {",
                    "    results['ray.insideBlock'] = hit.insideBlock === true;",
                    "    results['ray.face'] = hit.face === 'up';",
                    "    results['ray.block'] = hit.block !== undefined",
                    "        && hit.block.state.id === 'minecraft:stone';",
                    "    results['ray.entityUndefined'] = hit.entity === undefined;",
                    "  }",
                    "  const pass = Object.values(results).every(Boolean);",
                    "  for (const [key, value] of Object.entries(results)) {",
                    "    neo.chat.log('[E2E] ' + key + '=' + value);",
                    "  }",
                    "  neo.__test.report('block-chunk-gap', pass, JSON.stringify(results));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("blockchunk.js"));

            var adapter = e2e.adapter();
            adapter.worldPort().setBlock(stoneBlock());
            adapter.worldPort().setBlockType(stoneType());
            adapter.worldPort().setStateQueries(true, false);
            adapter.worldPort().setChunkContains(true, false);
            adapter.worldPort().setRawBlockEntity(new Object());
            adapter.worldPort().setChunk(new ChunkData(0, 0, -64, 319, 0,
                    Map.of("world_surface", surface()), List.of()));
            adapter.worldPort().setRaycastHit(new BlockInteraction("minecraft:stone",
                    new Pos3d(1, 64, 2), "up", true));
            adapter.entityPort().setEntities(List.of(new EntitySnapshot(
                    UUID.randomUUID(), "minecraft:sheep", "Sheep",
                    new Pos3d(4, 64, 4), 8)));

            adapter.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "block-chunk-gap");
            assertTrue(report != null && report.pass(),
                    "block-chunk-gap report: "
                            + (report == null ? "missing" : report.detail())
                            + " script-errors: " + e2e.scriptErrors());
        }
    }

    /** The canned stone block with its world physics extras attached. */
    private static BlockData stoneBlock() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("luminance", 0);
        extras.put("hardness", 1.5);
        extras.put("opaque", true);
        extras.put("exceedsCube", true);
        extras.put("hasComparatorOutput", false);
        extras.put("blocksMovement", true);
        extras.put("isSolid", false);
        return new BlockData("minecraft:stone", new Pos3d(1, 64, 2),
                Map.of("facing", "north"), extras);
    }

    /** The canned registry facts of the stone block type. */
    private static BlockTypeData stoneType() {
        return new BlockTypeData("minecraft:stone", "Stone",
                List.of("minecraft:base_stone_overworld"),
                1.5f, 0.4f, 0.6f, 1.0f, false, true,
                Map.of("facing", "north"),
                List.of(Map.of(), Map.of("half", "bottom")),
                new ItemStackData("minecraft:stone", 1, "Stone"));
    }

    /** One canned heightmap column: 96 at local (3, 4). */
    private static int[] surface() {
        int[] heights = new int[256];
        heights[3 + 4 * 16] = 96;
        return heights;
    }
}
