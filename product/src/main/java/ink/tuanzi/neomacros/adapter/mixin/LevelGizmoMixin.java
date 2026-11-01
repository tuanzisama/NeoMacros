package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.Draw3DFrameHub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds the 3D draw port into vanilla's per-frame gizmo collection: when the
 * level renderer opens its collector scope the port re-adds its persistent
 * gizmo, and vanilla drains, emits, and submits it with the rest of the
 * frame's gizmos.
 */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class LevelGizmoMixin {

    //? if >=26.1 {
    //? if >=26.2 {
    @Inject(method = "collectPerFrameRenderThreadGizmos", at = @At("RETURN"))
    private void neomacros$addFrameGizmo(CallbackInfoReturnable<net.minecraft.gizmos.Gizmos.TemporaryCollection> cir) {
        Draw3DFrameHub.addFrameGizmo();
    }
    //?} else {
    /*
    @Inject(method = "collectPerFrameGizmos", at = @At("RETURN"))
    private void neomacros$addFrameGizmo(CallbackInfoReturnable<net.minecraft.gizmos.Gizmos.TemporaryCollection> cir) {
        Draw3DFrameHub.addFrameGizmo();
    }
    */
    //?}
    //?}
}
