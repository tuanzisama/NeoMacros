package ink.tuanzi.neomacros.core.api;

import java.util.Map;

/**
 * The live value handed to a command handler: typed parsed arguments plus
 * the raw remainder string. Rendered to scripts as a flat handle with the
 * {@code args} property and the {@code raw} property.
 */
public final class CommandContext implements ScriptHandle {

    private final Map<String, Object> args;
    private final String raw;

    CommandContext(Map<String, Object> args, String raw) {
        this.args = args;
        this.raw = raw;
    }

    /** The parsed, typed argument map (slot name → value). */
    public Map<String, Object> getArgs() {
        return args;
    }

    /** The raw remainder string after the command name. */
    public String getRaw() {
        return raw;
    }
}
