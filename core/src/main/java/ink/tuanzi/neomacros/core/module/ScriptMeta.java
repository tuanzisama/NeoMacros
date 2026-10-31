package ink.tuanzi.neomacros.core.module;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed module manifest (the {@code meta} export of a script). Unknown or
 * missing values fall back to safe defaults: the name defaults to the file
 * stem, the lifecycle to {@link Lifecycle#WORLD}, and unknown capability
 * names are kept verbatim for the trust gate to reject.
 */
public record ScriptMeta(String name, String version, Lifecycle lifecycle, List<String> permissions) {

    /** Load timing of one script. */
    public enum Lifecycle {
        /** Loaded once at game start. */
        ALWAYS,
        /** Loaded when a world session opens, unloaded when it closes. */
        WORLD,
        /** Loaded only by explicit command. */
        MANUAL;

        /** Parses a manifest lifecycle value, defaulting to WORLD when absent. */
        public static Lifecycle parse(Object value, java.util.function.Consumer<String> warn) {
            if (value == null) {
                return WORLD;
            }
            try {
                return valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warn.accept("unknown lifecycle '" + value + "', using 'world'");
                return WORLD;
            }
        }
    }

    /** Builds the meta from the raw export map of a module. */
    public static ScriptMeta from(Map<String, Object> export, String fallbackName,
                                  java.util.function.Consumer<String> warn) {
        Object name = export.getOrDefault("name", fallbackName);
        Object version = export.getOrDefault("version", "0.0.0");
        List<String> permissions = export.get("permissions") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return new ScriptMeta(String.valueOf(name), String.valueOf(version),
                Lifecycle.parse(export.get("lifecycle"), warn), permissions);
    }
}
