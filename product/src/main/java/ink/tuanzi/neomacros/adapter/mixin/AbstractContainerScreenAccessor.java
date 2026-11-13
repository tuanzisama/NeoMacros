package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the open container screen's layout origin and hovered slot so the
 * inventory surface can report slot coordinates and the slot under the mouse
 * without duplicating the screen's hit testing.
 */
@Mixin(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int neomacros$leftPos();

    @Accessor("topPos")
    int neomacros$topPos();

    @Accessor("hoveredSlot")
    Slot neomacros$hoveredSlot();
}
