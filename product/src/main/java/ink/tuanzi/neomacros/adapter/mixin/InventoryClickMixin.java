package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.FabricInventoryPort;
import ink.tuanzi.neomacros.adapter.InventoryClickHub;
import ink.tuanzi.neomacros.adapter.InventoryEventHubs;
import ink.tuanzi.neomacros.port.InventoryPort;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/**
 * Feeds the inventory click source <em>before</em> the click is applied:
 * every container slot click (manual or script-issued, both go through the
 * game mode) reaches the click hub at HEAD, and a cancelled click aborts the
 * call so its packet is never sent and no slot changes. Menu THROW clicks
 * and clicks outside the window then pass through the drop hub, matching the
 * ClickSlot-then-DropSlot order; a cancelled click skips the drop check.
 */
@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public abstract class InventoryClickMixin {

    /** Menu id that marks a click outside the window (vanilla slotClickedOutside). */
    private static final int SLOT_CLICKED_OUTSIDE = -999;

    //? if >=26.1 {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void neomacros$onContainerInput(int containerId, int slot, int button,
                                            net.minecraft.world.inventory.ContainerInput input,
                                            net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        String kind = menuKind();
        String mode = input.name().toLowerCase(Locale.ROOT);
        InventoryPort.ClickSlotInfo click = new InventoryPort.ClickSlotInfo(
                containerId, kind, slot, button, mode,
                input == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
        if (InventoryClickHub.fire(click)) {
            ci.cancel();
            return;
        }
        if (input == net.minecraft.world.inventory.ContainerInput.THROW
                || slot == SLOT_CLICKED_OUTSIDE) {
            if (InventoryEventHubs.fireDrop(new InventoryPort.DropSlotInfo(
                    kind, slot, button == 1))) {
                ci.cancel();
            }
        }
    }
    //?} else {
    /*
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void neomacros$onInventoryMouseClick(int containerId, int slot, int button,
                                                 net.minecraft.world.inventory.ClickType clickType,
                                                 net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        String kind = menuKind();
        String mode = clickType.name().toLowerCase(Locale.ROOT);
        InventoryPort.ClickSlotInfo click = new InventoryPort.ClickSlotInfo(
                containerId, kind, slot, button, mode,
                clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE);
        if (InventoryClickHub.fire(click)) {
            ci.cancel();
            return;
        }
        if (clickType == net.minecraft.world.inventory.ClickType.THROW
                || slot == SLOT_CLICKED_OUTSIDE) {
            if (InventoryEventHubs.fireDrop(new InventoryPort.DropSlotInfo(
                    kind, slot, button == 1))) {
                ci.cancel();
            }
        }
    }
    */
    //?}

    /** The current menu kind label, or {@code player} without a menu. */
    private static String menuKind() {
        var menu = FabricInventoryPort.openMenu();
        return menu != null ? FabricInventoryPort.menuKind(menu) : "player";
    }
}
