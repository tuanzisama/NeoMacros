package ink.tuanzi.neomacros.core.update;

import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code core.update} section persists as a nested sub-object like web/mcp. */
class UpdateSettingsConfigTest {

    @Test
    void updateSectionRoundTrips(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.json");
        CoreOptions written = new CoreOptions();
        written.update.enabled = false;
        written.update.intervalHours = 24;
        written.update.channel = "release";
        written.update.notifyInGame = false;
        written.update.notifyEditor = false;
        written.update.sources = java.util.List.of("https://mirror.example/updates.json");
        written.update.ignoredVersion = "dev@0.1.0-dev.99+aaa";
        written.update.lastNotified = "release@0.2.0";

        ConfigManager manager = new ConfigManager(file, message -> { });
        manager.register(written);
        manager.save();

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"update\""), "nested update sub-object present");

        CoreOptions reloaded = new CoreOptions();
        ConfigManager second = new ConfigManager(file, message -> { });
        second.register(reloaded);
        second.load();
        assertTrue(!reloaded.update.enabled);
        assertEquals(24, reloaded.update.intervalHours);
        assertEquals("release", reloaded.update.channel);
        assertTrue(!reloaded.update.notifyInGame);
        assertTrue(!reloaded.update.notifyEditor);
        assertEquals(java.util.List.of("https://mirror.example/updates.json"), reloaded.update.sources);
        assertEquals("dev@0.1.0-dev.99+aaa", reloaded.update.ignoredVersion);
        assertEquals("release@0.2.0", reloaded.update.lastNotified);
    }

    @Test
    void defaultsSurviveOlderConfigFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.json");
        // A config written before the update section existed (only web.port present).
        Files.writeString(file, "{\"core\":{\"web\":{\"port\":1234}}}", StandardCharsets.UTF_8);
        CoreOptions options = new CoreOptions();
        ConfigManager manager = new ConfigManager(file, message -> { });
        manager.register(options);
        manager.load();
        assertTrue(options.update.enabled);
        assertEquals(12, options.update.intervalHours);
        assertEquals("auto", options.update.channel);
        assertEquals("", options.update.ignoredVersion);
        assertEquals(1234, options.web.port);
    }
}
