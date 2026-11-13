package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the trader screen's selected-offer index and its selection
 * handler so selecting a trade mirrors a click on the offer list: the
 * screen updates the menu hint, moves the input items, and sends the
 * selection request to the server.
 */
@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {

    @Accessor("shopItem")
    void neomacros$shopItem(int index);

    @Invoker("postButtonClick")
    void neomacros$postButtonClick();
}
