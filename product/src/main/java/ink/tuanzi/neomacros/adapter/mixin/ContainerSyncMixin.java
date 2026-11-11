package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.FabricInventoryPort;
import ink.tuanzi.neomacros.adapter.InventoryEventHubs;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the inventory sync sources before the menu applies the incoming
 * data, so observers still see the previous state and carry the old and new
 * stacks: single-slot updates, full menu resyncs, and cursor-stack syncs.
 * Menu id 0 covers the player inventory while no container screen is open.
 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class ContainerSyncMixin {

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
    private void neomacros$onSetSlot(net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket packet,
                                     CallbackInfo ci) {
        var menu = FabricInventoryPort.openMenu();
        String kind = menu != null ? FabricInventoryPort.menuKind(menu) : "player";
        InventoryEventHubs.fireSlotUpdate(new InventoryPort.SlotUpdateInfo(
                packet.getContainerId(), kind, packet.getSlot(), false,
                stackOf(packet.getSlot()), stackOf(packet.getItem().copy())));
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"))
    private void neomacros$onContainerContent(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet,
                                              CallbackInfo ci) {
        var menu = FabricInventoryPort.openMenu();
        String kind = menu != null ? FabricInventoryPort.menuKind(menu) : "player";
        InventoryEventHubs.fireSlotUpdate(new InventoryPort.SlotUpdateInfo(
                packet.containerId(), kind, -1, true, null, null));
    }

    @Inject(method = "handleSetCursorItem", at = @At("HEAD"))
    private void neomacros$onSetCursorItem(net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket packet,
                                           CallbackInfo ci) {
        var menu = FabricInventoryPort.openMenu();
        String kind = menu != null ? FabricInventoryPort.menuKind(menu) : "player";
        int containerId = menu != null ? menu.containerId : 0;
        InventoryEventHubs.fireHeldUpdate(new InventoryPort.HeldUpdateInfo(
                containerId, kind, FabricInventoryPort.stackDataOf(packet.contents())));
    }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void neomacros$onTakeItemEntity(net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket packet,
                                            CallbackInfo ci) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || packet.getPlayerId() != player.getId()) {
            return;
        }
        var entity = level.getEntity(packet.getItemId());
        if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) {
            return;
        }
        int amount = packet.getAmount();
        var stack = itemEntity.getItem().copyWithCount(amount);
        InventoryEventHubs.firePickup(new InventoryPort.ItemPickupInfo(
                FabricInventoryPort.stackDataOf(stack), amount));
    }

    /** The current menu stack at one index, or null when out of range. */
    private static ItemStackData stackOf(int index) {
        var menu = FabricInventoryPort.openMenu();
        if (menu == null || index < 0 || index >= menu.getItems().size()) {
            return null;
        }
        return FabricInventoryPort.stackDataOf(menu.getItems().get(index));
    }

    /** Converts one stack to its neutral snapshot (empty maps to null). */
    private static ItemStackData stackOf(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return FabricInventoryPort.stackDataOf(stack);
    }
}
