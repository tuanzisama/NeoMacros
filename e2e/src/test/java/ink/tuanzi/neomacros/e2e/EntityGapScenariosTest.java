package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
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
 * G-012 E2E scenarios: the base-entity state face (glow, custom name, air,
 * speed), the living traits (armor slots, attributes, undead/baby/bow pull),
 * the two-handle predicates and the client-side writes reach the guest with
 * the designed shapes, driven through the real engine over the stub adapter.
 */
class EntityGapScenariosTest {

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

    /** One living zombie snapshot: the minimal constructor leaves entities plain. */
    private static EntitySnapshot zombie() {
        return new EntitySnapshot(ZOMBIE, "minecraft:zombie", "Zombie",
                new Pos3d(0, 64, 0), 20, 20,
                new Pos3d(0, 0, 0), new Pos3d(0, 0, -1), new Rot2d(0, 0), 1.74,
                true, false, false, false, false, false, false, false,
                "standing", "south", "minecraft:overworld", null,
                List.of(), null, null, null, true);
    }

    @Test
    void entityBaseStateAndLivingTraitsReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "entitygap.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const e = neo.entity.lookup('" + ZOMBIE + "');",
                    "  results['entity.handle'] = e !== undefined && e !== null;",
                    "  results['entity.glowing'] = e.glowing === true;",
                    "  results['entity.glowColor'] = e.glowColor === 255;",
                    "  results['entity.customName'] = e.customName === 'Bob';",
                    "  results['entity.customNameVisible'] = e.customNameVisible === true;",
                    "  results['entity.air'] = e.air === 280;",
                    "  results['entity.maxAir'] = e.maxAir === 300;",
                    "  results['entity.speed'] = e.speed === 3.5;",
                    "  results['living.headArmor'] = e.headArmor !== undefined",
                    "      && e.headArmor.id === 'minecraft:diamond_helmet';",
                    "  results['living.chestArmor'] = e.chestArmor === undefined;",
                    "  results['living.legArmor'] = e.legArmor !== undefined",
                    "      && e.legArmor.id === 'minecraft:iron_leggings';",
                    "  results['living.footArmor'] = e.footArmor !== undefined",
                    "      && e.footArmor.id === 'minecraft:boots';",
                    "  results['living.armorToughness'] = e.armorToughness === 2;",
                    "  results['living.defaultMaxHealth'] = e.defaultMaxHealth === 20;",
                    "  results['living.breathesUnderwater'] = e.breathesUnderwater === true;",
                    "  results['living.noDrag'] = e.noDrag === false;",
                    "  results['living.partOfGame'] = e.partOfGame === true;",
                    "  results['living.undead'] = e.undead === true;",
                    "  results['living.baby'] = e.baby === true;",
                    "  results['living.bowPull'] = e.bowPull === 0.75;",
                    "  results['living.predicates'] = e.canBeAffected('speed')",
                    "      && e.canAttack(e) && e.hasLineOfSight(e);",
                    "  Promise.all([",
                    "    e.setGlowing(true),",
                    "    e.resetGlowing(),",
                    "    e.setGlowingColor(255),",
                    "    e.resetGlowingColor(),",
                    "    e.setCustomName('Bob'),",
                    "    e.setCustomName(null),",
                    "    e.setCustomNameVisible(false),",
                    "  ]).then(() => {",
                    "    results['entity.writes'] = true;",
                    "    neo.__test.report('entity-gap',",
                    "        Object.values(results).every(Boolean), JSON.stringify(results));",
                    "  }, (err) => {",
                    "    results['entity.writes'] = false;",
                    "    neo.__test.report('entity-gap', false,",
                    "        JSON.stringify(results) + ':' + String(err));",
                    "  });",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("entitygap.js"));

            var adapter = e2e.adapter();
            adapter.entityPort().setEntities(List.of(zombie()));

            Map<String, Object> state = new LinkedHashMap<>();
            state.put("glowing", true);
            state.put("glowColor", 255);
            state.put("customName", "Bob");
            state.put("customNameVisible", true);
            state.put("air", 280);
            state.put("maxAir", 300);
            state.put("speed", 3.5);
            adapter.entityPort().setEntityState(state);

            Map<String, Object> living = new LinkedHashMap<>();
            living.put("headArmor",
                    new ItemStackData("minecraft:diamond_helmet", 1, "Helmet"));
            living.put("chestArmor", null);
            living.put("legArmor",
                    new ItemStackData("minecraft:iron_leggings", 1, "Leggings"));
            living.put("footArmor", new ItemStackData("minecraft:boots", 1, "Boots"));
            living.put("armorToughness", 2.0);
            living.put("defaultMaxHealth", 20.0);
            living.put("breathesUnderwater", true);
            living.put("noDrag", false);
            living.put("partOfGame", true);
            living.put("undead", true);
            living.put("baby", true);
            living.put("bowPull", 0.75);
            adapter.entityPort().setLivingExtras(living);

            adapter.entityPort().canAttackResult = true;
            adapter.entityPort().canBeAffectedResult = true;
            adapter.entityPort().canSeeResult = true;

            adapter.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "entity-gap");
            assertTrue(report != null && report.pass(),
                    "entity-gap report: " + (report == null ? "missing" : report.detail()));
            assertEquals(List.of(
                    "setGlowing:true",
                    "resetGlowing",
                    "setGlowingColor:255",
                    "resetGlowingColor",
                    "setCustomName:Bob",
                    "setCustomName:null",
                    "setCustomNameVisible:false"),
                    adapter.entityPort().writes(),
                    "the stub records every write the guest issued");
        }
    }

    @Test
    void entityStateIsUndefinedWithoutAWorld() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "entitygap-absent.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const e = neo.entity.lookup('" + ZOMBIE + "');",
                    "  neo.__test.report('entity-gap-absent', e === undefined,",
                    "      JSON.stringify({ absent: e === undefined }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("entitygap-absent.js"));

            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "entity-gap-absent");
            assertTrue(report != null && report.pass(),
                    "entity-gap-absent report: "
                            + (report == null ? "missing" : report.detail()));
        }
    }
}
