package ink.tuanzi.neomacros.adapter.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import ink.tuanzi.neomacros.adapter.KeyEdgeHub;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the raw keyboard source from the vanilla key-state entry point
 * ({@code KeyMapping.set}), the one hook every input back end funnels
 * through (GLFW on 1.21.6, SDL on 26.x). Keyboard keys only; the hub
 * deduplicates to true down/up transitions. Edges fire only while no screen
 * is open, matching vanilla keybind semantics, so GUI typing never triggers
 * macros.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingHookMixin {

    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            at = @At("HEAD"))
    private static void neomacros$onSet(InputConstants.Key key, boolean down, CallbackInfo ci) {
        if (key.getType() != InputConstants.Type.KEYBOARD) {
            return;
        }
        if (neomacros$screenOpen()) {
            return;
        }
        KeyEdgeHub.fire(key.getName(), key.getValue(), down);
    }

    /** The current screen accessor moved onto Gui in 26.2. */
    private static boolean neomacros$screenOpen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen() != null;
        //?} else {
        /*
        return Minecraft.getInstance().screen != null;
        */
        //?}
    }
}
