package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.TabListTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors the latest tab list header/footer pushes into the tracker so world
 * queries can read them without touching rendering internals.
 */
@Mixin(net.minecraft.client.gui.components.PlayerTabOverlay.class)
public abstract class TabListMixin {

    @Inject(method = "setHeader", at = @At("TAIL"))
    private void neomacros$onHeader(Component header, CallbackInfo ci) {
        TabListTracker.header(header != null ? header.getString() : "");
    }

    @Inject(method = "setFooter", at = @At("TAIL"))
    private void neomacros$onFooter(Component footer, CallbackInfo ci) {
        TabListTracker.footer(footer != null ? footer.getString() : "");
    }
}
