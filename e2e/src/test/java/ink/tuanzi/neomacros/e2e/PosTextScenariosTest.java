package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-017B E2E scenarios: the lexicographic point compare, the block-grid
 * recipe behind the raw-coordinate gap, the text style readback, the
 * generic click and style builder members, and the show-entity hover all
 * reach the guest with the designed shapes over the stub adapter.
 */
class PosTextScenariosTest {

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
    void positionMathAndTextMembersReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "postext.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    // Point ordering: x decides first, then y; a value equals itself.
                    "  const a = new neo.Pos2D(1, 9);",
                    "  const b = new neo.Pos2D(2, -9);",
                    "  results['pos.compareOrder'] = a.compare(b) < 0 && b.compare(a) > 0;",
                    "  results['pos.compareTie'] = new neo.Pos2D(1, 2).compare(new neo.Pos2D(1, 3)) < 0;",
                    "  results['pos.compareSelf'] = a.compare(new neo.Pos2D(1, 9)) === 0;",
                    // The raw-coordinate members stay behind the block grid and java layer.
                    "  results['pos.floorRecipe'] = new neo.Pos3D(1.7, 64.2, -3.1).floor().x === 1",
                    "      && new neo.Pos3D(1.7, 64.2, -3.1).floor().z === -4;",
                    // Style readback on the owning node.
                    "  const tip = new neo.Component('tip').build();",
                    "  const link = new neo.Component('docs')",
                    "      .color('red')",
                    "      .hover('hover me')",
                    "      .clickCommand('/home')",
                    "      .build();",
                    "  results['text.styleReadback'] = link.color === 'red'",
                    "      && link.clickValue === '/home'",
                    "      && link.hoverAction === 'show_text'",
                    "      && link.hoverValue.plain === 'hover me';",
                    "  results['text.plainAbsent'] = tip.color === undefined",
                    "      && tip.hoverAction === undefined && tip.hoverValue === undefined;",
                    // The generic click form validates the vanilla vocabulary.",
                    "  const page = new neo.Component('p').click('change_page', '3').build();",
                    "  results['text.genericClick'] = page.clickValue === '3';",
                    "  let rejected = false;",
                    "  try { new neo.Component('p').click('custom', 'x'); } catch (e) { rejected = true; }",
                    "  results['text.clickRejected'] = rejected;",
                    // style() replaces the whole style and keeps the content.",
                    "  const donor = new neo.Component('donor').color('red').hover('t').build();",
                    "  const styled = new neo.Component('target')",
                    "      .color('blue')",
                    "      .style(donor)",
                    "      .build();",
                    "  results['text.styleMerge'] = styled.color === 'red'",
                    "      && styled.content === 'target'",
                    "      && styled.hoverAction === 'show_text';",
                    // Hover kinds are mutually exclusive, the latest call wins.",
                    "  const exclusive = new neo.Component('x')",
                    "      .hover('text')",
                    "      .hoverItem('diamond')",
                    "      .build();",
                    "  results['text.hoverExclusive'] = exclusive.hoverAction === 'show_item'",
                    "      && exclusive.hoverValue.id === 'minecraft:diamond';",
                    // The show-entity hover captures a live entity handle.",
                    "  const chunk = neo.world.chunkAt(8, 64, 8);",
                    "  const sheep = chunk === undefined ? undefined : chunk.entities[0];",
                    "  if (sheep === undefined) {",
                    "    results['text.entityHover'] = false;",
                    "  } else {",
                    "    const byEntity = new neo.Component('sheep').hoverEntity(sheep).build();",
                    "    const hovered = byEntity.hoverValue;",
                    "    results['text.entityHover'] = byEntity.hoverAction === 'show_entity'",
                    "        && hovered !== undefined",
                    "        && hovered.type === 'minecraft:sheep'",
                    "        && typeof hovered.uuid === 'string';",
                    "  }",
                    "  const pass = Object.values(results).every(Boolean);",
                    "  for (const [key, value] of Object.entries(results)) {",
                    "    neo.chat.log('[E2E] ' + key + '=' + value);",
                    "  }",
                    "  neo.__test.report('pos-text-gap', pass, JSON.stringify(results));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("postext.js"));

            var adapter = e2e.adapter();
            adapter.worldPort().setChunk(new ChunkData(0, 0, -64, 319, 0,
                    Map.of(), List.of()));
            adapter.entityPort().setEntities(List.of(new EntitySnapshot(
                    UUID.randomUUID(), "minecraft:sheep", "Sheep",
                    new Pos3d(4, 64, 4), 8)));

            adapter.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "pos-text-gap");
            assertTrue(report != null && report.pass(),
                    "pos-text-gap report: "
                            + (report == null ? "missing" : report.detail())
                            + " script-errors: " + e2e.scriptErrors());
        }
    }
}
