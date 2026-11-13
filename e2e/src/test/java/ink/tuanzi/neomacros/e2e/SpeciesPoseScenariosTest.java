package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BoundingBoxData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-022 E2E scenarios: the species pose/display face (armor stand
 * visibility and six pose triples, panda gene recessives, display
 * visibility box, block/item/text display members) reaches the guest with
 * the designed shapes, driven through the real engine over the stub
 * adapter. Every scope class of the task carries at least one assertion.
 */
class SpeciesPoseScenariosTest {

    private static final UUID STAND = UUID.randomUUID();
    private static final UUID PANDA = UUID.randomUUID();
    private static final UUID BLOCK_DISPLAY = UUID.randomUUID();
    private static final UUID ITEM_DISPLAY = UUID.randomUUID();
    private static final UUID TEXT_DISPLAY = UUID.randomUUID();

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

    /** One snapshot of the given species type at a fixed spot. */
    private static EntitySnapshot species(UUID uuid, String type, String name) {
        return new EntitySnapshot(uuid, type, name, new Pos3d(0, 64, 0), 20, 20,
                new Pos3d(0, 0, 0), new Pos3d(0, 0, -1), new Rot2d(0, 0), 1.74,
                true, false, false, false, false, false, false, false,
                "standing", "south", "minecraft:overworld", null,
                List.of(), null, null, null, true);
    }

    @Test
    void speciesPoseAndDisplayMembersReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "speciespose.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const stand = neo.entity.lookup('" + STAND + "');",
                    "  results['armorStand.handle'] = stand !== undefined && stand !== null;",
                    "  results['armorStand.invisible'] = stand.invisible === true;",
                    "  results['armorStand.showArms'] = stand.showArms() === true;",
                    "  results['armorStand.showBasePlate'] = stand.showBasePlate() === false;",
                    "  results['armorStand.small'] = stand.small === true;",
                    "  const head = stand.headPose;",
                    "  results['armorStand.headPose'] = head !== undefined && head !== null",
                    "      && head.x === -10 && head.y === 5 && head.z === 0;",
                    "  const body = stand.bodyPose;",
                    "  results['armorStand.bodyPose'] = body !== undefined",
                    "      && body.x === 0 && body.y === 22.5 && body.z === 0;",
                    "  results['armorStand.armPoses'] = stand.leftArmPose.x === -90",
                    "      && stand.rightArmPose.x === 0 && stand.rightArmPose.y === 0;",
                    "  results['armorStand.legPoses'] = stand.leftLegPose.z === 0",
                    "      && stand.rightLegPose.x === 1 && stand.rightLegPose.y === 2",
                    "      && stand.rightLegPose.z === 3;",
                    "  const panda = neo.entity.lookup('" + PANDA + "');",
                    "  results['panda.handle'] = panda !== undefined && panda !== null;",
                    "  results['panda.genes'] = panda.mainGene === 'minecraft:brown'",
                    "      && panda.hiddenGene === 'minecraft:normal';",
                    "  results['panda.recessive'] = panda.mainGeneRecessive === true",
                    "      && panda.hiddenGeneRecessive === false;",
                    "  const blockDisplay = neo.entity.lookup('" + BLOCK_DISPLAY + "');",
                    "  results['blockDisplay.handle'] = blockDisplay !== undefined",
                    "      && blockDisplay !== null;",
                    "  results['blockDisplay.billboard'] = blockDisplay.billboard === 'vertical';",
                    "  results['blockDisplay.block'] = blockDisplay.block === 'minecraft:stone';",
                    "  const rendered = blockDisplay.blockState;",
                    "  results['blockDisplay.blockState'] = rendered !== undefined",
                    "      && rendered !== null && rendered.id === 'minecraft:stone'",
                    "      && rendered.props !== undefined && rendered.props.facing === 'north';",
                    "  const box = blockDisplay.visibilityBoundingBox;",
                    "  results['blockDisplay.visibilityBox'] = box !== undefined && box !== null",
                    "      && box.min !== undefined && box.max !== undefined",
                    "      && box.min.x === -0.5 && box.min.y === 64 && box.min.z === -0.5",
                    "      && box.max.x === 0.5 && box.max.y === 65 && box.max.z === 0.5;",
                    "  const itemDisplay = neo.entity.lookup('" + ITEM_DISPLAY + "');",
                    "  results['itemDisplay.handle'] = itemDisplay !== undefined",
                    "      && itemDisplay !== null;",
                    "  results['itemDisplay.transform'] = itemDisplay.transform === 'ground';",
                    "  results['itemDisplay.billboard'] = itemDisplay.billboard === 'fixed';",
                    "  const textDisplay = neo.entity.lookup('" + TEXT_DISPLAY + "');",
                    "  results['textDisplay.handle'] = textDisplay !== undefined",
                    "      && textDisplay !== null;",
                    "  results['textDisplay.data'] = textDisplay.lineWidth === 200",
                    "      && textDisplay.textOpacity === -1",
                    "      && textDisplay.alignment === 'left';",
                    "  results['textDisplay.flags'] = textDisplay.hasShadow() === true",
                    "      && textDisplay.hasSeeThrough() === false",
                    "      && textDisplay.hasDefaultBackground() === true;",
                    "  results['textDisplay.text'] = textDisplay.text === 'hello'",
                    "      && textDisplay.backgroundColor === 0x40000000;",
                    "  neo.__test.report('species-pose',",
                    "      Object.values(results).every(Boolean), JSON.stringify(results));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("speciespose.js"));

            var adapter = e2e.adapter();
            adapter.entityPort().setEntities(List.of(
                    species(STAND, "minecraft:armor_stand", "Stand"),
                    species(PANDA, "minecraft:panda", "Panda"),
                    species(BLOCK_DISPLAY, "minecraft:block_display", "Block"),
                    species(ITEM_DISPLAY, "minecraft:item_display", "Item"),
                    species(TEXT_DISPLAY, "minecraft:text_display", "Text")));

            Map<String, Object> stand = new LinkedHashMap<>();
            stand.put("marker", false);
            stand.put("small", true);
            stand.put("invisible", true);
            stand.put("showArms", true);
            stand.put("showBasePlate", false);
            stand.put("bodyPose", new Pos3d(0, 22.5, 0));
            stand.put("headPose", new Pos3d(-10, 5, 0));
            stand.put("leftArmPose", new Pos3d(-90, 0, 0));
            stand.put("rightArmPose", new Pos3d(0, 0, 0));
            stand.put("leftLegPose", new Pos3d(0, 0, 0));
            stand.put("rightLegPose", new Pos3d(1, 2, 3));
            adapter.entityPort().setSpeciesState(STAND, stand);

            Map<String, Object> panda = new LinkedHashMap<>();
            panda.put("mainGene", "minecraft:brown");
            panda.put("hiddenGene", "minecraft:normal");
            panda.put("mainGeneRecessive", true);
            panda.put("hiddenGeneRecessive", false);
            adapter.entityPort().setSpeciesState(PANDA, panda);

            Map<String, Object> blockDisplay = new LinkedHashMap<>();
            blockDisplay.put("billboard", "vertical");
            blockDisplay.put("block", "minecraft:stone");
            blockDisplay.put("blockState", new BlockData("minecraft:stone",
                    new Pos3d(0, 64, 0), Map.of("facing", "north")));
            blockDisplay.put("visibilityBoundingBox", new BoundingBoxData(
                    new Pos3d(-0.5, 64, -0.5), new Pos3d(0.5, 65, 0.5)));
            adapter.entityPort().setSpeciesState(BLOCK_DISPLAY, blockDisplay);

            Map<String, Object> itemDisplay = new LinkedHashMap<>();
            itemDisplay.put("billboard", "fixed");
            itemDisplay.put("transform", "ground");
            adapter.entityPort().setSpeciesState(ITEM_DISPLAY, itemDisplay);

            Map<String, Object> textDisplay = new LinkedHashMap<>();
            textDisplay.put("text", "hello");
            textDisplay.put("backgroundColor", 0x40000000);
            textDisplay.put("lineWidth", 200);
            textDisplay.put("textOpacity", -1);
            textDisplay.put("shadow", true);
            textDisplay.put("seeThrough", false);
            textDisplay.put("defaultBackground", true);
            textDisplay.put("alignment", "left");
            adapter.entityPort().setSpeciesState(TEXT_DISPLAY, textDisplay);

            adapter.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "species-pose");
            assertTrue(report != null && report.pass(),
                    "species-pose report: " + (report == null ? "missing" : report.detail()));
            assertEquals(24, countTrue(report.detail()),
                    "every species-pose assertion ran true: " + report.detail());
        }
    }

    /** Counts the true entries in the report's JSON detail map. */
    private static int countTrue(String detail) {
        int hits = 0;
        for (String entry : detail.replaceAll("[{}]", "").split(",")) {
            if (entry.contains(":true")) {
                hits++;
            }
        }
        return hits;
    }
}
