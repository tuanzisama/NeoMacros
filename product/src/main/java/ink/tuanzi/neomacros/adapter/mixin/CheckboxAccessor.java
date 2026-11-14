package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.components.Checkbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the checkbox's private selected flag so a script
 * {@code setControl(id, {checked})} write lands in place (no rebuild, no
 * change-event echo — the same semantics as the other widget writes).
 */
@Mixin(Checkbox.class)
public interface CheckboxAccessor {

    @Accessor("selected")
    void neomacros$setSelected(boolean value);
}
