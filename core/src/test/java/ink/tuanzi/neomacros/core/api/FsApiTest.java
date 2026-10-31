package ink.tuanzi.neomacros.core.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FsApi contract: read/write/list/mkdir/delete/exists round-trip over the
 * sandbox bridge, and path-escape rejection.
 */
class FsApiTest {

    static FsApi api(Path root) {
        var direct = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            return new FsApi(path -> root.resolve(path).normalize(), direct);
        } finally {
            // executor is per-call here; the futures complete on it before join
        }
    }

    private static <T> T join(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void writeReadListDeleteRoundTrip(@TempDir Path dir) throws Exception {
        FsApi api = api(dir);
        join(api.mkdir("scripts/data"));
        join(api.writeText("scripts/data/a.txt", "hello"));
        assertEquals("hello", join(api.readText("scripts/data/a.txt")));
        assertEquals(List.of("a.txt"), join(api.list("scripts/data")));
        assertTrue(join(api.exists("scripts/data/a.txt")));
        join(api.delete("scripts/data/a.txt"));
        assertFalse(join(api.exists("scripts/data/a.txt")));
    }

    @Test
    void pathEscapeIsRejected(@TempDir Path dir) {
        FsApi api = api(dir);
        assertThrows(Exception.class, () -> join(api.readText("../outside.txt")));
    }

    @Test
    void missingFileReadCompletesExceptionally(@TempDir Path dir) throws Exception {
        FsApi api = api(dir);
        var future = api.readText("missing.txt");
        try {
            join(future);
            throw new AssertionError("expected failure");
        } catch (java.util.concurrent.ExecutionException e) {
            // expected: NoSuchFileException wrapped
        }
    }
}
