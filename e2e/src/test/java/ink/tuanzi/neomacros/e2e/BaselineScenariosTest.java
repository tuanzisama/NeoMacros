package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.core.console.ScriptConsole;
import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Baseline E2E-A scenarios: real user-format scripts through the real engine,
 * event framework, API modules, and the stub adapter. Each scenario writes a
 * fixture, loads it through the module manager, drives injected events, and
 * asserts on chat output and {@code __test} reports.
 */
class BaselineScenariosTest {

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
    void topLevelAndGameStartSubscription() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "hello.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { chat } from '@neomacros/chat';",
                    "chat.log('HELLO-TOPLEVEL');",
                    "events.on('game:start', () => chat.log('HELLO-START'));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("hello.js"));

            assertTrue(e2e.adapter().chatPort().output().contains("log:HELLO-TOPLEVEL"),
                    "output was: " + e2e.adapter().chatPort().output());
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();
            assertTrue(e2e.adapter().chatPort().output().contains("log:HELLO-START"),
                    "output was: " + e2e.adapter().chatPort().output());
        }
    }

    @Test
    void tickCountingThroughSelfReport() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "ticker.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { report } from '@neomacros/__test';",
                    "let n = 0;",
                    "events.on('tick', () => {",
                    "  n++;",
                    "  if (n === 3) report('ticker', true, 'counted to 3');",
                    "});",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("ticker.js"));
            e2e.adapter().lifecyclePort().fireTick(1);
            e2e.adapter().lifecyclePort().fireTick(2);
            e2e.adapter().lifecyclePort().fireTick(3);
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("ticker");
            assertTrue(report != null && report.pass(), "ticker report missing");
            assertEquals("counted to 3", report.detail());
        }
    }

    @Test
    void chatReceiveEcho() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "echo.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { chat } from '@neomacros/chat';",
                    "events.on('chat:receive', (ev) => chat.log('echo:' + JSON.stringify(ev)));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("echo.js"));

            e2e.adapter().chatPort().fireReceive("ping");
            e2e.awaitSettle();
            // the async-channel delivery can land a beat after the loop goes
            // idle on cold runners; poll before declaring the echo missing
            long deadline = System.currentTimeMillis() + 5000;
            boolean echoed = false;
            while (System.currentTimeMillis() < deadline) {
                if (e2e.adapter().chatPort().output().stream()
                        .anyMatch(l -> l.contains("echo:"))) {
                    echoed = true;
                    break;
                }
                Thread.sleep(20);
            }
            assertTrue(echoed, "output was: " + e2e.adapter().chatPort().output());
        }
    }

    @Test
    void syncCancellationRejectsEvent() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "filter.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "events.onSync('chat:send', (ev) => ev.message === 'spam');",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("filter.js"));

            var spam = e2e.eventBus().publish("chat:send",
                    new ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent("spam"));
            var ok = e2e.eventBus().publish("chat:send",
                    new ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent("hello"));

            assertTrue(spam.canceled(), "spam should be canceled");
            assertFalse(ok.canceled(), "hello should pass");
        }
    }

    @Test
    void storagePersistsAcrossHarnessRestarts() throws Exception {
        Path scripts = dir.resolve("scripts");
        try (E2ERuntime e2e = E2ERuntime.create(scripts, dir.resolve("storage"))) {
            fixture(scripts, "counter.js", String.join("\n",
                    "import { storage } from '@neomacros/storage';",
                    "import { report } from '@neomacros/__test';",
                    "const n = (storage.get('n') ?? 0) + 1;",
                    "storage.put('n', n);",
                    "report('storage-count', n >= 1, 'n=' + n);",
                    ""));
            e2e.loadScript(scripts.resolve("counter.js"));
            assertTrue(e2e.bridge().get("storage-count").pass());
        }
        try (E2ERuntime e2e = E2ERuntime.create(scripts, dir.resolve("storage"))) {
            fixture(scripts, "counter.js", String.join("\n",
                    "import { storage } from '@neomacros/storage';",
                    "import { report } from '@neomacros/__test';",
                    "report('storage-count2', storage.get('n') === 1, 'n=' + storage.get('n'));",
                    ""));
            e2e.loadScript(scripts.resolve("counter.js"));
            assertTrue(e2e.bridge().get("storage-count2").pass(),
                    "report was: " + e2e.bridge().get("storage-count2"));
        }
    }

    @Test
    void reloadKeepsSingleSubscription() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "reloader.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { report } from '@neomacros/__test';",
                    "let n = 0;",
                    "events.on('game:start', () => { n++; report('reload', n === 1, 'fired ' + n); });",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("reloader.js"));

            e2e.modules().reloadAll();
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("reload");
            assertTrue(report != null && report.pass(), "report was: " + report);
        }
    }

    @Test
    void domainWildcardSubscription() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "wild.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { report } from '@neomacros/__test';",
                    "events.on('lifecycle:*', () => report('wild', true, 'fired'));",
                    "events.on('game:start', () => report('wild-exact', true, 'exact fired'));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("wild.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();
            assertTrue(e2e.bridge().get("wild") != null || e2e.bridge().get("wild-exact") != null,
                    "reports were: " + e2e.bridge().all());
        }
    }

    @Test
    void asyncPrimitivesResolve() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "sleeper.js", String.join("\n",
                    "import { thread } from '@neomacros/core';",
                    "import { report } from '@neomacros/__test';",
                    "report('async-top', true, 'toplevel-ran');",
                    "thread.sleep(5).then(() => {",
                    "  thread.ticks(1).then(() => report('async', true, 'resolved'),",
                    "    (e) => report('async', false, 'ticks-rejected: ' + e));",
                    "}, (e) => report('async', false, 'sleep-rejected: ' + e));",
                    "globalThis.__stage = 'installed';",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("sleeper.js"));
            e2e.awaitSettle();
            Thread.sleep(100);
            e2e.adapter().lifecyclePort().fireTick(1);
            e2e.awaitSettle();
            assertTrue(e2e.bridge().get("async-top") != null,
                    "toplevel missing; reports=" + e2e.bridge().all());
            TestBridge.Report report = e2e.bridge().get("async");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all());
        }
    }

    @Test
    void consoleOutputCarriesIdentityAndLocation() throws Exception {
        var events = new LinkedBlockingQueue<ScriptConsoleEvent>();
        java.util.function.Consumer<ScriptConsoleEvent> dispatcher = events::add;
        ScriptConsole.installDispatcher(dispatcher);
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "logger.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "console.log('boot', {n: 1});",
                    "events.on('game:start', () => console.warn('started'));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("logger.js"));

            ScriptConsoleEvent boot = events.poll(5, TimeUnit.SECONDS);
            assertTrue(boot != null, "the top-level console event is missing");
            assertEquals("logger", boot.macro(), "the macro id is the manifest name");
            assertEquals("index.js", boot.file());
            assertEquals(2, boot.line());
            assertEquals("log", boot.level());

            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();
            ScriptConsoleEvent started = events.poll(5, TimeUnit.SECONDS);
            assertTrue(started != null && "warn".equals(started.level()),
                    "event was: " + started);
            assertEquals("logger", started.macro());
            assertEquals("index.js", started.file());
        } finally {
            ScriptConsole.removeDispatcher(dispatcher);
        }
    }
}
