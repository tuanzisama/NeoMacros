package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-026 E2E scenarios: the JsMacros CommandBuilder members expressed on the
 * declarative {@code command.register} surface — factory kinds, numeric
 * ranges, literals, quoted strings — then dispatched through the port-shaped
 * registry so the typed slots reach the script handler as {@code ctx.args}.
 */
class CommandGapScenariosTest {

    /** The remainder the Java side dispatches; the script asserts the same text. */
    private static final String REMAINDER =
            "set 32 0.5 show 069a79f4-44e9-4726-a5be-fca90e38aaf5"
                    + " minecraft:gold_block 2s 5000000000000 true tagged"
                    + " \"hello world\" and   the rest";

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
    void commandBuilderMembersReachTheDeclarativeSurface() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "commandgap.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const arg = neo.command.arg;",
                    // The factory answers the extended slot vocabulary.",
                    "  results['command.factoryKinds'] = typeof arg.int === 'function'",
                    "      && typeof arg.long === 'function'",
                    "      && typeof arg.literal === 'function'",
                    "      && typeof arg.uuid === 'function'",
                    "      && typeof arg.identifier === 'function'",
                    "      && typeof arg.time === 'function';",
                    // JsMacros CommandBuilder members, mapped declaratively:
                    // literalArg / intArg(min,max) / doubleArg(min,max) /
                    // longArg / uuidArgType / identifierArg / timeArg /
                    // quotedStringArg / wordArg / greedyStringArg / booleanArg.",
                    "  const line = 'set 32 0.5 show 069a79f4-44e9-4726-a5be-fca90e38aaf5 '",
                    "      + 'minecraft:gold_block 2s 5000000000000 true tagged '",
                    "      + '\"hello world\" and   the rest';",
                    "  const spec = {",
                    "    name: 'g026',",
                    "    description: 'G-026 command builder mapping',",
                    "    args: {",
                    "      verb: arg.literal('set'),",
                    "      amount: arg.int().range(1, 64).defaultTo(10),",
                    "      ratio: arg.float().range(0, 1).optional(),",
                    "      show: arg.literal(),",
                    "      id: arg.uuid(),",
                    "      block: arg.identifier(),",
                    "      delay: arg.time(),",
                    "      big: arg.long().range(0, 10000000000000).optional(),",
                    "      flag: arg.bool().optional(),",
                    "      tag: arg.word().optional(),",
                    "      note: arg.string().optional(),",
                    "      rest: arg.greedy().optional()",
                    "    },",
                    "    handler(ctx) {",
                    "      const a = ctx.args;",
                    "      const seen = {",
                    "        verb: a.verb === 'set',",
                    "        amount: a.amount === 32,",
                    "        ratio: a.ratio === 0.5,",
                    "        show: a.show === 'show',",
                    "        id: a.id === '069a79f4-44e9-4726-a5be-fca90e38aaf5',",
                    "        block: a.block === 'minecraft:gold_block',",
                    "        delay: a.delay === 40,",
                    "        big: a.big === 5000000000000,",
                    "        flag: a.flag === true,",
                    "        tag: a.tag === 'tagged',",
                    "        note: a.note === 'hello world',",
                    "        rest: a.rest === 'and   the rest',",
                    "        raw: ctx.raw === line",
                    "      };",
                    "      const pass = Object.values(seen).every(Boolean);",
                    "      neo.__test.report('command-handler-args', pass,",
                    "          JSON.stringify({ seen: seen, raw: ctx.raw }));",
                    "    }",
                    "  };",
                    "  results['command.registered'] = neo.command.register(spec) === 'g026';",
                    "  const probe = neo.command.register({",
                    "    name: 'g026-probe',",
                    "    handler(ctx) { }",
                    "  });",
                    // execute() dispatches synchronously into the stub, so the
                    // probe still exists when the line runs; only then does
                    // the script unregister it.",
                    "  neo.command.execute('/g026-probe').then(ok => {",
                    "    neo.__test.report('command-execute-promise', ok === true, String(ok));",
                    "  });",
                    "  neo.command.unregister(probe);",
                    "  results['command.probeUnregistered'] = probe === 'g026-probe';",
                    "  const pass = Object.values(results).every(Boolean);",
                    "  neo.__test.report('command-builder', pass, JSON.stringify(results));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("commandgap.js"));

            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report built = awaitReport(e2e, "command-builder");
            assertTrue(built != null && built.pass(),
                    "command-builder report: "
                            + (built == null ? "missing" : built.detail())
                            + " script-errors: " + e2e.scriptErrors());
            TestBridge.Report probe = awaitReport(e2e, "command-execute-promise");
            assertTrue(probe != null && probe.pass(),
                    "command-execute-promise report: "
                            + (probe == null ? "missing" : probe.detail())
                            + " script-errors: " + e2e.scriptErrors());

            // Dispatch the way FabricCommandPort does: raw remainder in,
            // typed slots parsed, the script handler reads ctx.args.
            boolean dispatched = e2e.commands()
                    .execute("/g026 " + REMAINDER).get();
            assertTrue(dispatched, "the registered command must dispatch");
            e2e.awaitSettle();

            TestBridge.Report handled = awaitReport(e2e, "command-handler-args");
            assertTrue(handled != null && handled.pass(),
                    "command-handler-args report: "
                            + (handled == null ? "missing" : handled.detail())
                            + " script-errors: " + e2e.scriptErrors());

            // Rejected input never reaches the handler: the wrapped parse
            // raises before the script callback runs.
            assertThrows(IllegalArgumentException.class, () ->
                    e2e.commands().dispatch("g026", "hide 32"),
                    "literalArg mismatch is rejected");
            assertThrows(IllegalArgumentException.class, () ->
                    e2e.commands().dispatch("g026", "set 65"),
                    "intArg(min, max) rejects out-of-range input");
            assertThrows(IllegalArgumentException.class, () ->
                    e2e.commands().dispatch("g026",
                            "set 32 0.5 show not-a-uuid"),
                    "uuidArgType rejects malformed input");
            assertTrue(awaitReport(e2e, "command-handler-args") != null,
                    "the failed dispatches must not overwrite the passing report");

            // The unregister the script performed is visible on the registry.
            assertFalse(e2e.commands().isRegistered("g026-probe"));
            assertTrue(e2e.commands().unregistered().contains("g026-probe"));
            assertTrue(e2e.commands().isRegistered("g026"));
            Map<String, Object> spec = e2e.commands().spec("g026");
            assertTrue(spec.get("args") instanceof Map<?, ?> slots
                            && slots.size() == 12
                            && slots.values().stream().allMatch(
                                    ink.tuanzi.neomacros.core.api.CommandArg.class::isInstance),
                    "the twelve typed slots must cross as CommandArg instances: "
                            + (spec.get("args") == null ? "null"
                            : spec.get("args").getClass().getName()));
        }
    }
}
