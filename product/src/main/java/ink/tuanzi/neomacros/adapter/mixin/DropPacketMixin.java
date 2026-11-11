package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.FabricInventoryPort;
import ink.tuanzi.neomacros.adapter.InventoryEventHubs;
import ink.tuanzi.neomacros.port.InventoryPort;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the hotbar drop source: the outgoing DROP_ITEM / DROP_ALL_ITEMS
 * action packet passes the drop hub before it reaches the wire, so a
 * cancelled drop never leaves the client. The source slot follows the
 * vanilla convention ({@code 36 + selected hotbar slot}); menu THROW clicks
 * go through the container-click path instead and are fed by
 * {@link InventoryClickMixin}.
 */
@Mixin(net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl.class)
public abstract class DropPacketMixin {

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void neomacros$onSend(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ServerboundPlayerActionPacket action)) {
            return;
        }
        var kind = action.getAction();
        if (kind != ServerboundPlayerActionPacket.Action.DROP_ITEM
                && kind != ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int slot = 36 + player.getInventory().getSelectedSlot();
        boolean all = kind == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS;
        var menu = FabricInventoryPort.openMenu();
        String menuKind = menu != null ? FabricInventoryPort.menuKind(menu) : "player";
        if (InventoryEventHubs.fireDrop(new InventoryPort.DropSlotInfo(menuKind, slot, all))) {
            ci.cancel();
        }
    }
}
