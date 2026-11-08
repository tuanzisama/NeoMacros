package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the chat input box so the screen surface can read the text
 * currently being typed.
 */
@Mixin(net.minecraft.client.gui.screens.ChatScreen.class)
public interface ChatScreenAccessor {

    @Accessor("input")
    EditBox neomacros$input();
}
