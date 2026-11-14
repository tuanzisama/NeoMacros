package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-026B E2E scenarios: the completion surface of the declarative
 * {@code command.register} channel — slot {@code suggests} metadata, the
 * command listing, and the suggestions query — routed through the stub the
 * way {@code FabricCommandPort} routes them through brigadier.
 */
class CommandSuggestScenariosTest {

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
    void slotSuggestionsReachTheScriptSurface() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "commandsuggest.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const arg = neo.command.arg;",
                    "  const spec = {",
                    "    name: 'g026b',",
                    "    description: 'G-026B completion mapping',",
                    "    args: {",
                    "      verb: arg.literal('set'),",
                    "      mode: arg.word().suggests(['peaceful', 'easy', 'normal']),",
                    "      target: arg.word().suggests((builder) => {",
                    "        builder.suggestMatching(['alpha', 'beta']).suggest('gamma');",
                    "        builder.suggestWithTooltip('tagged', new neo.Component('marked').build());",
                    "      })",
                    "    },",
                    "    handler(ctx) { }",
                    "  };",
                    "  results['command.registered'] = neo.command.register(spec) === 'g026b';",
                    "  // The command listing sees the registration.",
                    "  results['command.names'] = Array.isArray(neo.command.names)",
                    "      && neo.command.names.includes('g026b');",
                    "  // Literal slot: the fixed word completes its own prefix.",
                    "  neo.command.suggestions('/g026b se').then(list => {",
                    "    neo.__test.report('command-suggest-literal',",
                    "        Array.isArray(list) && list.includes('set'),",
                    "        JSON.stringify(list));",
                    "  });",
                    "  // Static list slot: prefix-filtered candidates for the second token.",
                    "  neo.command.suggestions('g026b set ').then(list => {",
                    "    neo.__test.report('command-suggest-static',",
                    "        list.includes('peaceful') && list.includes('easy')",
                    "            && list.includes('normal'),",
                    "        JSON.stringify(list));",
                    "  });",
                    "  // Callback slot: matching candidates filter, raw and tooltip pass.",
                    "  neo.command.suggestions('g026b set easy g').then(list => {",
                    "    neo.__test.report('command-suggest-callback',",
                    "        list.includes('gamma') && list.includes('tagged')",
                    "            && !list.includes('alpha'),",
                    "        JSON.stringify(list));",
                    "  }).catch(err => {",
                    "    neo.__test.report('command-suggest-callback', false,",
                    "        String((err && err.stack) || err));",
                    "  });",
                    "  // Unknown names complete against the listing.",
                    "  neo.command.suggestions('g026').then(list => {",
                    "    neo.__test.report('command-suggest-prefix',",
                    "        list.includes('g026b'), JSON.stringify(list));",
                    "  });",
                    "  const pass = Object.values(results).every(Boolean);",
                    "  neo.__test.report('command-suggest', pass, JSON.stringify(results));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("commandsuggest.js"));

            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report listing = awaitReport(e2e, "command-suggest");
            assertTrue(listing != null && listing.pass(),
                    "command-suggest report: "
                            + (listing == null ? "missing" : listing.detail())
                            + " script-errors: " + e2e.scriptErrors());
            TestBridge.Report literal = awaitReport(e2e, "command-suggest-literal");
            assertTrue(literal != null && literal.pass(),
                    "command-suggest-literal report: "
                            + (literal == null ? "missing" : literal.detail())
                            + " script-errors: " + e2e.scriptErrors());
            TestBridge.Report statics = awaitReport(e2e, "command-suggest-static");
            assertTrue(statics != null && statics.pass(),
                    "command-suggest-static report: "
                            + (statics == null ? "missing" : statics.detail())
                            + " script-errors: " + e2e.scriptErrors());
            TestBridge.Report callback = awaitReport(e2e, "command-suggest-callback");
            assertTrue(callback != null && callback.pass(),
                    "command-suggest-callback report: "
                            + (callback == null ? "missing" : callback.detail())
                            + " script-errors: " + e2e.scriptErrors());
            TestBridge.Report prefix = awaitReport(e2e, "command-suggest-prefix");
            assertTrue(prefix != null && prefix.pass(),
                    "command-suggest-prefix report: "
                            + (prefix == null ? "missing" : prefix.detail())
                            + " script-errors: " + e2e.scriptErrors());

            // The Java side sees the same routing the port performs.
            assertTrue(e2e.commands().names().contains("g026b"),
                    "the listing must contain the registered command");
            List<String> texts = e2e.commands().suggestions("g026b se").get();
            assertTrue(texts.contains("set"),
                    "the literal slot must complete its word: " + texts);
        }
    }
}
