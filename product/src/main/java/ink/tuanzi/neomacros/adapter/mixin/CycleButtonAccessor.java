package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.components.CycleButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the cycling button's private step so a script
 * {@code setControl(id, {cycle})} write steps the live value list the
 * way a press does: vanilla's {@code cycleValue} wraps the index, redraws
 * the label, and fires the value-change callback exactly once. Present
 * with the same signature on every supported node (1.21.6 / 26.x, javap
 * verified), so no version branch is needed.
 */
@Mixin(CycleButton.class)
public interface CycleButtonAccessor {

    @Invoker("cycleValue")
    void neomacros$cycle(int amount);
}
