package ink.tuanzi.neomacros.core.module.manifest;

import ink.tuanzi.neomacros.core.module.ScriptMeta;

import java.util.List;

/**
 * The typed behavior block of a manifest ({@code config:}), grouped per
 * category: the keypress trigger definition, the event auto-subscriptions,
 * and the service lifecycle hints. Identity fields stay at the manifest top
 * level; only the {@code config} block may carry behavior.
 *
 * @param keypress keypress trigger definition; required for keypress macros
 * @param event    event auto-subscription definition; required for event macros
 * @param service  service lifecycle hints; optional for services macros
 */
public record MacroConfig(Keypress keypress, Event event, Service service) {

    /** A config block with no declared sections. */
    public static final MacroConfig EMPTY = new MacroConfig(null, null, null);

    /** keypress trigger definition. */
    public record Keypress(String on, String defaultKey, List<String> modifiers) {

        /** Recognized trigger edges in declaration order. */
        public static final List<String> EDGES = List.of("keydown", "keyup", "keypress");

        /** Modifier keys a manifest may require to be held. */
        public static final List<String> MODIFIERS = List.of("ctrl", "shift", "alt");

        /** The declared trigger edge or {@code keypress} when absent. */
        public String onOrDefault() {
            return on == null || on.isEmpty() ? "keypress" : on;
        }

        /** The suggested initial binding; empty when undeclared. */
        public String defaultKeyOrEmpty() {
            return defaultKey == null ? "" : defaultKey;
        }

        /** The modifier keys that must be held for the trigger to fire; never null. */
        public List<String> modifiersOrEmpty() {
            return modifiers == null ? List.of() : modifiers;
        }
    }

    /** event auto-subscription definition. */
    public record Event(List<String> on) {

        /** The subscribed event names; never null. */
        public List<String> onOrEmpty() {
            return on == null ? List.of() : on;
        }
    }

    /** service lifecycle hints. */
    public record Service(ScriptMeta.Lifecycle lifecycle, Boolean autostart) {
    }
}
