package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Inventory queries backed by the local player's container. GUI-free reads
 * (getItem snapshots) answer on the calling thread; the registry lookups for
 * item ids marshal onto the main thread.
 */
public final class FabricInventoryPort implements InventoryPort {

    private LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    @Override
    public Supplier<Optional<ItemStackData>> heldItem() {
        return () -> {
            LocalPlayer player = player();
            if (player == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(stackDataOf(player.getInventory().getSelectedItem()));
        };
    }

    @Override
    public Optional<ItemStackData> slot(int index) {
        LocalPlayer player = player();
        if (player == null || index < 0 || index >= player.getInventory().getContainerSize()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stackDataOf(player.getInventory().getItem(index)));
    }

    @Override
    public Supplier<List<InventoryPort.IndexedStack>> allItems() {
        return () -> {
            LocalPlayer player = player();
            List<InventoryPort.IndexedStack> out = new ArrayList<>();
            if (player == null) {
                return out;
            }
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    out.add(new InventoryPort.IndexedStack(i, stackDataOf(stack)));
                }
            }
            return out;
        };
    }

    @Override
    public int countOf(String item) {
        LocalPlayer player = player();
        if (player == null) {
            return 0;
        }
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && idOf(stack).equals(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static ItemStackData stackDataOf(ItemStack stack) {
        return new ItemStackData(idOf(stack), stack.getCount(),
                stack.getItemName().getString());
    }

    private static String idOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString();
    }

    @Override
    public java.util.Optional<ContainerInfo> openedContainer() {
        return MainThread.run(() -> {
            var menu = player() != null ? player().containerMenu : null;
            if (menu == null) {
                return java.util.Optional.empty();
            }
            String kind = menu.getClass().getSimpleName()
                    .replace("Menu", "").toLowerCase(java.util.Locale.ROOT);
            return java.util.Optional.of(new ContainerInfo(kind, kind,
                    menu.getItems().size()));
        });
    }

    @Override
    public java.util.Optional<ItemStackData> menuSlot(int index) {
        return MainThread.run(() -> {
            var menu = player() != null ? player().containerMenu : null;
            if (menu == null || index < 0 || index >= menu.getItems().size()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.ofNullable(stackDataOf(menu.getItems().get(index)));
        });
    }

    @Override
    public java.util.Map<String, Object> containerState() {
        return MainThread.run(() -> {
            var menu = player() != null ? player().containerMenu : null;
            if (menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu furnace) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("isLit", furnace.isLit());
                state.put("burnProgress", furnace.getBurnProgress());
                state.put("litProgress", furnace.getLitProgress());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu enchanting) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("costs", enchanting.costs.clone());
                state.put("goldCount", enchanting.getGoldCount());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.StonecutterMenu stonecutter) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("selectedRecipeIndex", stonecutter.getSelectedRecipeIndex());
                state.put("recipeCount", stonecutter.getNumberOfVisibleRecipes());
                state.put("hasInput", stonecutter.hasInputItem());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.LoomMenu loom) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("selectedPatternIndex", loom.getSelectedBannerPatternIndex());
                state.put("patternCount", loom.getSelectablePatterns().size());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.BeaconMenu beacon) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("levels", beacon.getLevels());
                state.put("primaryEffect", beacon.getPrimaryEffect() != null
                        ? beacon.getPrimaryEffect().getRegisteredName() : null);
                state.put("secondaryEffect", beacon.getSecondaryEffect() != null
                        ? beacon.getSecondaryEffect().getRegisteredName() : null);
                state.put("hasPayment", beacon.hasPayment());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu brewing) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("fuel", brewing.getFuel());
                state.put("brewingTicks", brewing.getBrewingTicks());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.MerchantMenu merchant) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("traderLevel", merchant.getTraderLevel());
                state.put("traderXp", merchant.getTraderXp());
                state.put("futureTraderXp", merchant.getFutureTraderXp());
                java.util.List<Object> trades = new java.util.ArrayList<>();
                for (net.minecraft.world.item.trading.MerchantOffer offer : merchant.getOffers()) {
                    java.util.Map<String, Object> trade = new java.util.LinkedHashMap<>();
                    if (!offer.getBaseCostA().isEmpty()) {
                        trade.put("baseCostA", stackDataOf(offer.getBaseCostA()));
                    }
                    if (!offer.getCostA().isEmpty()) {
                        trade.put("costA", stackDataOf(offer.getCostA()));
                    }
                    if (!offer.getCostB().isEmpty()) {
                        trade.put("costB", stackDataOf(offer.getCostB()));
                    }
                    if (!offer.getResult().isEmpty()) {
                        trade.put("result", stackDataOf(offer.getResult()));
                    }
                    trade.put("uses", offer.getUses());
                    trade.put("maxUses", offer.getMaxUses());
                    trade.put("demand", offer.getDemand());
                    trades.add(trade);
                }
                state.put("trades", trades);
                return state;
            }
            return java.util.Map.of();
        });
    }
}