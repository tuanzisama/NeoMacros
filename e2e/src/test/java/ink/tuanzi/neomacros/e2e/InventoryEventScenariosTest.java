package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inventory event family E2E-A scenarios over the real engine and the stub
 * adapter: the cancellable click/drop/open pre-events reach script handlers,
 * and the slot/pickup observers carry the new payload fields.
 */
class InventoryEventScenariosTest {

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
    void scriptCancelsClicksDropsAndChestOpens() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "inv-cancel.js", String.join("\n",
                    "neo.events.onSync('inventory:click', (ev) => {",
                    "  if (ev.slot === 5 && ev.mode === 'throw') ev.cancel();",
                    "});",
                    "neo.events.onSync('inventory:dropSlot', (ev) => {",
                    "  if (ev.all) ev.cancel();",
                    "});",
                    "neo.events.onSync('inventory:open', (ev) => {",
                    "  if (ev.kind === 'chest') ev.cancel();",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("inv-cancel.js"));

            var stub = e2e.adapter().inventoryPort();
            assertTrue(stub.fireClickSlot(new InventoryPort.ClickSlotInfo(
                    1, "chest", 5, 1, "throw", false)), "the script cancels the throw click");
            assertFalse(stub.fireClickSlot(new InventoryPort.ClickSlotInfo(
                    1, "chest", 6, 0, "pickup", false)), "other clicks pass");
            assertTrue(stub.fireDropSlot(new InventoryPort.DropSlotInfo("player", 40, true)));
            assertFalse(stub.fireDropSlot(new InventoryPort.DropSlotInfo("player", 40, false)));
        }
    }

    @Test
    void asyncObserversSeeTheClickPayload() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "inv-watch-click.js", String.join("\n",
                    "neo.events.on('inventory:click', (ev) => {",
                    "  neo.__test.report('inv-click-seen',",
                    "    ev.kind === 'chest' && ev.slot === 7 && ev.mode === 'pickup'",
                    "        && ev.button === 0 && ev.shift === false && ev.cancellable === true,",
                    "    JSON.stringify({ kind: ev.kind, slot: ev.slot, mode: ev.mode }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("inv-watch-click.js"));
            e2e.adapter().inventoryPort().fireClickSlot(new InventoryPort.ClickSlotInfo(
                    1, "chest", 7, 0, "pickup", false));
            e2e.awaitSettle();

            TestBridge.Report seen = e2e.bridge().get("inv-click-seen");
            assertTrue(seen != null && seen.pass(),
                    "inv-click-seen: " + (seen == null ? "missing" : seen.detail())
                            + " | script errors: " + e2e.scriptErrors());
        }
    }

    @Test
    void scriptSeesSlotAndPickupPayloads() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "inv-watch.js", String.join("\n",
                    "let slot = null;",
                    "let pickup = null;",
                    "neo.events.on('inventory:update', (ev) => {",
                    "  if (ev.mode === 'slot') slot = ev;",
                    "});",
                    "neo.events.on('item:pickup', (ev) => {",
                    "  pickup = ev;",
                    "  neo.__test.report('inv-watch', slot !== null",
                    "      && slot.slot === 3 && slot.oldStack.damage === 10",
                    "      && slot.newStack.damage === 11",
                    "      && pickup.amount === 3 && pickup.item.id === 'minecraft:iron_ingot',",
                    "    JSON.stringify({ slot: slot && slot.slot,",
                    "      old: slot && slot.oldStack && slot.oldStack.damage,",
                    "      amount: pickup.amount, item: pickup.item.id }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("inv-watch.js"));

            var stub = e2e.adapter().inventoryPort();
            var oldStack = new ItemStackData("minecraft:iron_pickaxe", 1, "Pickaxe", 10, 250,
                    List.of(), List.of(), java.util.Map.of());
            var newStack = new ItemStackData("minecraft:iron_pickaxe", 1, "Pickaxe", 11, 250,
                    List.of(), List.of(), java.util.Map.of());
            stub.fireSlotUpdate(new InventoryPort.SlotUpdateInfo(1, "chest", 3, false,
                    oldStack, newStack));
            stub.fireItemPickup(new InventoryPort.ItemPickupInfo(new ItemStackData(
                    "minecraft:iron_ingot", 3, "Iron Ingot", 0, 0, List.of(), List.of(),
                    java.util.Map.of()), 3));
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("inv-watch");
            assertTrue(report != null && report.pass(),
                    "inv-watch: " + (report == null ? "missing" : report.detail())
                            + " | script errors: " + e2e.scriptErrors());
        }
    }

    @Test
    void openAndUpdateCarryTheScreenClassName() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "inv-screen.js", String.join("\n",
                    "neo.events.on('inventory:open', (ev) => {",
                    "  neo.__test.report('inv-open-screen',",
                    "      ev.screen === 'GenericContainerScreen',",
                    "      JSON.stringify({ screen: ev.screen }));",
                    "});",
                    "neo.events.on('inventory:update', (ev) => {",
                    "  if (ev.mode === 'slot' && ev.screen === 'GenericContainerScreen') {",
                    "    neo.__test.report('inv-update-screen', ev.slot === 3,",
                    "        JSON.stringify({ screen: ev.screen, slot: ev.slot }));",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("inv-screen.js"));

            var adapter = e2e.adapter();
            adapter.playerPort().setSelf(new ink.tuanzi.neomacros.port.model.PlayerSnapshot(
                    java.util.UUID.randomUUID(), "tester",
                    new ink.tuanzi.neomacros.port.model.Pos3d(0, 64, 0),
                    new ink.tuanzi.neomacros.port.model.Rot2d(0, 0), 20, 20, 300));
            adapter.lifecyclePort().setScreen(java.util.Optional.of(
                    new ink.tuanzi.neomacros.port.LifecyclePort.ScreenInfo(
                            "GenericContainerScreen", "chest", "Chest")));
            adapter.inventoryPort().opened = java.util.Optional.of(
                    new InventoryPort.ContainerInfo("Chest", "chest", 27, 1));
            adapter.lifecyclePort().fireTick(1);
            adapter.lifecyclePort().fireTick(2);
            e2e.awaitSettle();

            TestBridge.Report openSeen = e2e.bridge().get("inv-open-screen");
            assertTrue(openSeen != null && openSeen.pass(),
                    "inv-open-screen: " + (openSeen == null ? "missing" : openSeen.detail())
                            + " | script errors: " + e2e.scriptErrors());

            var stack = new ItemStackData("minecraft:diamond", 1, "Diamond", 0, 0,
                    List.of(), List.of(), java.util.Map.of());
            adapter.inventoryPort().fireSlotUpdate(new InventoryPort.SlotUpdateInfo(
                    1, "chest", 3, false, stack, stack));
            e2e.awaitSettle();

            TestBridge.Report updateSeen = e2e.bridge().get("inv-update-screen");
            assertTrue(updateSeen != null && updateSeen.pass(),
                    "inv-update-screen: " + (updateSeen == null ? "missing" : updateSeen.detail())
                            + " | script errors: " + e2e.scriptErrors());
        }
    }

    @Test
    void cancelledChestOpenClosesTheContainer() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "inv-open.js", String.join("\n",
                    "neo.events.onSync('inventory:open', (ev) => {",
                    "  if (ev.kind === 'chest') ev.cancel();",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("inv-open.js"));

            var adapter = e2e.adapter();
            adapter.playerPort().setSelf(new ink.tuanzi.neomacros.port.model.PlayerSnapshot(
                    java.util.UUID.randomUUID(), "tester",
                    new ink.tuanzi.neomacros.port.model.Pos3d(0, 64, 0),
                    new ink.tuanzi.neomacros.port.model.Rot2d(0, 0), 20, 20, 300));
            adapter.inventoryPort().opened = java.util.Optional.of(
                    new InventoryPort.ContainerInfo("Chest", "chest", 27, 1));
            adapter.lifecyclePort().fireTick(1);
            adapter.lifecyclePort().fireTick(2);
            e2e.awaitSettle();

            assertEquals(List.of("close"), adapter.inventoryPort().actions(),
                    "the cancelled open closes the container right away");
        }
    }
}
