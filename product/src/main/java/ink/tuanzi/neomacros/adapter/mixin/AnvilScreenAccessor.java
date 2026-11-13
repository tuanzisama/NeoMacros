package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the anvil rename box so the anvil menu face can read and write
 * the rename text exactly as the screen does, including the responder that
 * recomputes the menu result and cost.
 */
@Mixin(AnvilScreen.class)
public interface AnvilScreenAccessor {

    @Accessor("name")
    EditBox neomacros$name();
}
