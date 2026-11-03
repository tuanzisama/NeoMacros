package ink.tuanzi.neomacros.core.module.manifest;

import ink.tuanzi.neomacros.core.module.ScriptMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The parsed {@code neomacro.{yml,json,json5}} declaration of one script
 * folder: identity metadata in the style of a Tampermonkey userscript header
 * plus the optional entry/binding/lifecycle hints. {@code name}, {@code
 * version}, and {@code author} are required; everything else falls back to
 * safe defaults. The manifest is the single metadata source for folder-based
 * scripts (the in-code {@code meta} export is not read for them).
 *
 * @param name       unique script identity (commands, trust, autoload keys)
 * @param version    script version string
 * @param author     script author
 * @param description short summary, may be empty
 * @param license    license identifier, may be empty
 * @param homepage   project page URL, may be empty
 * @param supportURL issue/support URL, may be empty
 * @param main       entry file relative to the folder; empty uses {@code index.js}
 * @param key        default key binding (vanilla key name, e.g. {@code key.keyboard.j});
 *                   empty means unbound unless the central binding table overrides
 * @param lifecycle  load timing override; null uses the category default
 * @param permissions declared capabilities feeding the trust gate
 * @param events     declared event subscriptions (documentation/validation
 *                   only; runtime wiring stays with the script's events.on)
 * @param autostart  initial auto-load default for services scripts; null
 *                   means unspecified (the load filter treats it as true
 *                   until an explicit enable/disable overrides it)
 */
public record MacroManifest(String name, String version, String author,
                            String description, String license, String homepage, String supportURL,
                            String main, String key, ScriptMeta.Lifecycle lifecycle,
                            List<String> permissions, List<String> events, Boolean autostart) {

    /** Fields recognized in a manifest; anything else draws a warning. */
    private static final List<String> KNOWN_FIELDS = List.of(
            "name", "version", "author", "description", "license", "homepage",
            "supportURL", "main", "key", "lifecycle", "permissions", "events", "autostart");

    /**
     * Builds and validates a manifest from a raw parsed document. Throws
     * {@link ManifestParseException} when a required field is missing or a
     * value is malformed; unknown fields warn but do not reject.
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
                optionalString(document, "key"),
                document.get("lifecycle") == null
                        ? null : ScriptMeta.Lifecycle.parse(document.get("lifecycle"), warn),
                stringList(document.get("permissions"), source, "permissions", true),
                stringList(document.get("events"), source, "events", false),
                optionalBoolean(document, "autostart", source));
    }

    /** The lifecycle for a folder of the given category (override or category default). */
    public ScriptMeta.Lifecycle lifecycleOr(ScriptCategory category) {
        return lifecycle != null ? lifecycle : category.defaultLifecycle();
    }

    /** The declared permissions; never null. */
    public List<String> permissions() {
        return permissions == null ? List.of() : permissions;
    }

    /** The declared event subscriptions; never null. */
    public List<String> events() {
        return events == null ? List.of() : events;
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
                                           String source) {
        Object value = document.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new ManifestParseException(source + ": '" + field + "' must be true or false");
    }

    /** Human-readable rendering of a rejected character for diagnostics. */
    private static final class NameUtil {
        private static String readable(char c) {
            return c < 0x20 ? "control character U+" + String.format("%04X", (int) c) : String.valueOf(c);
        }
    }
}
