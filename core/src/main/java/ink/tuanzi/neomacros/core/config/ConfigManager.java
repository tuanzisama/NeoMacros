package ink.tuanzi.neomacros.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Owns the mod's config file ({@code config.json}): loads registered
 * sections at startup, writes the merged document back, applies the schema
 * version with migrations, and keeps a {@code .bak} copy of the last good
 * file. A corrupt file is restored from the backup; when both are
 * unreadable the corrupt file is set aside as {@code .corrupted} and
 * defaults take over.
 */
public final class ConfigManager implements AutoCloseable {

    /** File-level schema version written next to the section payloads. */
    public static final int TARGET_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Consumer<String> warn;
    private final Map<String, ConfigSection> sections = new LinkedHashMap<>();
    private boolean dirty;

    /** Creates the manager over one config file. */
    public ConfigManager(Path file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn;
        // A missing file means first install: write the defaults on save.
        this.dirty = !Files.exists(file);
    }

    /** Writes the defaults when the config file does not exist yet (first install). */
    public void createIfMissing() {
        if (!Files.exists(file)) {
            save();
        }
    }

    /** Registers one section. Duplicate keys throw. */
    public void register(ConfigSection section) {
        if (sections.put(section.key(), section) != null) {
            throw new IllegalStateException("duplicate config section: " + section.key());
        }
    }

    /**
     * Loads the file into every registered section. Missing files keep
     * defaults; corrupt files restore from the backup or fall back to
     * defaults; outdated schema versions run through the migration chain.
     */
    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn.accept("cannot read config file: " + e.getMessage());
            return;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(content).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            root = recoverCorruptFile(e);
            if (root == null) {
                return;
            }
        }
        int fileVersion = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : 0;
        if (fileVersion > TARGET_VERSION) {
            warn.accept("config file schema v" + fileVersion + " is newer than supported v"
                    + TARGET_VERSION + "; loading what we can");
        }
        JsonObject migrated = migrate(root.deepCopy(), fileVersion);
        for (ConfigSection section : sections.values()) {
            JsonElement payload = migrated.get(section.key());
            if (payload != null && payload.isJsonObject()) {
                ConfigCodec.load(section, payload.getAsJsonObject(), warn);
            }
        }
        dirty = fileVersion != TARGET_VERSION;
    }

    /**
     * Writes the merged document (schema marker + section payloads) to disk.
     * Unconditional: runtime code may mutate section fields (e.g. the trust
     * table) without a dirty-flag handshake, and the write is idempotent.
     */
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", TARGET_VERSION);
        for (ConfigSection section : sections.values()) {
            root.add(section.key(), ConfigCodec.save(section));
        }
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                Files.copy(file, backupPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            warn.accept("cannot write config file: " + e.getMessage());
        }
    }

    /** Flushes on close so callers never need an explicit final save. */
    @Override
    public void close() {
        save();
    }

    private JsonObject recoverCorruptFile(Exception parseError) {
        warn.accept("config file is corrupt (" + parseError.getMessage() + "); trying backup");
        Path backup = backupPath();
        if (Files.exists(backup)) {
            try {
                String content = Files.readString(backup, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
                warn.accept("restored config from backup");
                dirty = true;
                return root;
            } catch (IOException | JsonSyntaxException | IllegalStateException backupError) {
                warn.accept("backup is also unreadable: " + backupError.getMessage());
            }
        }
        Path quarantined = file.resolveSibling(file.getFileName() + ".corrupted");
        try {
            Files.move(file, quarantined, StandardCopyOption.REPLACE_EXISTING);
            warn.accept("corrupt config moved to " + quarantined.getFileName() + "; defaults take over");
        } catch (IOException ioError) {
            warn.accept("cannot quarantine corrupt config: " + ioError.getMessage());
        }
        dirty = true;
        return null;
    }

    /** Applies the registered migration steps from {@code fileVersion} upward. */
    private JsonObject migrate(JsonObject root, int fileVersion) {
        for (int version = fileVersion; version < TARGET_VERSION; version++) {
            Consumer<JsonObject> step = MIGRATIONS.get(version);
            if (step != null) {
                step.accept(root);
            }
        }
        if (fileVersion < TARGET_VERSION) {
            root.addProperty("schema", TARGET_VERSION);
        }
        return root;
    }

    private Path backupPath() {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    /** Migration chain: source file version -> transforming step. */
    private static final Map<Integer, Consumer<JsonObject>> MIGRATIONS = Map.of();

    /** Unmodifiable view of the registered sections (diagnostics and tests). */
    public List<ConfigSection> sections() {
        return new ArrayList<>(sections.values());
    }
}
