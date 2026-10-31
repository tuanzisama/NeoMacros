package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.InputScrollHub;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the scroll source: every scroll event the window receives forwards
 * the Y offset to the hub.
 */
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void neomacros$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (yOffset != 0) {
            InputScrollHub.fire(yOffset);
        }
    }
}
