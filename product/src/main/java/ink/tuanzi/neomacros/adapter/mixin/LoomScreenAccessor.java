package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.screens.inventory.LoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes whether the loom screen currently displays its pattern list, the
 * gate the pattern-selection face checks before applying a pattern.
 */
@Mixin(LoomScreen.class)
public interface LoomScreenAccessor {

    @Accessor("displayPatterns")
    boolean neomacros$displayPatterns();
}
