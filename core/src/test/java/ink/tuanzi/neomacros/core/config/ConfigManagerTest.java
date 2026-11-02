package ink.tuanzi.neomacros.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Config file contract: round-trip, clamping, backup on save, corrupt-file
 * recovery from backup, quarantining when the backup is unreadable, and the
 * migration chain entry.
 */
class ConfigManagerTest {

    /** Test section exercising every supported option type. */
    static final class TestSection implements ConfigSection {
        @Option(description = "flag")
        public boolean enabled = true;
        @Option(name = "sync_ms", min = 50, max = 10_000)
        public int maxSyncTime = 500;
        @Option
        public double ratio = 0.5;
        @Option
        public String label = "default";
        @Option
        public List<String> tags = new ArrayList<>(List.of("a"));
        @Option
        public Map<String, List<String>> trust = Map.of();

        @Override
        public String key() {
            return "test";
        }

        @Override
        public int schemaVersion() {
            return ConfigManager.TARGET_VERSION;
        }
    }

    private static List<String> warnings() {
        return new ArrayList<>();
    }

    @Test
    void roundTripKeepsValues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("options.json");
        var section = new TestSection();
        section.enabled = false;
        section.maxSyncTime = 750;
        section.ratio = 0.25;
        section.label = "custom";
        section.tags = new ArrayList<>(List.of("x", "y"));
        section.trust = Map.of("hash1", List.of("fs"));

        try (ConfigManager manager = new ConfigManager(file, warnings()::add)) {
            manager.register(section);
            manager.load();
            manager.save();
        }

        try (ConfigManager manager = new ConfigManager(file, warnings()::add)) {
            var reloaded = new TestSection();
            manager.register(reloaded);
            manager.load();
            assertEquals(false, reloaded.enabled);
            assertEquals(750, reloaded.maxSyncTime);
            assertEquals(0.25, reloaded.ratio);
            assertEquals("custom", reloaded.label);
            assertEquals(List.of("x", "y"), reloaded.tags);
            assertEquals(Map.of("hash1", List.of("fs")), reloaded.trust);
        }
    }

    @Test
    void numericValuesClampToDeclaredBounds(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("options.json");
        Files.writeString(file, "{\"test\": {\"sync_ms\": 99999}}", StandardCharsets.UTF_8);

        var section = new TestSection();
        try (ConfigManager manager = new ConfigManager(file, warnings()::add)) {
            manager.register(section);
            manager.load();
        }
        assertEquals(10_000, section.maxSyncTime);
    }

    @Test
    void unknownKeysAreIgnoredAndWarned(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("options.json");
        Files.writeString(file, "{\"test\": {\"legacyOption\": 3, \"enabled\": false}}",
                StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();

        var section = new TestSection();
        try (ConfigManager manager = new ConfigManager(file, warnings::add)) {
            manager.register(section);
            manager.load();
        }
        assertEquals(false, section.enabled);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("legacyOption")));
    }

    @Test
    void corruptFileRestoresFromBackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("options.json");
        Path backup = dir.resolve("options.json.bak");
        Files.writeString(backup, "{\"test\": {\"label\": \"from-backup\"}}", StandardCharsets.UTF_8);
        Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();

        var section = new TestSection();
        try (ConfigManager manager = new ConfigManager(file, warnings::add)) {
            manager.register(section);
            manager.load();
        }
        assertEquals("from-backup", section.label);
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("from-backup"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("restored")));
    }

    @Test
    void corruptFileWithoutBackupQuarantinesAndUsesDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("options.json");
        Files.writeString(file, "{ broken", StandardCharsets.UTF_8);

        var section = new TestSection();
        try (ConfigManager manager = new ConfigManager(file, warnings()::add)) {
            manager.register(section);
            manager.load();
        }
        assertEquals("default", section.label);
        assertTrue(Files.exists(dir.resolve("options.json.corrupted")));
        // The closing save rewrote the file with defaults.
        assertTrue(Files.exists(file));
    }

    @Test
    void createIfMissingWritesDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.json");
        var manager = new ConfigManager(file, message -> { });
        manager.register(new TestSection());
        manager.createIfMissing();
        assertTrue(Files.exists(file));
        // Idempotent: a second call does not disturb the written file.
        long firstWrite = Files.getLastModifiedTime(file).toMillis();
        manager.createIfMissing();
        assertEquals(firstWrite, Files.getLastModifiedTime(file).toMillis());
    }

    @Test
    void missingFileKeepsDefaultsAndSaveCreatesIt(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("options.json");

        var section = new TestSection();
        try (ConfigManager manager = new ConfigManager(file, warnings()::add)) {
            manager.register(section);
            manager.load();
        }
        // First install writes the defaults immediately.
        assertTrue(Files.exists(file));

        try (ConfigManager manager = new ConfigManager(file, warnings()::add)) {
            manager.register(section);
            manager.load();
            section.label = "created";
            manager.save();
        }
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"schema\": 1"));
    }
}
