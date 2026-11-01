package ink.tuanzi.neomacros.fabric;

import net.minecraft.network.chat.Component;

/**
 * Client-side text lookup for the mod's surfaces: resolves a lang key with
 * the active locale at call time (screens rebuild and commands run on the
 * client thread, so per-call resolution tracks language switches).
 */
public final class UiText {

    private UiText() {
    }

    /** Resolves one lang key with arguments to its display string. */
    public static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
