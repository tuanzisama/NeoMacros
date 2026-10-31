package ink.tuanzi.neomacros.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storage contract: get/put/delete/has round-trip, JSON persistence across
 * instances, plain-data snapshots only, and forced flush on close.
 */
class ModuleStorageTest {

    @Test
    void roundTripAndDelete(@TempDir Path dir) {
        ModuleStorage storage = new ModuleStorage(dir, "alpha", 0, m -> { });
        storage.put("counter", 42);
        storage.put("name", "widget");
        storage.put("nested", Map.of("a", 1, "tags", List.of("x")));

        assertEquals(42, ((Number) storage.get("counter")).intValue());
        assertEquals("widget", storage.get("name"));
        assertTrue(storage.has("nested"));
        storage.delete("name");
        assertNull(storage.get("name"));
        assertFalse(storage.has("name"));
        storage.flushNow();
        storage.close();
    }

    @Test
    void persistsAcrossInstances(@TempDir Path dir) {
        ModuleStorage first = new ModuleStorage(dir, "persist", 0, m -> { });
        first.put("key", Map.of("deep", List.of(1, 2, 3)));
        first.flushNow();
        first.close();

        ModuleStorage second = new ModuleStorage(dir, "persist", 0, m -> { });
        assertEquals(Map.of("deep", List.of(1.0, 2.0, 3.0)), second.get("key"));
        second.close();
    }

    @Test
    void closeFlushesPendingWrites(@TempDir Path dir) throws Exception {
        ModuleStorage storage = new ModuleStorage(dir, "flush", 60_000, m -> { });
        storage.put("late", true);
        // The debounce window has not elapsed; close must still flush.
        storage.close();

        String content = Files.readString(dir.resolve("flush.json"), StandardCharsets.UTF_8);
        assertTrue(content.contains("late"));
        assertTrue(content.contains("{"));
    }

    @Test
    void sanitizesModuleNamesForFiles(@TempDir Path dir) {
        ModuleStorage storage = new ModuleStorage(dir, "we ird/name!", 0, m -> { });
        storage.put("a", 1);
        storage.flushNow();
        storage.close();
        assertTrue(Files.exists(dir.resolve("we_ird_name_.json")));
    }

    @Test
    void nullPutDeletes(@TempDir Path dir) {
        ModuleStorage storage = new ModuleStorage(dir, "nullput", 0, m -> { });
        storage.put("gone", "value");
        storage.put("gone", null);
        assertFalse(storage.has("gone"));
        storage.close();
    }
}
