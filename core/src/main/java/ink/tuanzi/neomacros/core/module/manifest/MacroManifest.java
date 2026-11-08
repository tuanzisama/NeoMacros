package ink.tuanzi.neomacros.core.module.manifest;

import ink.tuanzi.neomacros.core.module.ScriptMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The parsed {@code neomacro.{yml,json}} declaration of one script
 * folder: identity metadata in the style of a Tampermonkey userscript header
 * plus the typed {@code config} behavior block. {@code name}, {@code
 * version}, and {@code author} are required; everything else falls back to
 * safe defaults. Behavior (trigger definitions, lifecycle) lives exclusively
 * in {@code config.*}; legacy top-level behavior fields are rejected with a
 * migration hint.
 *
 * @param name        unique script identity (commands, trust, autoload keys)
 * @param version     script version string
 * @param author      script author
 * @param description short summary, may be empty
 * @param license     license identifier, may be empty
 * @param homepage    project page URL, may be empty
 * @param supportURL  issue/support URL, may be empty
 * @param main        entry file relative to the folder; empty uses {@code index.js}
 * @param permissions declared capabilities feeding the trust gate
 * @param config      typed behavior block; never null after parsing
 */
public record MacroManifest(String name, String version, String author,
                            String description, String license, String homepage, String supportURL,
                            String main, List<String> permissions, MacroConfig config) {

    /** Fields recognized at the top level; anything else draws a warning. */
    private static final List<String> KNOWN_FIELDS = List.of(
            "name", "version", "author", "description", "license", "homepage",
            "supportURL", "main", "permissions", "config");

    /**
     * Legacy top-level behavior fields with their {@code config.*} migration
     * targets; their presence rejects the manifest with a migration hint.
     */
    private static final Map<String, String> LEGACY_FIELDS = Map.of(
            "key", "config.keypress.key_code",
            "lifecycle", "config.service.lifecycle",
            "autostart", "config.service.autostart",
            "events", "config.event.on",
            "on", "config.<type>.on",
            "modifiers", "config.keypress.modifiers");

    /**
     * Builds and validates a manifest from a raw parsed document. Throws
     * {@link ManifestParseException} when a required field is missing, a
     * value is malformed, or a legacy top-level behavior field is used;
     * unknown fields warn but do not reject.
     */
    public static MacroManifest from(Map<String, Object> document, String source,
                                     Consumer<String> warn) {
        List<String> problems = new ArrayList<>();
        String name = requiredString(document, "name", source, problems);
        String version = requiredString(document, "version", source, problems);
        String author = requiredString(document, "author", source, problems);
        if (!problems.isEmpty()) {
            throw new ManifestParseException(source + ": " + String.join("; ", problems));
        }
        validateName(name, source);
        for (String field : document.keySet()) {
            String legacyTarget = LEGACY_FIELDS.get(field);
            if (legacyTarget != null) {
                throw new ManifestParseException(source + ": field '" + field
                        + "' moved to '" + legacyTarget + "' (see the migration guide)");
            }
            if (!KNOWN_FIELDS.contains(field)) {
                warn.accept(source + ": unknown field '" + field + "' ignored");
            }
        }
        return new MacroManifest(
                name,
                version,
                author,
                optionalString(document, "description"),
                optionalString(document, "license"),
                optionalString(document, "homepage"),
                optionalString(document, "supportURL"),
                optionalString(document, "main"),
                stringList(document.get("permissions"), source, "permissions", true),
                parseConfig(document.get("config"), source, warn));
    }

    /** The lifecycle for a folder of the given category (config override or category default). */
    public ScriptMeta.Lifecycle lifecycleOr(ScriptCategory category) {
        MacroConfig.Service service = configOrEmpty().service();
        if (service != null && service.lifecycle() != null) {
            return service.lifecycle();
        }
        return category.defaultLifecycle();
    }

    /** The declared permissions; never null. */
    public List<String> permissions() {
        return permissions == null ? List.of() : permissions;
    }

    /** The parsed config block; never null. */
    public MacroConfig configOrEmpty() {
        return config == null ? MacroConfig.EMPTY : config;
    }

    /**
     * Category-specific config validation: the section of the macro's own
     * type is required, foreign sections (possible after a category move)
     * only draw a warning.
     */
    public void validateFor(ScriptCategory category, Consumer<String> warn) {
        MacroConfig cfg = configOrEmpty();
        switch (category) {
            case KEYPRESS -> {
                requireSection(cfg.keypress(),
                        "keypress macros must declare the config.keypress block");
                warnForeignSection(cfg.event(), "config.event", category, warn);
                warnForeignSection(cfg.service(), "config.service", category, warn);
            }
            case EVENT -> {
                requireSection(cfg.event(),
                        "event macros must declare the config.event block with at least one 'on' entry");
                if (cfg.event() != null && cfg.event().onOrEmpty().isEmpty()) {
                    throw new ManifestParseException("config.event.on must declare at least one event name");
                }
                warnForeignSection(cfg.keypress(), "config.keypress", category, warn);
                warnForeignSection(cfg.service(), "config.service", category, warn);
            }
            case SERVICES -> {
                warnForeignSection(cfg.keypress(), "config.keypress", category, warn);
                warnForeignSection(cfg.event(), "config.event", category, warn);
            }
        }
    }

    private static void requireSection(Object section, String message) {
        if (section == null) {
            throw new ManifestParseException(message);
        }
    }

    private static void warnForeignSection(Object section, String name, ScriptCategory category,
                                           Consumer<String> warn) {
        if (section != null) {
            warn.accept("config section '" + name + "' does not apply to "
                    + category.dirName() + " macros and is ignored");
        }
    }

    /** Parses the {@code config} block into typed sections. */
    private static MacroConfig parseConfig(Object value, String source, Consumer<String> warn) {
        if (value == null) {
            return MacroConfig.EMPTY;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ManifestParseException(source + ": 'config' must be a mapping");
        }
        Map<String, Object> block = stringKeyed(map, source, "config");
        for (String field : block.keySet()) {
            if (!List.of("keypress", "event", "service").contains(field)) {
                warn.accept(source + ": unknown config section '" + field + "' ignored");
            }
        }
        return new MacroConfig(
                parseKeypress(block.get("keypress"), source),
                parseEvent(block.get("event"), source),
                parseService(block.get("service"), source, warn));
    }

    private static MacroConfig.Keypress parseKeypress(Object value, String source) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ManifestParseException(source + ": 'config.keypress' must be a mapping");
        }
        Map<String, Object> block = stringKeyed(map, source, "config.keypress");
        for (String legacy : List.of("key", "default-key")) {
            if (block.containsKey(legacy)) {
                throw new ManifestParseException(source + ": config.keypress field '" + legacy
                        + "' is now 'key_code' (see the migration guide)");
            }
        }
        String on = optionalString(block, "on");
        if (!on.isEmpty() && !MacroConfig.Keypress.EDGES.contains(on)) {
            throw new ManifestParseException(source + ": config.keypress.on must be one of "
                    + MacroConfig.Keypress.EDGES + " but was '" + on + "'");
        }
        String keyCode = optionalString(block, "key_code");
        List<String> modifiers = stringList(block.get("modifiers"), source, "config.keypress.modifiers", true);
        for (String modifier : modifiers) {
            if (!MacroConfig.Keypress.MODIFIERS.contains(modifier)) {
                throw new ManifestParseException(source + ": config.keypress.modifiers must be a subset of "
                        + MacroConfig.Keypress.MODIFIERS + " but contained '" + modifier + "'");
            }
        }
        return new MacroConfig.Keypress(on, keyCode, modifiers);
    }

    private static MacroConfig.Event parseEvent(Object value, String source) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ManifestParseException(source + ": 'config.event' must be a mapping");
        }
        Map<String, Object> block = stringKeyed(map, source, "config.event");
        Object on = block.get("on");
        if (on == null) {
            throw new ManifestParseException(source + ": config.event is missing 'on'");
        }
        List<String> names;
        if (on instanceof String single) {
            String text = single.trim();
            if (text.isEmpty()) {
                throw new ManifestParseException(source + ": config.event.on must not be empty");
            }
            names = List.of(text);
        } else {
            names = stringList(on, source, "config.event.on", false);
            if (names.isEmpty()) {
                throw new ManifestParseException(source + ": config.event.on must declare at least one event name");
            }
        }
        return new MacroConfig.Event(names);
    }

    private static MacroConfig.Service parseService(Object value, String source, Consumer<String> warn) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ManifestParseException(source + ": 'config.service' must be a mapping");
        }
        Map<String, Object> block = stringKeyed(map, source, "config.service");
        return new MacroConfig.Service(
                block.get("lifecycle") == null
                        ? null : ScriptMeta.Lifecycle.parse(block.get("lifecycle"), warn),
                optionalBoolean(block, "autostart", source, "config.service.autostart"));
    }

    /**
     * Narrows a raw map to string keys. YAML 1.1 resolves the bare keys
     * {@code on}/{@code off} to booleans; inside config blocks they are
     * normalized back so {@code on: keydown} survives unquoted.
     */
    private static Map<String, Object> stringKeyed(Map<?, ?> map, String source, String field) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                Object key = entry.getKey();
                if (key instanceof Boolean flag) {
                    key = flag ? "on" : "off";
                }
                out.put(String.valueOf(key), entry.getValue());
            }
        }
        return out;
    }

    private static String requiredString(Map<String, Object> document, String field, String source,
                                         List<String> problems) {
        Object value = document.get(field);
        if (value == null) {
            problems.add("missing required field '" + field + "'");
            return "";
        }
        if (value instanceof Boolean) {
            // YAML 1.1 reads bare on/off/yes/no as booleans; a quoted value
            // is required for identity fields.
            problems.add("field '" + field + "' must be a quoted string (YAML parsed it as "
                    + value + ")");
            return "";
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            problems.add("field '" + field + "' must not be empty");
        }
        return text;
    }

    private static void validateName(String name, String source) {
        if (".".equals(name) || "..".equals(name)) {
            throw new ManifestParseException(source + ": invalid name '" + name + "'");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == '/' || c == '\\' || c == '<' || c == '>' || c == ':' || c == '"'
                    || c == '|' || c == '?' || c == '*') {
                throw new ManifestParseException(source + ": name must not contain '"
                        + NameUtil.readable(c) + "'");
            }
        }
    }

    private static String optionalString(Map<String, Object> document, String field) {
        Object value = document.get(field);
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "" : text;
    }

    private static List<String> stringList(Object value, String source, String field,
                                           boolean lowercase) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ManifestParseException(source + ": '" + field + "' must be a list");
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (lowercase) {
                text = text.toLowerCase(Locale.ROOT);
            }
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return List.copyOf(out);
    }

    /** An optional boolean flag; a present-but-wrong-typed value rejects the manifest. */
    private static Boolean optionalBoolean(Map<String, Object> document, String field,
                                           String source, String path) {
        Object value = document.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new ManifestParseException(source + ": '" + path + "' must be true or false");
    }

    /** Human-readable rendering of a rejected character for diagnostics. */
    private static final class NameUtil {
        private static String readable(char c) {
            return c < 0x20 ? "control character U+" + String.format("%04X", (int) c) : String.valueOf(c);
        }
    }
}
