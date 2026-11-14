package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.BossbarHub;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Fires boss bar events by re-dispatching the incoming boss packet into the
 * hub with a read-only handler. Dispatch is pure packet reading, so running
 * it a second time beside the vanilla store touches no game state.
 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class BossUpdateMixin {

    @Inject(method = "handleBossUpdate", at = @At("HEAD"))
    private void neomacros$onBossUpdate(ClientboundBossEventPacket packet, CallbackInfo ci) {
        try {
            packet.dispatch(new ClientboundBossEventPacket.Handler() {
                @Override
                public void add(UUID id, Component name, float progress,
                        net.minecraft.world.BossEvent.BossBarColor color,
                        net.minecraft.world.BossEvent.BossBarOverlay overlay,
                        boolean darkenedScreen, boolean worldScreenEffects,
                        boolean createWorldFog) {
                    BossbarHub.fire(new BossbarHub.BossbarData(
                            id.toString(), "add", name.getString(), progress,
                            color.getSerializedName(), overlay.getSerializedName()));
                }

                @Override
                public void remove(UUID id) {
                    BossbarHub.fire(new BossbarHub.BossbarData(
                            id.toString(), "remove", null, null, null, null));
                }

                @Override
                public void updateProgress(UUID id, float progress) {
                    BossbarHub.fire(new BossbarHub.BossbarData(
                            id.toString(), "updateProgress", null, progress, null, null));
                }

                @Override
                public void updateName(UUID id, Component name) {
                    BossbarHub.fire(new BossbarHub.BossbarData(
                            id.toString(), "updateName", name.getString(), null, null,
                            null));
                }

                @Override
                public void updateStyle(UUID id,
                        net.minecraft.world.BossEvent.BossBarColor color,
                        net.minecraft.world.BossEvent.BossBarOverlay overlay) {
                    BossbarHub.fire(new BossbarHub.BossbarData(
                            id.toString(), "updateStyle", null, null,
                            color.getSerializedName(), overlay.getSerializedName()));
                }

                @Override
                public void updateProperties(UUID id, boolean darkenedScreen,
                        boolean worldScreenEffects, boolean createWorldFog) {
                    BossbarHub.fire(new BossbarHub.BossbarData(
                            id.toString(), "updateProperties", null, null, null, null));
                }
            });
        } catch (Exception ignored) {
            // A malformed packet must never break the network handler
        }
    }
}
