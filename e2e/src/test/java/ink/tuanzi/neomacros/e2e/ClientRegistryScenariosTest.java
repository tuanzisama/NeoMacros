package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.EnchantmentSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-025 registry spectrum across the guest boundary: typed lookups,
 * unknown ids reading undefined, the connection-backed id lists, the
 * pre-existing registryIds/registryLookup bridges, and identifier parsing,
 * all served from the stub's canned registry face.
 */
class ClientRegistryScenariosTest {

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
    void typedRegistryLookupsReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var stub = e2e.adapter().clientPort();
            e2e.adapter().itemPort().enchantmentSpec = new EnchantmentSpec(
                    "minecraft:sharpness", 1, 5, 10, false, false,
                    List.of("minecraft:diamond_sword"));

            fixture(dir.resolve("scripts"), "registry.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  try {",
                    "    const checks = {",
                    "      item: neo.client.getItem('stone') !== undefined",
                    "          && neo.client.getItem('stone').id === 'minecraft:stone',",
                    "      itemUnknown: neo.client.getItem('missing') === undefined,",
                    "      stack: neo.client.getItemStack('stone', '[damage=12]') !== undefined",
                    "          && neo.client.getItemStack('stone', '[damage=12]').damage === 12,",
                    "      block: neo.client.getBlock('oak_stairs') !== undefined",
                    "          && neo.client.getBlock('oak_stairs').props.facing === 'north',",
                    "      blockUnknown: neo.client.getBlock('missing') === undefined,",
                    "      blockState: neo.client.getBlockState('oak_stairs', '[facing=south]')",
                    "          !== undefined",
                    "          && neo.client.getBlockState('oak_stairs', '[facing=south]')",
                    "              .props.facing === 'south',",
                    "      effect: neo.client.getStatusEffect('speed') !== undefined",
                    "          && neo.client.getStatusEffect('speed').id === 'minecraft:speed'",
                    "          && neo.client.getStatusEffect('speed').duration === 0,",
                    "      effectUnknown: neo.client.getStatusEffect('missing') === undefined,",
                    "      effects: Array.isArray(neo.client.statusEffects)",
                    "          && neo.client.statusEffects.length === 2,",
                    "      enchantment: neo.client.getEnchantment('sharpness', 3) !== undefined",
                    "          && neo.client.getEnchantment('sharpness', 3).id === 'minecraft:sharpness'",
                    "          && neo.client.getEnchantment('sharpness', 3).level === 3,",
                    "      enchantmentDefaultLevel: neo.client.getEnchantment('sharpness').level === 0,",
                    "      enchantmentIds: Array.isArray(neo.client.enchantmentIds)",
                    "          && neo.client.enchantmentIds[0] === 'minecraft:sharpness',",
                    "      paintingIds: Array.isArray(neo.client.paintingIds)",
                    "          && neo.client.paintingIds[0] === 'minecraft:kebab',",
                    "      featureIds: Array.isArray(neo.client.featureIds)",
                    "          && neo.client.featureIds[0] === 'minecraft:ore',",
                    "      items: Array.isArray(neo.client.items)",
                    "          && neo.client.items.length === 2",
                    "          && neo.client.items[1].id === 'minecraft:stone',",
                    "      blocks: Array.isArray(neo.client.blocks)",
                    "          && neo.client.blocks.length === 1",
                    "          && neo.client.blocks[0].id === 'minecraft:oak_stairs',",
                    "      entityIdsBridge: neo.client.registryIds('entity_type')[0]",
                    "          === 'minecraft:cow',",
                    "      fluidBridge: neo.client.registryLookup('fluid', 'water')",
                    "          === 'minecraft:water',",
                    "      fluidUnknown: neo.client.registryLookup('fluid', 'missing')",
                    "          === undefined,",
                    "      menuBridge: neo.client.registryIds('menu')[0]",
                    "          === 'minecraft:generic_9x3',",
                    "      parseNameSpace: neo.client.parseNameSpace('stone')",
                    "          === 'minecraft:stone',",
                    "      parseIdentifier: neo.client.parseIdentifier('stone')",
                    "          === 'minecraft:stone',",
                    "    };",
                    "    let parseRejected = false;",
                    "    try { neo.client.parseIdentifier('Bad:ID'); }",
                    "    catch (err) { parseRejected = true; }",
                    "    checks.parseIdentifierRejects = parseRejected;",
                    "    let blockRejected = false;",
                    "    try { neo.client.getBlockState('oak_stairs', '[bogus=north]'); }",
                    "    catch (err) { blockRejected = true; }",
                    "    checks.blockStateRejectsMalformed = blockRejected;",
                    "    const pass = Object.values(checks).every(Boolean);",
                    "    const failed = Object.keys(checks).filter((k) => !checks[k]);",
                    "    neo.__test.report('registry', pass,",
                    "        'failed=' + failed.join(','));",
                    "  } catch (err) {",
                    "    neo.__test.report('registry', false, 'sync-error: ' + err);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("registry.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("registry");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
            assertTrue(stub.actions().isEmpty(),
                    "the registry face stays read-only, recorded=" + stub.actions());
        }
    }
}
