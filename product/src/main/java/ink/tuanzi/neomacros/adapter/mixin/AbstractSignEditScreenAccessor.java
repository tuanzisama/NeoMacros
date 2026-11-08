package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the sign block entity behind the sign editor so the screen
 * surface can read the front-face text.
 */
@Mixin(net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {

    @Accessor("sign")
    SignBlockEntity neomacros$sign();
}
