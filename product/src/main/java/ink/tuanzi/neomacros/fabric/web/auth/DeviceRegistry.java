package ink.tuanzi.neomacros.fabric.web.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Owns {@code web-devices.json}: the paired web devices and their public
 * keys. Approvals and revocations write through to disk immediately (the
 * file is small); the last-seen timestamp updates in memory and flushes on
 * {@link #save()}. A corrupt file is set aside as {@code .corrupted} and the
 * registry falls back to empty, following the options.json recovery idiom.
 */
public final class DeviceRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path file;
    private final Consumer<String> warn;
    private final Map<String, DeviceRecord> devices = new LinkedHashMap<>();

    /** Creates the registry over one storage file and loads existing devices. */
    public DeviceRegistry(Path file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn;
        load();
    }

    /** The device with the given id, or null. */
    public synchronized DeviceRecord get(String id) {
        return devices.get(id);
    }

    /** All devices, oldest first. */
    public synchronized List<DeviceRecord> all() {
        return List.copyOf(devices.values());
    }

    /** Registers an approved device, allocating an unused id, and persists. */
    public synchronized DeviceRecord register(String label, String algo, Map<String, Object> pubJwk) {
        String id = allocateId();
        long now = System.currentTimeMillis() / 1000;
        DeviceRecord record = new DeviceRecord(id, label, algo, pubJwk, now, now);
        devices.put(id, record);
        save();
        return record;
    }

    /** Removes the device; returns false when the id is unknown. Persists. */
    public synchronized boolean revoke(String id) {
        if (devices.remove(id) == null) {
            return false;
        }
        save();
        return true;
    }

    /** Updates the last-seen timestamp in memory (flushed by {@link #save()}). */
    public synchronized void touch(String id) {
        DeviceRecord record = devices.get(id);
        if (record != null) {
            long now = System.currentTimeMillis() / 1000;
            if (now > record.lastSeenAt()) {
                devices.put(id, new DeviceRecord(record.id(), record.label(), record.algo(),
                        record.pubJwk(), record.createdAt(), now));
            }
        }
    }

    /** Writes the device table to disk. */
    public synchronized void save() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        var array = new com.google.gson.JsonArray();
        for (DeviceRecord device : devices.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", device.id());
            entry.addProperty("label", device.label());
            entry.addProperty("algo", device.algo());
            entry.add("pubJwk", Jsons.valueOf(device.pubJwk()));
            entry.addProperty("createdAt", device.createdAt());
            entry.addProperty("lastSeenAt", device.lastSeenAt());
            array.add(entry);
        }
        root.add("devices", array);
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn.accept("cannot write " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn.accept("cannot read " + file.getFileName() + ": " + e.getMessage());
            return;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(content).getAsJsonObject();
        } catch (RuntimeException e) {
            quarantine(e);
            return;
        }
        if (!root.get("devices").isJsonArray()) {
            quarantine(new IllegalStateException("'devices' is not an array"));
            return;
        }
        for (JsonElement element : root.getAsJsonArray("devices")) {
            try {
                JsonObject entry = element.getAsJsonObject();
                String id = entry.get("id").getAsString();
                String label = entry.get("label").getAsString();
                String algo = entry.get("algo").getAsString();
                Map<String, Object> jwk = Jsons.objectOf(entry.get("pubJwk"));
                long createdAt = entry.get("createdAt").getAsLong();
                long lastSeenAt = entry.get("lastSeenAt").getAsLong();
                devices.put(id, new DeviceRecord(id, label, algo, jwk, createdAt, lastSeenAt));
            } catch (RuntimeException e) {
                warn.accept("skipping malformed device entry: " + e.getMessage());
            }
        }
    }

    private void quarantine(Exception cause) {
        warn.accept(file.getFileName() + " is corrupt (" + cause.getMessage() + "); starting empty");
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupted"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            warn.accept("cannot quarantine corrupt device file: " + e.getMessage());
        }
    }

    /** Allocates a short random id that is not in use. */
    private String allocateId() {
        List<String> taken = new ArrayList<>(devices.keySet());
        for (int attempt = 0; attempt < 64; attempt++) {
            byte[] raw = new byte[2];
            RANDOM.nextBytes(raw);
            String id = "dev_" + HexFormat.of().formatHex(raw);
            if (!taken.contains(id)) {
                return id;
            }
        }
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        return "dev_" + HexFormat.of().formatHex(raw);
    }
}
