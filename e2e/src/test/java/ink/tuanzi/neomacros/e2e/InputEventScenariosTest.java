package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.KeyPressEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-430 scenarios: the cancellable input:keyPress channel, the input:wheel
 * publish, the chat:send intercept channel, and script-defined custom events
 * (register/emit round trip).
 */
class InputEventScenariosTest {

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
    void keyPressIsCancellableAndWheelPublishes() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "keys.js", String.join("\n",
                    "neo.events.onSync('input:keyPress', (ev) => {",
                    "  if (ev.key === 'key.keyboard.blocked') { ev.cancel(); }",
                    "});",
                    "neo.events.on('input:keyPress', (ev) => {",
                    "  neo.__test.report('key-press', ev.key === 'key.keyboard.free' && ev.action === 'press',",
                    "    JSON.stringify({ key: ev.key, action: ev.action }));",
                    "});",
                    "neo.events.on('input:wheel', (ev) => {",
                    "  neo.__test.report('wheel', ev.amount === 2.5, String(ev.amount));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("keys.js"));

            var adapter = e2e.adapter();
            // The blocked key must cancel (the onSync verdict feeds the port
            // fire result); the free key must pass through. Both outcomes are
            // observed via the report bindings the script registers.
            adapter.inputPort().fireEdge(new KeyPressEdge(
                    "key.keyboard.blocked", 43, true));
            adapter.inputPort().fireEdge(new KeyPressEdge(
                    "key.keyboard.free", 39, true));
            adapter.inputPort().onScrollFire(2.5);
            e2e.awaitSettle();

            TestBridge.Report keyReport = e2e.bridge().get("key-press");
            assertTrue(keyReport != null && keyReport.pass(),
                    "key report: " + (keyReport == null ? "missing" : keyReport.detail()));
            TestBridge.Report wheelReport = e2e.bridge().get("wheel");
            assertTrue(wheelReport != null && wheelReport.pass(),
                    "wheel report: " + (wheelReport == null ? "missing" : wheelReport.detail()));
        }
    }

    @Test
    void chatSendInterceptsCancelsAndRewrites() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "chatguard.js", String.join("\n",
                    "neo.events.onSync('chat:send', (ev) => {",
                    "  if (ev.message === 'spam') { ev.cancel(); }",
                    "  if (ev.message === 'rewrite-me') { ev.message = 'rewritten'; }",
                    "});",
                    "neo.events.on('chat:send', (ev) => {",
                    "  neo.__test.report('chat-send', ev.direction === 'c2s', ev.direction);",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("chatguard.js"));

            var adapter = e2e.adapter();
            var blocked = adapter.chatPort().fireChatSend("spam");
            e2e.awaitSettle();
            var rewritten = adapter.chatPort().fireChatSend("rewrite-me");
            e2e.awaitSettle();
            var pass = adapter.chatPort().fireChatSend("hello");
            assertTrue(blocked.canceled(), "spam must be canceled");
            assertFalse(rewritten.canceled(), "rewritten message passes");
            assertEquals("rewritten", rewritten.message(), "rewrite verdict must carry the text");
            assertFalse(pass.canceled() || pass.message() != null, "hello passes untouched");
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("chat-send");
            assertTrue(report != null && report.pass(),
                    "chat-send report: " + (report == null ? "missing" : report.detail()));
        }
    }

    @Test
    void customEventsRegisterEmitAndSubscribe() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "custom.js", String.join("\n",
                    "neo.events.register('macro:pulse');",
                    "neo.events.on('macro:pulse', (ev) => {",
                    "  neo.__test.report('custom', ev.count === 3, JSON.stringify(ev));",
                    "});",
                    "neo.events.emit('macro:pulse', { count: 3 });",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("custom.js"));

            var adapter = e2e.adapter();
            adapter.lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("custom");
            assertTrue(report != null && report.pass(),
                    "custom report: " + (report == null ? "missing" : report.detail()));

            // Registering a second time or shadowing a built-in domain raises.
            var script = String.join("\n",
                    "let raised = '';",
                    "try { neo.events.register('macro:pulse'); } catch (e) { raised = 'dup'; }",
                    "try { neo.events.register('player:fake'); } catch (e) { raised += '-domain'; }",
                    "neo.__test.report('custom-guards', raised === 'dup-domain', raised);",
                    "");
            fixture(dir.resolve("scripts"), "guards.js", script);
            e2e.loadScript(dir.resolve("scripts").resolve("guards.js"));
            adapter.lifecyclePort().fireTick(2);
            e2e.awaitSettle();

            TestBridge.Report guards = e2e.bridge().get("custom-guards");
            assertTrue(guards != null && guards.pass(),
                    "guards report: " + (guards == null ? "missing" : guards.detail()));
        }
    }
}
