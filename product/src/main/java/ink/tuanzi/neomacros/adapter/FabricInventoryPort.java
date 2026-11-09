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

    /** The menu currently open on the local player, or null. */
    public static net.minecraft.world.inventory.AbstractContainerMenu openMenu() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.containerMenu : null;
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
        java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();
        extras.put("maxStackSize", stack.getItem().getDefaultMaxStackSize());
        extras.put("damageable", stack.isDamageableItem());
        extras.put("enchantable", stack.isEnchantable());
        extras.put("enchanted", stack.isEnchanted());
        extras.put("edible", stack.get(net.minecraft.core.component.DataComponents.FOOD) != null);
        Integer repairCost = stack.get(net.minecraft.core.component.DataComponents.REPAIR_COST);
        extras.put("repairCost", repairCost != null ? repairCost : 0);
        return new ItemStackData(idOf(stack), stack.getCount(),
                stack.getItemName().getString(), 0, 0, java.util.List.of(),
                java.util.List.of(), extras);
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
            return java.util.Optional.of(new ContainerInfo(
                    screenTitle(), menuKind(menu), menu.getItems().size(), menu.containerId));
        });
    }

    /** The title of the screen above the open menu, or an empty string. */
    static String screenTitle() {
        var screen = FabricLifecyclePort.currentScreen();
        return screen != null && screen.getTitle() != null ? screen.getTitle().getString() : "";
    }

    /** Maps one open menu to its inventory kind label. */
    public static String menuKind(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu) {
            return "player";
        }
        if (menu instanceof net.minecraft.world.inventory.FurnaceMenu
                || menu instanceof net.minecraft.world.inventory.BlastFurnaceMenu
                || menu instanceof net.minecraft.world.inventory.SmokerMenu) {
            return "furnace";
        }
        if (menu instanceof net.minecraft.world.inventory.ChestMenu) {
            return "chest";
        }
        if (menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu) {
            return "shulkerbox";
        }
        if (menu instanceof net.minecraft.world.inventory.CraftingMenu) {
            return "crafting";
        }
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu) {
            return "anvil";
        }
        if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu) {
            return "enchanting";
        }
        if (menu instanceof net.minecraft.world.inventory.GrindstoneMenu) {
            return "grindstone";
        }
        if (menu instanceof net.minecraft.world.inventory.StonecutterMenu) {
            return "stonecutter";
        }
        if (menu instanceof net.minecraft.world.inventory.SmithingMenu) {
            return "smithing";
        }
        if (menu instanceof net.minecraft.world.inventory.CartographyTableMenu) {
            return "cartography";
        }
        if (menu instanceof net.minecraft.world.inventory.LoomMenu) {
            return "loom";
        }
        if (menu instanceof net.minecraft.world.inventory.LecternMenu) {
            return "lectern";
        }
        if (menu instanceof net.minecraft.world.inventory.DispenserMenu) {
            return "dispenser";
        }
        if (menu instanceof net.minecraft.world.inventory.HopperMenu) {
            return "hopper";
        }
        if (menu instanceof net.minecraft.world.inventory.CrafterMenu) {
            return "crafter";
        }
        if (menu instanceof net.minecraft.world.inventory.BeaconMenu) {
            return "beacon";
        }
        if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu) {
            return "brewing";
        }
        if (menu instanceof net.minecraft.world.inventory.MerchantMenu) {
            return "villager";
        }
        if (menu instanceof net.minecraft.world.inventory.HorseInventoryMenu) {
            return "horse";
        }
        return "container";
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

    @Override
    public Optional<ItemStackData> carriedItem() {
        return MainThread.run(() -> {
            var menu = openMenu();
            if (menu == null || menu.getCarried().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(stackDataOf(menu.getCarried()));
        });
    }

    @Override
    public Integer selectedHotbarSlot() {
        LocalPlayer player = player();
        return player != null ? player.getInventory().getSelectedSlot() : null;
    }

    // === actions ===

    /** Dispatches one action on the main thread; failures settle the future. */
    private java.util.concurrent.CompletableFuture<Void> dispatch(Runnable action) {
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /** Dispatches one computing action on the main thread. */
    private <T> java.util.concurrent.CompletableFuture<T> dispatchValue(
            Supplier<T> action) {
        java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                future.complete(action.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Performs one container click with the named action on the open menu
     * (the game-mode path, which both predicts locally and sends the packet;
     * script-issued clicks surface in the inventory click source).
     */
    private void clickContainer(int slot, int button, String action) {
        LocalPlayer player = player();
        var gameMode = Minecraft.getInstance().gameMode;
        var menu = openMenu();
        if (player == null || gameMode == null || menu == null) {
            throw new IllegalStateException("no open container");
        }
        //? if >=26.1 {
        gameMode.handleContainerInput(menu.containerId, slot, button,
                switch (action) {
                    case "quickMove" -> net.minecraft.world.inventory.ContainerInput.QUICK_MOVE;
                    case "clone" -> net.minecraft.world.inventory.ContainerInput.CLONE;
                    case "throw" -> net.minecraft.world.inventory.ContainerInput.THROW;
                    case "swap" -> net.minecraft.world.inventory.ContainerInput.SWAP;
                    case "pickupAll" -> net.minecraft.world.inventory.ContainerInput.PICKUP_ALL;
                    case "quickCraft" -> net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT;
                    default -> net.minecraft.world.inventory.ContainerInput.PICKUP;
                }, player);
        //?} else {
        /* gameMode.handleInventoryMouseClick(menu.containerId, slot, button,
                switch (action) {
                    case "quickMove" -> net.minecraft.world.inventory.ClickType.QUICK_MOVE;
                    case "clone" -> net.minecraft.world.inventory.ClickType.CLONE;
                    case "throw" -> net.minecraft.world.inventory.ClickType.THROW;
                    case "swap" -> net.minecraft.world.inventory.ClickType.SWAP;
                    case "pickupAll" -> net.minecraft.world.inventory.ClickType.PICKUP_ALL;
                    case "quickCraft" -> net.minecraft.world.inventory.ClickType.QUICK_CRAFT;
                    default -> net.minecraft.world.inventory.ClickType.PICKUP;
                }, player); */
        //?}
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> click(int slot, int button, boolean shift) {
        return dispatch(() -> clickContainer(slot, button,
                shift ? "quickMove" : button == 2 ? "clone" : "pickup"));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> drop(int slot, boolean all) {
        return dispatch(() -> clickContainer(slot, all ? 1 : 0, "throw"));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> swapHotbar(int slot, int hotbarIndex) {
        return dispatch(() -> {
            if (hotbarIndex != 40 && (hotbarIndex < 0 || hotbarIndex > 8)) {
                throw new IllegalArgumentException(
                        "hotbar index must be 0-8 or 40 (offhand)");
            }
            clickContainer(slot, hotbarIndex, "swap");
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> close() {
        return dispatch(() -> {
            LocalPlayer player = player();
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            player.closeContainer();
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> closeAndDrop() {
        return dispatch(() -> {
            LocalPlayer player = player();
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            var menu = openMenu();
            if (menu != null && !menu.getCarried().isEmpty()) {
                // clicking outside the screen drops the cursor-held stack
                clickContainer(net.minecraft.world.inventory.AbstractContainerMenu.SLOT_CLICKED_OUTSIDE,
                        0, "pickup");
            }
            player.closeContainer();
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setSelectedHotbarSlot(int index) {
        return dispatch(() -> {
            if (!net.minecraft.world.entity.player.Inventory.isHotbarSlot(index)) {
                throw new IllegalArgumentException("hotbar index must be 0-8");
            }
            LocalPlayer player = player();
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            player.getInventory().setSelectedSlot(index);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> pickupAll(int slot) {
        return dispatch(() -> clickContainer(slot, 0, "pickupAll"));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> dragClick(List<Integer> slots, int button) {
        return dispatch(() -> {
            if (slots == null || slots.isEmpty()) {
                throw new IllegalArgumentException("dragClick needs at least one slot");
            }
            int type = button == 0
                    ? net.minecraft.world.inventory.AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE
                    : net.minecraft.world.inventory.AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY;
            clickContainer(net.minecraft.world.inventory.AbstractContainerMenu.SLOT_CLICKED_OUTSIDE,
                    net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(
                            net.minecraft.world.inventory.AbstractContainerMenu.QUICKCRAFT_HEADER_START, type),
                    "quickCraft");
            for (int slot : slots) {
                clickContainer(slot,
                        net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(
                                net.minecraft.world.inventory.AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, type),
                        "quickCraft");
            }
            clickContainer(net.minecraft.world.inventory.AbstractContainerMenu.SLOT_CLICKED_OUTSIDE,
                    net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(
                            net.minecraft.world.inventory.AbstractContainerMenu.QUICKCRAFT_HEADER_END, type),
                    "quickCraft");
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Integer> quickAll(int slot, int button) {
        return dispatchValue(() -> {
            LocalPlayer player = player();
            var menu = openMenu();
            if (player == null || menu == null) {
                throw new IllegalStateException("no open container");
            }
            if (slot < 0 || slot >= menu.slots.size()) {
                throw new IllegalArgumentException("slot out of range: " + slot);
            }
            var reference = menu.slots.get(slot);
            var cursor = reference.getItem().copy();
            int count = 0;
            for (net.minecraft.world.inventory.Slot candidate : menu.slots) {
                if (candidate.mayPickup(player) && candidate.hasItem()
                        && candidate.container == reference.container
                        && net.minecraft.world.inventory.AbstractContainerMenu.canItemQuickReplace(
                                candidate, cursor, true)) {
                    count += candidate.getItem().getCount();
                    clickContainer(candidate.index, button, "quickMove");
                }
            }
            return count;
        });
    }
}