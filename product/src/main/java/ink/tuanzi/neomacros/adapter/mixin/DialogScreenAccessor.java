package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the dialog data and the screen chain behind a dialog screen so
 * the dialog surface can read the open dialog and reopen the underlying
 * screen on close.
 */
@Mixin(net.minecraft.client.gui.screens.dialog.DialogScreen.class)
public interface DialogScreenAccessor {

    @Accessor("dialog")
    Dialog neomacros$dialog();

    @Accessor("previousScreen")
    net.minecraft.client.gui.screens.Screen neomacros$previousScreen();
}
