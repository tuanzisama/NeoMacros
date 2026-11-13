package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.FabricEntityPort;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds the script glow overrides into the two render decisions: whether an
 * entity carries a glow outline and which colour that outline is drawn in.
 * Both injections only read the override table the adapter owns, so an
 * entity with no script override keeps the game's own answer untouched.
 */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void neomacros$applyForcedGlow(CallbackInfoReturnable<Boolean> cir) {
        int mode = FabricEntityPort.neomacros$forceGlowMode((Entity) (Object) this);
        if (mode == 0) {
            cir.setReturnValue(false);
        } else if (mode == 2) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void neomacros$applyGlowColor(CallbackInfoReturnable<Integer> cir) {
        Integer color = FabricEntityPort.neomacros$glowColorOverride((Entity) (Object) this);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
