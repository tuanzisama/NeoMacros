package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.FabricInventoryPort;
import ink.tuanzi.neomacros.adapter.InventoryClickHub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the inventory click source: every processed container slot click
 * (manual or script-issued, both go through the game mode) is forwarded to
 * the click hub once the click is applied.
 */
@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public abstract class InventoryClickMixin {

    //? if >=26.1 {
    @Inject(method = "handleContainerInput", at = @At("TAIL"))
    private void neomacros$onContainerInput(int containerId, int slot, int button,
                                            net.minecraft.world.inventory.ContainerInput input,
                                            net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        var menu = FabricInventoryPort.openMenu();
        InventoryClickHub.fire(new InventoryClickHub.ClickInfo(containerId,
                menu != null ? FabricInventoryPort.menuKind(menu) : "player",
                slot, button, input == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE));
    }
    //?} else {
    /*
    @Inject(method = "handleInventoryMouseClick", at = @At("TAIL"))
    private void neomacros$onInventoryMouseClick(int containerId, int slot, int button,
                                                 net.minecraft.world.inventory.ClickType clickType,
                                                 net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        var menu = FabricInventoryPort.openMenu();
        InventoryClickHub.fire(new InventoryClickHub.ClickInfo(containerId,
                menu != null ? FabricInventoryPort.menuKind(menu) : "player",
                slot, button, clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE));
    }
    */
    //?}
}
