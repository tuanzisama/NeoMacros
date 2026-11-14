package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.BossBarInfoData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import ink.tuanzi.neomacros.port.model.ScoreboardObjectiveData;
import ink.tuanzi.neomacros.port.model.ServerInfoData;
import ink.tuanzi.neomacros.port.model.TabListEntryInfoData;
import ink.tuanzi.neomacros.port.model.TabListInfoData;
import ink.tuanzi.neomacros.port.model.TeamData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-018B data-handle faces across the guest boundary: mod metadata,
 * status-effect flags, boss-bar style/color facts, tab-list profile facts,
 * the derived scoreboard objective, and the team/color faces, each read
 * through the property and method surface a script actually sees.
 */
class StatusScoreModScenariosTest {

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
    void dataHandleFacesReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"),
                dir.resolve("storage"))) {
            StubAdapter stub = e2e.adapter();
            var self = new PlayerSnapshot(UUID.randomUUID(), "Alice",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300);
            stub.playerPort().setSelf(self);
            // The player handle resolves its name through the entity lookup.
            stub.entityPort().setEntities(List.of(new EntitySnapshot(self.uuid(),
                    "minecraft:player", "Alice", new Pos3d(0, 64, 0), 20)));

            stub.clientPort().serverInfo =
                    new ServerInfoData("My Server", "mc.example:25565", 42);

            TeamData red = new TeamData("red", "Red Team", "red", 14, 0xFF5555,
                    List.of("Alice"));
            stub.worldPort().addTeam(red);
            stub.worldPort().addTeam(new TeamData("plain", "Plain Team", "reset", -1,
                    -1, List.of("Bob")));
            stub.worldPort().setLocalTeam(red);

            ScoreboardObjectiveData objective = new ScoreboardObjectiveData(
                    "sidebar", "Sidebar", "dummy", "integer",
                    Map.of("Alice", 12, "Bob", 7),
                    Map.of(12, "Alice", 7, "Bob"),
                    List.of("Alice", "Bob"),
                    List.of("Alice", "Bob"),
                    List.of("Alice", "Bob"));
            stub.worldPort().setDisplayObjective(1, objective);
            stub.worldPort().setDisplayObjective(17, objective);

            stub.worldPort().setTabList(new TabListInfoData("head", "foot", List.of(
                    new TabListEntryInfoData("u-1", "Alice", 42, "survival", "Al",
                            new byte[] {1, 2, 3},
                            "minecraft:textures/entity/steve.png",
                            "https://skins.example/steve.png",
                            "minecraft:textures/entity/cape.png", null, true, false,
                            red),
                    new TabListEntryInfoData("u-2", "Bob", 7, "creative", null,
                            null, null, null, null, null, false, true, null))));

            stub.worldPort().addBossBar(new BossBarInfoData("b-1", "Ender Dragon",
                    0.5f, "purple", "notched_10", 0xAA00AA, "dark_purple"));

            fixture(dir.resolve("scripts"), "datahandles.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  try {",
                    "    const checks = {};",
                    // ServerInfoHelper face: the connected-server entry.
                    "    const info = neo.client.serverInfo;",
                    "    checks.serverInfo = info != null",
                    "        && info.name === 'My Server'",
                    "        && info.address === 'mc.example:25565'",
                    "        && info.ping === 42;",
                    // ModContainerHelper: full loader metadata per mod.
                    "    const mods = neo.client.mods;",
                    "    checks.modList = Array.isArray(mods) && mods.length >= 1;",
                    "    const mod = neo.client.getMod('neomacros');",
                    "    checks.modFacts = mod !== undefined",
                    "        && mod.id === 'neomacros'",
                    "        && mod.name === 'NeoMacros'",
                    "        && typeof mod.description === 'string'",
                    "        && mod.env === 'BOTH'",
                    "        && Array.isArray(mod.authors)",
                    "        && mod.dependencies[0] === 'fabricloader';",
                    "    checks.modAbsent = neo.client.getMod('missing') === undefined;",
                    "    checks.modLoaded = neo.client.isModLoaded('neomacros') === true;",
                    // StatusEffectHelper: instance flags plus type facts.
                    "    const speed = neo.client.getStatusEffect('speed');",
                    "    checks.statusEffect = speed !== undefined",
                    "        && speed.id === 'minecraft:speed'",
                    "        && speed.duration === 0 && speed.amplifier === 0",
                    "        && speed.infinite === false",
                    "        && speed.ambient === false && speed.showIcon === true",
                    "        && speed.visible === true && speed.instant === false",
                    "        && speed.beneficial === true && speed.neutral === false",
                    "        && speed.harmful === false;",
                    "    const slow = neo.client.getStatusEffect('slowness');",
                    "    checks.statusEffectHarmful = slow !== undefined",
                    "        && slow.harmful === true && slow.beneficial === false;",
                    "    checks.statusEffectUnknown =",
                    "        neo.client.getStatusEffect('missing') === undefined;",
                    "    checks.statusEffects = Array.isArray(neo.client.statusEffects)",
                    "        && neo.client.statusEffects.length === 2;",
                    // BossBarHelper: identity, progress, style, color facts.
                    "    const bar = neo.world.bossBars[0];",
                    "    checks.bossBar = bar !== undefined",
                    "        && bar.uuid === 'b-1'",
                    "        && bar.name === 'Ender Dragon'",
                    "        && bar.progress === 0.5",
                    "        && bar.color === 'purple'",
                    "        && bar.style === 'notched_10'",
                    "        && bar.colorValue === 0xAA00AA",
                    "        && bar.colorFormat === 'dark_purple';",
                    // PlayerListEntryHelper: profile, skin and team facts.
                    "    const entries = neo.world.tabList.entries;",
                    "    const entry = entries[0];",
                    "    checks.playerEntry = entry.uuid === 'u-1'",
                    "        && entry.name === 'Alice' && entry.latency === 42",
                    "        && entry.gamemode === 'survival'",
                    "        && entry.displayName === 'Al'",
                    "        && entry.cape === true && entry.slimModel === false",
                    "        && entry.skinTexture === 'minecraft:textures/entity/steve.png'",
                    "        && entry.skinUrl === 'https://skins.example/steve.png'",
                    "        && entry.capeTexture === 'minecraft:textures/entity/cape.png'",
                    "        && entry.elytraTexture === undefined",
                    "        && entry.publicKey instanceof Uint8Array",
                    "        && entry.publicKey.length === 3",
                    "        && entry.team !== undefined && entry.team.name === 'red'",
                    "        && entry.team.colorName === 'red';",
                    "    const second = entries[1];",
                    "    checks.playerEntryAbsent = second.publicKey === undefined",
                    "        && second.team === undefined && second.cape === false",
                    "        && second.skinTexture === undefined",
                    "        && second.displayName === undefined;",
                    // ScoreboardObjectiveHelper: the derived objective face.
                    "    const objective = neo.world.getObjectiveSlot(1);",
                    "    checks.objective = objective !== undefined",
                    "        && objective.name === 'sidebar'",
                    "        && objective.displayName === 'Sidebar'",
                    "        && objective.criteria === 'dummy'",
                    "        && objective.renderType === 'integer'",
                    "        && objective.playerScores.Alice === 12",
                    "        && objective.playerScores.Bob === 7",
                    "        && objective.scoreToDisplayName[12] === 'Alice'",
                    "        && objective.texts.length === 2",
                    "        && objective.texts[0] === 'Alice'",
                    "        && objective.knownPlayers[0] === 'Alice'",
                    "        && objective.knownPlayersDisplayNames.length === 2;",
                    "    checks.objectiveAbsent =",
                    "        neo.world.getObjectiveSlot(99) === undefined",
                    "        && neo.world.getObjectiveForTeamColorIndex(-1) === undefined",
                    "        && neo.world.getObjectiveForTeamColorIndex(16) === undefined;",
                    // ScoreboardsHelper: teams, colour faces, current sidebar.
                    "    checks.teams = Array.isArray(neo.world.teams)",
                    "        && neo.world.teams.length === 2",
                    "        && neo.world.teams[0].name === 'red'",
                    "        && neo.world.teams[0].displayName === 'Red Team'",
                    "        && neo.world.teams[0].colorName === 'red'",
                    "        && neo.world.teams[0].colorIndex === 14",
                    "        && neo.world.teams[0].colorValue === 0xFF5555",
                    "        && neo.world.teams[0].players[0] === 'Alice';",
                    "    checks.localTeam = neo.world.playerTeam !== undefined",
                    "        && neo.world.playerTeam.name === 'red'",
                    "        && neo.world.playerTeamColorIndex === 14",
                    "        && neo.world.teamColor === 0xFF5555",
                    "        && neo.world.teamColorName === 'red'",
                    "        && neo.world.teamColorFormatting === 'red';",
                    "    checks.currentScoreboard =",
                    "        neo.world.currentScoreboard !== undefined",
                    "        && neo.world.currentScoreboard.name === 'sidebar';",
                    // Overload binding: parameterized getters stay methods
                    // while the zero-arg forms publish as properties.
                    "    checks.overloads =",
                    "        typeof neo.world.getTeamColor === 'function'",
                    "        && typeof neo.world.getPlayerTeam === 'function'",
                    "        && typeof neo.world.getPlayerTeamColorIndex === 'function'",
                    "        && typeof neo.world.getTeamColorName === 'function'",
                    "        && typeof neo.world.getTeamColorFormatting === 'function'",
                    "        && typeof neo.world.teamColor === 'number';",
                    "    checks.playerTeamMethods =",
                    "        neo.world.getTeamColor(neo.player.self) === 0xFF5555",
                    "        && neo.world.getPlayerTeam(neo.player.self).name === 'red'",
                    "        && neo.world.getPlayerTeamColorIndex(neo.player.self) === 14",
                    "        && neo.world.getTeamColorName(neo.player.self) === 'red'",
                    "        && neo.world.getTeamColorFormatting(neo.player.self)",
                    "            === 'red';",
                    "    const pass = Object.values(checks).every(Boolean);",
                    "    const failed = Object.keys(checks).filter((k) => !checks[k]);",
                    "    neo.__test.report('datahandles', pass,",
                    "        'failed=' + failed.join(','));",
                    "  } catch (err) {",
                    "    neo.__test.report('datahandles', false, 'sync-error: ' + err);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("datahandles.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("datahandles");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
            assertTrue(stub.clientPort().actions().isEmpty(),
                    "the data-handle faces stay read-only, recorded="
                            + stub.clientPort().actions());
        }
    }
}
