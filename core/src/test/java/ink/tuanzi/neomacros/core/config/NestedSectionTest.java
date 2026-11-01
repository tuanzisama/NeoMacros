package ink.tuanzi.neomacros.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nested-section shape: an options field whose type is itself an
 * annotated object round-trips as a sub-object ({@code core.web.*}).
 */
class NestedSectionTest {

    /** Mirrors the core section's nested web block. */
    static final class HostSection implements ConfigSection {
        @Option(description = "nested settings")
        public WebSettings web = new WebSettings();

        @Override
        public String key() {
            return "core";
        }

        @Override
        public int schemaVersion() {
            return ConfigManager.TARGET_VERSION;
        }
    }

    @Test
    void nestedSectionRoundTripsAsASubObject(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("options.json");
        var section = new HostSection();
        section.web.enabled = true;
        section.web.port = 52841;
        section.web.panelUrl = "http://localhost:5173";
        section.web.listen = "loopback";

        var manager = new ConfigManager(file, message -> { });
        manager.register(section);
        manager.save();
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("\"web\""), "nested sub-object present");

        var reloaded = new HostSection();
        var second = new ConfigManager(file, message -> { });
        second.register(reloaded);
        second.load();
        assertTrue(reloaded.web.enabled);
        assertEquals(52841, reloaded.web.port);
        assertEquals("http://localhost:5173", reloaded.web.panelUrl);
        assertEquals("loopback", reloaded.web.listen);
        assertFalse(reloaded.web.autoStart);
    }

    @Test
    void nestedDefaultsSurvivePartialFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("options.json");
        Files.writeString(file, "{\"core\":{\"web\":{\"port\":1234,"
                + "\"unknownKey\":true}}}", StandardCharsets.UTF_8);
        var section = new HostSection();
        var manager = new ConfigManager(file, message -> { });
        manager.register(section);
        manager.load();
        assertEquals(1234, section.web.port);
        assertFalse(section.web.enabled);
        assertEquals("https://panel.neomacros.app", section.web.panelUrl);
    }
}
