package ink.tuanzi.neomacros.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Persistent key-value store for one script module, backed by a JSON file
 * under the storage directory. Values must be JSON-serializable plain data
 * (maps, lists, strings, numbers, booleans). Writes are debounced: callers
 * mutate the in-memory map and the file lands after the debounce window; both
 * {@link #flushNow()} and {@link #close()} force it immediately.
 */
public final class ModuleStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() { }.getType();

    private final Path file;
    private final Consumer<String> warn;
    private final ScheduledExecutorService debounceExecutor;
    private final Map<String, Object> data = new LinkedHashMap<>();
    private ScheduledFuture<?> pendingWrite;
    private long debounceMillis;

    /** Loads (or initializes) the store for one script name. */
    public ModuleStorage(Path storageDir, String moduleName, long debounceMillis, Consumer<String> warn) {
        this.file = storageDir.resolve(sanitize(moduleName) + ".json");
        this.warn = warn;
        this.debounceMillis = debounceMillis;
        this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "neomacros-storage-" + sanitize(moduleName));
            thread.setDaemon(true);
            return thread;
        });
        if (Files.isRegularFile(file)) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, Object> loaded = GSON.fromJson(content, MAP_TYPE);
                if (loaded != null) {
                    data.putAll(loaded);
                }
            } catch (IOException | RuntimeException e) {
                warn.accept("storage file unreadable, starting empty: " + e.getMessage());
            }
        }
    }

    /** Reads one value, or null when absent. */
    public Object get(String key) {
        return data.get(key);
    }

    /** Writes one value and schedules a debounced save. */
    public void put(String key, Object value) {
        if (value == null) {
            data.remove(key);
        } else {
            data.put(key, value);
        }
        scheduleSave();
    }

    /** Removes one key and schedules a debounced save. */
    public void delete(String key) {
        data.remove(key);
        scheduleSave();
    }

    /** Returns whether the key exists. */
    public boolean has(String key) {
        return data.containsKey(key);
    }

    /** Schedules a save after the debounce window (one pending write max). */
    private void scheduleSave() {
        if (pendingWrite != null && !pendingWrite.isDone()) {
            return;
        }
        pendingWrite = debounceExecutor.schedule(this::flushNow, debounceMillis, TimeUnit.MILLISECONDS);
    }

    /** Writes the current map to disk immediately. */
    public void flushNow() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn.accept("storage write failed: " + e.getMessage());
        }
    }

    /** Cancels any pending debounced write and flushes; safe to call twice. */
    public void close() {
        if (pendingWrite != null) {
            pendingWrite.cancel(false);
        }
        flushNow();
        debounceExecutor.shutdown();
    }

    private static String sanitize(String moduleName) {
        return moduleName.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
