package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.BlockData;
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
 * G-023 E2E scenarios: the species small-gap face reaches the guest with
 * the designed shapes, driven through the real engine over the stub
 * adapter. Every class the batch landed a member on carries at least one
 * assertion, plus spot checks of the members bridged from the existing
 * surface.
 */
class SpeciesSmallGapsScenariosTest {

    private static final UUID WITHER = UUID.randomUUID();
    private static final UUID ENDERMAN = UUID.randomUUID();
    private static final UUID GUARDIAN = UUID.randomUUID();
    private static final UUID ELDER = UUID.randomUUID();
    private static final UUID VINDICATOR = UUID.randomUUID();
    private static final UUID PILLAGER = UUID.randomUUID();
    private static final UUID CLOUD = UUID.randomUUID();
    private static final UUID FALLING = UUID.randomUUID();
    private static final UUID HORSE = UUID.randomUUID();
    private static final UUID DONKEY = UUID.randomUUID();
    private static final UUID AXOLOTL = UUID.randomUUID();
    private static final UUID FISH = UUID.randomUUID();
    private static final UUID WOLF = UUID.randomUUID();
    private static final UUID BOAT = UUID.randomUUID();
    private static final UUID CHEST_BOAT = UUID.randomUUID();
    private static final UUID COW_A = UUID.randomUUID();
    private static final UUID COW_B = UUID.randomUUID();
    private static final UUID DROWNED = UUID.randomUUID();
    private static final UUID SHULKER = UUID.randomUUID();
    private static final UUID WITCH = UUID.randomUUID();
    private static final UUID FOX = UUID.randomUUID();
    private static final UUID MOOSHROOM = UUID.randomUUID();
    private static final UUID PUFFERFISH = UUID.randomUUID();
    private static final UUID RABBIT = UUID.randomUUID();
    private static final UUID CRYSTAL = UUID.randomUUID();
    private static final UUID PAINTING = UUID.randomUUID();
    private static final UUID ALLAY = UUID.randomUUID();
    private static final UUID ARROW = UUID.randomUUID();
    private static final UUID HOOK = UUID.randomUUID();
    private static final UUID ZOMBIE = UUID.randomUUID();
    private static final UUID ZOMBIE_VILLAGER = UUID.randomUUID();
    private static final UUID TNT = UUID.randomUUID();
    private static final UUID TNT_MINECART = UUID.randomUUID();
    private static final UUID VILLAGER = UUID.randomUUID();
    private static final UUID GHAST = UUID.randomUUID();
    private static final UUID GOAT = UUID.randomUUID();
    private static final UUID TRADER_LLAMA = UUID.randomUUID();
    private static final UUID GLOW_FRAME = UUID.randomUUID();

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
        return species(uuid, type, name, null);
    }

    /** One snapshot of the given species type carrying a main-hand stack. */
    private static EntitySnapshot species(UUID uuid, String type, String name,
            ItemStackData held) {
        return new EntitySnapshot(uuid, type, name, new Pos3d(0, 64, 0), 20, 20,
                new Pos3d(0, 0, 0), new Pos3d(0, 0, -1), new Rot2d(0, 0), 1.74,
                true, false, false, false, false, false, false, false,
                "standing", "south", "minecraft:overworld", null,
                List.of(), null, held, null, true);
    }

    @Test
    void speciesSmallGapMembersReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "speciessmallgap.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const at = (id) => neo.entity.lookup(id);",
                    "  const wither = at('" + WITHER + "');",
                    "  results['wither.powered'] = wither.powered === true",
                    "      && wither.invulnerableTicks === 0;",
                    "  const enderman = at('" + ENDERMAN + "');",
                    "  results['enderman.provoked'] = enderman.provoked === true",
                    "      && enderman.screaming === false;",
                    "  const held = enderman.heldBlock;",
                    "  results['enderman.heldBlock'] = held !== undefined && held !== null",
                    "      && held.id === 'minecraft:sand'",
                    "      && held.props !== undefined && held.props.falling_block === 'true';",
                    "  const guardian = at('" + GUARDIAN + "');",
                    "  results['guardian.moving'] = guardian.moving === true;",
                    "  const elder = at('" + ELDER + "');",
                    "  results['guardian.elder'] = elder.type === 'minecraft:elder_guardian'",
                    "      && elder.moving === false;",
                    "  const vindicator = at('" + VINDICATOR + "');",
                    "  results['illager.celebrating'] = vindicator.celebrating === true",
                    "      && vindicator.armPose === 'crossed';",
                    "  const pillager = at('" + PILLAGER + "');",
                    "  results['pillager.captain'] = pillager.headArmor !== undefined",
                    "      && pillager.headArmor !== null",
                    "      && pillager.headArmor.is('minecraft:white_banner') === true;",
                    "  const cloud = at('" + CLOUD + "');",
                    "  results['effectCloud.color'] = cloud.color === 0x40FF00FF",
                    "      && cloud.particle === 'minecraft:entity_effect';",
                    "  const falling = at('" + FALLING + "');",
                    "  const state = falling.blockState;",
                    "  results['fallingBlock.state'] = state !== undefined && state !== null",
                    "      && state.id === 'minecraft:sand'",
                    "      && state.props.falling_block === 'true';",
                    "  const start = falling.startPos;",
                    "  results['fallingBlock.startPos'] = start !== undefined && start !== null",
                    "      && start.x === 12 && start.y === 70 && start.z === 14;",
                    "  const horse = at('" + HORSE + "');",
                    "  results['horse.standing'] = horse.standing === true",
                    "      && horse.tamed === true;",
                    "  const donkey = at('" + DONKEY + "');",
                    "  results['donkey.chest'] = donkey.hasChest() === true;",
                    "  const axolotl = at('" + AXOLOTL + "');",
                    "  results['axolotl.variant'] = axolotl.variantId === 0",
                    "      && axolotl.variant === 'minecraft:lucy';",
                    "  const fish = at('" + FISH + "');",
                    "  results['tropicalFish.size'] = fish.size === 'large'",
                    "      && fish.varietyId === 16909060;",
                    "  results['tropicalFish.pattern'] = fish.pattern === 'minecraft:kob';",
                    "  const wolf = at('" + WOLF + "');",
                    "  results['wolf.begging'] = wolf.begging === true && wolf.angry === false;",
                    "  const boat = at('" + BOAT + "');",
                    "  const boatItem = boat.boatItem;",
                    "  results['boat.pickResult'] = boatItem !== undefined",
                    "      && boatItem !== null && boatItem.id === 'minecraft:oak_boat'",
                    "      && boatItem.count === 1 && boat.underWater === false;",
                    "  const chestBoat = at('" + CHEST_BOAT + "');",
                    "  results['boat.chestBoat'] = chestBoat instanceof neo.ChestBoatEntity;",
                    "  const cowA = at('" + COW_A + "');",
                    "  const cowB = at('" + COW_B + "');",
                    "  results['animal.isFood'] = cowA.isFood('carrot') === true;",
                    "  results['animal.canBreedWith'] = cowA.canBreedWith(cowB) === true;",
                    "  const drowned = at('" + DROWNED + "');",
                    "  results['drowned.trident'] = drowned.heldItem !== undefined",
                    "      && drowned.heldItem.is('minecraft:trident') === true;",
                    "  const shulker = at('" + SHULKER + "');",
                    "  results['shulker.closed'] = shulker.peek <= 0",
                    "      && shulker.attachFace === 'up';",
                    "  const witch = at('" + WITCH + "');",
                    "  results['witch.potion'] = witch.heldItem !== undefined",
                    "      && witch.heldItem.id === 'minecraft:awkward_potion';",
                    "  const fox = at('" + FOX + "');",
                    "  results['fox.variant'] = fox.variant === 'minecraft:snow';",
                    "  const mooshroom = at('" + MOOSHROOM + "');",
                    "  results['mooshroom.variant'] = mooshroom.variant === 'minecraft:red';",
                    "  const pufferfish = at('" + PUFFERFISH + "');",
                    "  results['pufferfish.size'] = pufferfish.puffState === 2;",
                    "  const rabbit = at('" + RABBIT + "');",
                    "  results['rabbit.variant'] = rabbit.variant === 'minecraft:killer_bunny';",
                    "  const crystal = at('" + CRYSTAL + "');",
                    "  results['endCrystal.bottom'] = crystal.showingBottom === true;",
                    "  const painting = at('" + PAINTING + "');",
                    "  results['painting.art'] = painting.artId === 'minecraft:kebab';",
                    "  const allay = at('" + ALLAY + "');",
                    "  results['allay.holding'] = allay.hasItemInHand() === true;",
                    "  const arrow = at('" + ARROW + "');",
                    "  results['arrow.pierce'] = arrow.pierceLevel === 2;",
                    "  const hook = at('" + HOOK + "');",
                    "  results['hook.openWater'] = hook.openWater === true",
                    "      && hook.hookedEntity === undefined;",
                    "  const zombie = at('" + ZOMBIE + "');",
                    "  results['zombie.converting'] = zombie.underWaterConverting === true;",
                    "  const zombieVillager = at('" + ZOMBIE_VILLAGER + "');",
                    "  results['zombieVillager.converting'] = zombieVillager.converting === true",
                    "      && zombieVillager.variant === 'minecraft:snow';",
                    "  const tnt = at('" + TNT + "');",
                    "  results['tnt.fuse'] = tnt.fuse === 40;",
                    "  const tntMinecart = at('" + TNT_MINECART + "');",
                    "  results['tntMinecart.fuse'] = tntMinecart.fuse === 40;",
                    "  const villager = at('" + VILLAGER + "');",
                    "  results['villager.style'] = villager.variant === 'minecraft:plains';",
                    "  const ghast = at('" + GHAST + "');",
                    "  results['ghast.shooting'] = ghast.charging === true;",
                    "  const goat = at('" + GOAT + "');",
                    "  results['goat.horns'] = goat.hasLeftHorn() === true",
                    "      && goat.hasRightHorn() === true;",
                    "  const traderLlama = at('" + TRADER_LLAMA + "');",
                    "  results['llama.trader'] = traderLlama.type === 'minecraft:trader_llama'",
                    "      && traderLlama instanceof neo.LlamaEntity;",
                    "  const glowFrame = at('" + GLOW_FRAME + "');",
                    "  results['itemFrame.glow'] = glowFrame.type === 'minecraft:glow_item_frame'",
                    "      && glowFrame instanceof neo.ItemFrameEntity;",
                    "  neo.__test.report('species-small-gaps',",
                    "      Object.values(results).every(Boolean), JSON.stringify(results));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("speciessmallgap.js"));

            var adapter = e2e.adapter();
            adapter.entityPort().setEntities(List.of(
                    species(WITHER, "minecraft:wither", "Wither"),
                    species(ENDERMAN, "minecraft:enderman", "Enderman"),
                    species(GUARDIAN, "minecraft:guardian", "Guardian"),
                    species(ELDER, "minecraft:elder_guardian", "Elder"),
                    species(VINDICATOR, "minecraft:vindicator", "Vindicator"),
                    species(PILLAGER, "minecraft:pillager", "Pillager"),
                    species(CLOUD, "minecraft:area_effect_cloud", "Cloud"),
                    species(FALLING, "minecraft:falling_block", "Falling"),
                    species(HORSE, "minecraft:horse", "Horse"),
                    species(DONKEY, "minecraft:donkey", "Donkey"),
                    species(AXOLOTL, "minecraft:axolotl", "Axolotl"),
                    species(FISH, "minecraft:tropical_fish", "Fish"),
                    species(WOLF, "minecraft:wolf", "Wolf"),
                    species(BOAT, "minecraft:oak_boat", "Boat"),
                    species(CHEST_BOAT, "minecraft:oak_chest_boat", "ChestBoat"),
                    species(COW_A, "minecraft:cow", "CowA"),
                    species(COW_B, "minecraft:cow", "CowB"),
                    species(DROWNED, "minecraft:drowned", "Drowned",
                            new ItemStackData("minecraft:trident", 1, "Trident")),
                    species(SHULKER, "minecraft:shulker", "Shulker"),
                    species(WITCH, "minecraft:witch", "Witch",
                            new ItemStackData("minecraft:awkward_potion", 1, "Potion")),
                    species(FOX, "minecraft:fox", "Fox"),
                    species(MOOSHROOM, "minecraft:mooshroom", "Mooshroom"),
                    species(PUFFERFISH, "minecraft:pufferfish", "Pufferfish"),
                    species(RABBIT, "minecraft:rabbit", "Rabbit"),
                    species(CRYSTAL, "minecraft:end_crystal", "Crystal"),
                    species(PAINTING, "minecraft:painting", "Painting"),
                    species(ALLAY, "minecraft:allay", "Allay"),
                    species(ARROW, "minecraft:arrow", "Arrow"),
                    species(HOOK, "minecraft:fishing_bobber", "Hook"),
                    species(ZOMBIE, "minecraft:zombie", "Zombie"),
                    species(ZOMBIE_VILLAGER, "minecraft:zombie_villager", "ZombieVillager"),
                    species(TNT, "minecraft:tnt", "Tnt"),
                    species(TNT_MINECART, "minecraft:tnt_minecart", "TntMinecart"),
                    species(VILLAGER, "minecraft:villager", "Villager"),
                    species(GHAST, "minecraft:ghast", "Ghast"),
                    species(GOAT, "minecraft:goat", "Goat"),
                    species(TRADER_LLAMA, "minecraft:trader_llama", "TraderLlama"),
                    species(GLOW_FRAME, "minecraft:glow_item_frame", "GlowFrame")));

            // The pillager captain check reads the head equipment slot, which
            // the stub serves from one shared living-extras map.
            Map<String, Object> extras = new LinkedHashMap<>();
            extras.put("headArmor", new ItemStackData("minecraft:white_banner", 1, "Banner"));
            adapter.entityPort().setLivingExtras(extras);

            adapter.entityPort().setSpeciesState(WITHER, Map.of(
                    "invulnerableTicks", 0, "powered", true));
            adapter.entityPort().setSpeciesState(ENDERMAN, Map.of(
                    "provoked", true, "screaming", false, "carriedBlock", "minecraft:sand",
                    "heldBlock", new BlockData("minecraft:sand", new Pos3d(0, 64, 0),
                            Map.of("falling_block", "true"))));
            adapter.entityPort().setSpeciesState(GUARDIAN, Map.of("moving", true));
            adapter.entityPort().setSpeciesState(ELDER, Map.of("moving", false));
            adapter.entityPort().setSpeciesState(VINDICATOR, Map.of(
                    "armPose", "crossed", "celebrating", true));
            adapter.entityPort().setSpeciesState(PILLAGER, Map.of("chargingCrossbow", false));
            adapter.entityPort().setSpeciesState(CLOUD, Map.of(
                    "radius", 3.0, "waiting", false, "waitTime", 0,
                    "particle", "minecraft:entity_effect", "color", 0x40FF00FF));
            adapter.entityPort().setSpeciesState(FALLING, Map.of(
                    "block", "minecraft:sand",
                    "blockState", new BlockData("minecraft:sand", new Pos3d(0, 64, 0),
                            Map.of("falling_block", "true")),
                    "startPos", new Pos3d(12, 70, 14)));
            adapter.entityPort().setSpeciesState(HORSE, Map.of(
                    "tamed", true, "standing", true, "saddled", false));
            adapter.entityPort().setSpeciesState(DONKEY, Map.of(
                    "tamed", false, "standing", false, "hasChest", true));
            adapter.entityPort().setSpeciesState(AXOLOTL, Map.of(
                    "variant", "minecraft:lucy", "variantId", 0, "playingDead", false));
            adapter.entityPort().setSpeciesState(FISH, Map.of(
                    "pattern", "minecraft:kob", "baseColor", "minecraft:white",
                    "patternColor", "minecraft:orange", "size", "large",
                    "varietyId", 0x01020304));
            adapter.entityPort().setSpeciesState(WOLF, Map.of(
                    "collarColor", "minecraft:red", "begging", true, "angry", false));
            adapter.entityPort().setSpeciesState(BOAT, Map.of(
                    "underWater", false,
                    "boatItem", new ItemStackData("minecraft:oak_boat", 1, "Oak Boat")));
            adapter.entityPort().setSpeciesState(CHEST_BOAT, Map.of("underWater", false));
            adapter.entityPort().setSpeciesState(SHULKER, Map.of(
                    "peek", 0.0, "attachFace", "up", "color", "minecraft:red"));
            adapter.entityPort().setSpeciesState(FOX, Map.of("variant", "minecraft:snow"));
            adapter.entityPort().setSpeciesState(MOOSHROOM, Map.of(
                    "variant", "minecraft:red", "shearable", false));
            adapter.entityPort().setSpeciesState(PUFFERFISH, Map.of("puffState", 2));
            adapter.entityPort().setSpeciesState(RABBIT, Map.of(
                    "variant", "minecraft:killer_bunny"));
            adapter.entityPort().setSpeciesState(CRYSTAL, Map.of("showBottom", true));
            adapter.entityPort().setSpeciesState(PAINTING, Map.of(
                    "artId", "minecraft:kebab", "width", 1, "height", 1));
            adapter.entityPort().setSpeciesState(ALLAY, Map.of("hasItemInHand", true));
            adapter.entityPort().setSpeciesState(ARROW, Map.of(
                    "critical", false, "pierceLevel", 2));
            adapter.entityPort().setSpeciesState(HOOK, Map.of("openWater", true));
            adapter.entityPort().setSpeciesState(ZOMBIE, Map.of("underWaterConverting", true));
            adapter.entityPort().setSpeciesState(ZOMBIE_VILLAGER, Map.of(
                    "converting", true, "variant", "minecraft:snow"));
            adapter.entityPort().setSpeciesState(TNT, Map.of("fuse", 40));
            adapter.entityPort().setSpeciesState(TNT_MINECART, Map.of(
                    "fuse", 40, "primed", true));
            adapter.entityPort().setSpeciesState(VILLAGER, Map.of(
                    "profession", "minecraft:farmer", "level", 1,
                    "variant", "minecraft:plains"));
            adapter.entityPort().setSpeciesState(GHAST, Map.of("charging", true));
            adapter.entityPort().setSpeciesState(GOAT, Map.of(
                    "leftHorn", true, "rightHorn", true, "screaming", false));
            adapter.entityPort().setSpeciesState(TRADER_LLAMA, Map.of(
                    "strength", 3, "llamaVariant", "minecraft:creamy",
                    "hasChest", false));

            adapter.entityPort().isFoodResult = true;
            adapter.entityPort().canBreedWithResult = true;

            adapter.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "species-small-gaps");
            assertTrue(report != null && report.pass(),
                    "species-small-gaps report: " + (report == null ? "missing" : report.detail()));
            assertEquals(41, countTrue(report.detail()),
                    "every species-small-gaps assertion ran true: " + report.detail());
            assertEquals("minecraft:carrot", adapter.entityPort().lastFoodId,
                    "the food id reaches the port already namespaced");
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
