package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.EffectSnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-014 E2E scenarios: the player and entity event payloads carry the new
 * fields (armor slot delta, experience previous triple, signed health delta,
 * death position and inventory snapshot, held-hand flag with the previous
 * stack, riding's former vehicle, the replaced status effect and the entity
 * post-change health), driven through the real engine over the stub adapter.
 */
class PlayerEntityEventScenariosTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID ZOMBIE = UUID.randomUUID();
    private static final UUID MOUNT = UUID.randomUUID();

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

    private static ItemStackData stack(String id) {
        return new ItemStackData(id, 1, id);
    }

    private static PlayerSnapshot player(double health) {
        return new PlayerSnapshot(PLAYER, "tester", new Pos3d(0, 64, 0), new Rot2d(0, 0),
                health, 20, 300);
    }

    /** One living zombie snapshot at the player's feet, with the given health. */
    private static EntitySnapshot living(UUID uuid, double health) {
        return new EntitySnapshot(uuid, "minecraft:zombie", "Zombie",
                new Pos3d(2, 64, 2), health, 20,
                new Pos3d(0, 0, 0), new Pos3d(0, 0, -1), new Rot2d(0, 0), 1.74,
                true, false, false, false, false, false, false, false,
                "standing", "south", "minecraft:overworld", null,
                List.of(), null, null, null, true);
    }

    @Test
    void playerAndEntityPayloadsCarryTheNewFields() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "player-entity-payload.js", String.join("\n",
                    "neo.events.on('player:exp', (ev) => {",
                    "  neo.__test.report('exp',",
                    "      ev.level === 5 && ev.total === 120 && ev.prevLevel === 0",
                    "          && ev.prevTotal === 0 && ev.prevProgress === 0,",
                    "      JSON.stringify({ level: ev.level, total: ev.total,",
                    "        prevLevel: ev.prevLevel, prevTotal: ev.prevTotal }));",
                    "});",
                    "neo.events.on('player:armor', (ev) => {",
                    "  neo.__test.report('armor',",
                    "      ev.slot === 'chest'",
                    "          && ev.item !== undefined && ev.item.id === 'minecraft:diamond_chestplate'",
                    "          && ev.oldItem !== undefined",
                    "          && ev.oldItem.id === 'minecraft:iron_chestplate',",
                    "      JSON.stringify({ slot: ev.slot,",
                    "        item: ev.item && ev.item.id, old: ev.oldItem && ev.oldItem.id }));",
                    "});",
                    "neo.events.on('player:health', (ev) => {",
                    "  if (ev.health !== 15) return;",
                    "  neo.__test.report('health', ev.change === -5,",
                    "      JSON.stringify({ health: ev.health, change: ev.change }));",
                    "});",
                    "neo.events.on('player:heldItem', (ev) => {",
                    "  if (ev.offHand) {",
                    "    neo.__test.report('held-off',",
                    "        ev.previous !== undefined && ev.previous.id === 'minecraft:torch'",
                    "            && ev.current !== undefined",
                    "            && ev.current.id === 'minecraft:shield',",
                    "        JSON.stringify({ offHand: ev.offHand,",
                    "          previous: ev.previous && ev.previous.id }));",
                    "  } else {",
                    "    neo.__test.report('held-main',",
                    "        ev.previous !== undefined && ev.previous.id === 'minecraft:stick'",
                    "            && ev.current !== undefined",
                    "            && ev.current.id === 'minecraft:diamond_sword',",
                    "        JSON.stringify({ offHand: ev.offHand,",
                    "          previous: ev.previous && ev.previous.id }));",
                    "  }",
                    "});",
                    "neo.events.on('player:riding', (ev) => {",
                    "  if (ev.vehicle !== undefined) {",
                    "    neo.__test.report('riding-mount',",
                    "        ev.previousVehicle === undefined,",
                    "        JSON.stringify({ vehicle: ev.vehicle && ev.vehicle.uuid }));",
                    "  } else {",
                    "    neo.__test.report('riding-dismount',",
                    "        ev.previousVehicle !== undefined",
                    "            && String(ev.previousVehicle.uuid) === '" + MOUNT + "',",
                    "        JSON.stringify({ previous: ev.previousVehicle",
                    "          && String(ev.previousVehicle.uuid) }));",
                    "  }",
                    "});",
                    "neo.events.on('player:effect', (ev) => {",
                    "  if (ev.previousEffect === undefined) return;",
                    "  neo.__test.report('effect',",
                    "      ev.previousEffect.amplifier === 0 && ev.amplifier === 1",
                    "          && ev.effectId === 'minecraft:speed',",
                    "      JSON.stringify({ previous: ev.previousEffect.amplifier,",
                    "        amplifier: ev.amplifier }));",
                    "});",
                    "neo.events.on('entity:damage', (ev) => {",
                    "  neo.__test.report('entity-damage',",
                    "      String(ev.entity.uuid) === '" + ZOMBIE + "' && ev.amount === 7",
                    "          && ev.health === 13,",
                    "      JSON.stringify({ amount: ev.amount, health: ev.health }));",
                    "});",
                    "neo.events.on('entity:heal', (ev) => {",
                    "  neo.__test.report('entity-heal',",
                    "      String(ev.entity.uuid) === '" + ZOMBIE + "' && ev.amount === 2",
                    "          && ev.health === 15,",
                    "      JSON.stringify({ amount: ev.amount, health: ev.health }));",
                    "});",
                    "neo.events.on('player:death', (ev) => {",
                    "  neo.__test.report('death',",
                    "      ev.deathPos !== undefined && ev.deathPos.y === 64",
                    "          && Array.isArray(ev.inventory)",
                    "          && ev.inventory.length === 2",
                    "          && ev.inventory[0].id === 'minecraft:diamond_sword',",
                    "      JSON.stringify({ deathPos: ev.deathPos && ev.deathPos.y,",
                    "        inventory: ev.inventory && ev.inventory.map(s => s.id) }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("player-entity-payload.js"));

            var adapter = e2e.adapter();
            var playerPort = adapter.playerPort();
            var inventory = adapter.inventoryPort();

            // Baseline sample: every tracker establishes its previous state.
            playerPort.setSelf(player(20.0));
            playerPort.setExp(0, 0f, 0);
            playerPort.setArmor(List.of(stack("minecraft:iron_boots"),
                    stack("minecraft:air"), stack("minecraft:iron_chestplate"),
                    stack("minecraft:air")));
            playerPort.setEffects(List.of(new EffectSnapshot("minecraft:speed", 0, 200)));
            playerPort.setVehicle(null);
            inventory.slots.put(0, stack("minecraft:stick"));
            inventory.slots.put(40, stack("minecraft:torch"));
            adapter.entityPort().setEntities(List.of(living(ZOMBIE, 20.0), living(MOUNT, 30.0)));
            adapter.lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            // One tick moves every tracked state at once.
            playerPort.setSelf(player(15.0));
            playerPort.setExp(5, 0.25f, 120);
            playerPort.setArmor(List.of(stack("minecraft:iron_boots"),
                    stack("minecraft:air"), stack("minecraft:diamond_chestplate"),
                    stack("minecraft:air")));
            playerPort.setEffects(List.of(new EffectSnapshot("minecraft:speed", 1, 200)));
            playerPort.setVehicle(MOUNT);
            inventory.slots.put(0, stack("minecraft:diamond_sword"));
            inventory.slots.put(40, stack("minecraft:shield"));
            adapter.entityPort().setEntities(List.of(living(ZOMBIE, 13.0), living(MOUNT, 30.0)));
            adapter.lifecyclePort().fireTick(2);
            e2e.awaitSettle();

            assertReport(e2e, "exp");
            assertReport(e2e, "armor");
            assertReport(e2e, "health");
            assertReport(e2e, "held-main");
            assertReport(e2e, "held-off");
            assertReport(e2e, "riding-mount");
            assertReport(e2e, "effect");
            assertReport(e2e, "entity-damage");

            // The entity recovers and the player dismounts.
            adapter.entityPort().setEntities(List.of(living(ZOMBIE, 15.0), living(MOUNT, 30.0)));
            playerPort.setVehicle(null);
            adapter.lifecyclePort().fireTick(3);
            e2e.awaitSettle();

            assertReport(e2e, "entity-heal");
            assertReport(e2e, "riding-dismount");

            // The death edge carries the block position and the inventory.
            playerPort.setSelf(player(0.0));
            adapter.lifecyclePort().fireTick(4);
            e2e.awaitSettle();

            assertReport(e2e, "death");
            assertTrue(e2e.scriptErrors().isEmpty(),
                    "script errors: " + e2e.scriptErrors());
        }
    }
}
