package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.SoundEventHub;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires world sound events from the vanilla SoundEngine.play pipeline.
 * play is public and non-final on SoundEngine (javap verified on 26.3);
 * HEAD injection captures all sounds before filtering or spatial processing.
 */
@Mixin(net.minecraft.client.sounds.SoundEngine.class)
public abstract class SoundEnginePlayMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void neomacros$onPlay(SoundInstance sound, CallbackInfoReturnable<net.minecraft.client.sounds.SoundEngine.PlayResult> cir) {
        try {
            var soundId = sound.getIdentifier().toString();
            var x = sound.getX();
            var y = sound.getY();
            var z = sound.getZ();
            var volume = sound.getVolume();
            var pitch = sound.getPitch();
            SoundEventHub.fire(new SoundEventHub.WorldSoundData(
                    soundId, x, y, z, volume, pitch));
        } catch (Exception ignored) {
            // SoundInstance may be in an inconsistent state during shutdown
        }
    }
}
