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

    public static ItemStackData stackDataOf(ItemStack stack) {
        java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();
        extras.put("maxStackSize", stack.getItem().getDefaultMaxStackSize());
        extras.put("damageable", stack.isDamageableItem());
        extras.put("enchantable", stack.isEnchantable());
        extras.put("enchanted", stack.isEnchanted());
        extras.put("edible", stack.get(net.minecraft.core.component.DataComponents.FOOD) != null);
        Integer repairCost = stack.get(net.minecraft.core.component.DataComponents.REPAIR_COST);
        extras.put("repairCost", repairCost != null ? repairCost : 0);
        // Real durability values: item:damage diffs these, and scripts read
        // them as ItemStack.damage/maxDamage (previously hardcoded to 0).
        Integer damage = stack.get(net.minecraft.core.component.DataComponents.DAMAGE);
        Integer maxDamage = stack.get(net.minecraft.core.component.DataComponents.MAX_DAMAGE);
        // The potion type rides in extras so brewing reads can rebuild a
        // stack from a neutral snapshot without carrying MC components.
        var potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (potion != null) {
            potion.potion().ifPresent(holder -> extras.put("potion", holder.getRegisteredName()));
        }
        return new ItemStackData(idOf(stack), stack.getCount(),
                stack.getItemName().getString(),
                damage != null ? damage : 0,
                maxDamage != null ? maxDamage : 0,
                java.util.List.of(), java.util.List.of(), extras);
    }

    /**
     * Rebuilds one MC stack from a neutral snapshot: item and count carry
     * over, and a potion type recorded in the extras goes back onto the
     * {@code POTION_CONTENTS} component so brewing queries see it.
     */
    static net.minecraft.world.item.ItemStack mcStack(ink.tuanzi.neomacros.port.model.ItemStackData data) {
        if (data == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        var location = net.minecraft.resources.Identifier.tryParse(data.id());
        var item = location == null ? null
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(location);
        if (item == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(item, Math.max(0, data.count()));
        if (data.extras().get("potion") instanceof String potionId) {
            var potion = net.minecraft.resources.Identifier.tryParse(potionId);
            var holder = potion == null ? null
                    : net.minecraft.core.registries.BuiltInRegistries.POTION.get(potion).orElse(null);
            if (holder != null) {
                stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        new net.minecraft.world.item.alchemy.PotionContents(holder));
            }
        }
        return stack;
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
            // The creative screen drives its own item-picker menu while the
            // player menu stays open underneath, so the screen decides the
            // kind here.
            var screen = FabricLifecyclePort.currentScreen();
            if (screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
                return java.util.Optional.of(new ContainerInfo(
                        screenTitle(), "creative", menu.getItems().size(), menu.containerId));
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
                // Raw data slots: 0 remaining burn, 1 total burn, 2 cook
                // progress, 3 cook total, all in ticks.
                var data = ((ink.tuanzi.neomacros.adapter.mixin.AbstractFurnaceMenuAccessor) furnace)
                        .neomacros$data();
                state.put("fuelTime", data.get(0));
                state.put("fuelTimeTotal", data.get(1));
                state.put("smeltingTime", data.get(2));
                state.put("smeltingTimeTotal", data.get(3));
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.AnvilMenu anvil) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("cost", anvil.getCost());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu enchanting) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("costs", enchanting.costs.clone());
                state.put("goldCount", enchanting.getGoldCount());
                state.put("enchantments", enchantmentClues(enchanting));
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.StonecutterMenu stonecutter) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("selectedRecipeIndex", stonecutter.getSelectedRecipeIndex());
                state.put("recipeCount", stonecutter.getNumberOfVisibleRecipes());
                state.put("hasInput", stonecutter.hasInputItem());
                state.put("recipes", stonecutterResults(stonecutter));
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.GrindstoneMenu) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("simulatedXp", simulatedGrindXp(menu));
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
                // The screen keeps the selection the user (or a script)
                // highlighted until it is applied; it wins over the synced
                // menu value so a select reads back immediately.
                net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> primary =
                        beacon.getPrimaryEffect();
                net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> secondary =
                        beacon.getSecondaryEffect();
                var screen = FabricLifecyclePort.currentScreen();
                if (screen instanceof net.minecraft.client.gui.screens.inventory.BeaconScreen) {
                    var layout = (ink.tuanzi.neomacros.adapter.mixin.BeaconScreenAccessor) screen;
                    if (layout.neomacros$primary() != null) {
                        primary = layout.neomacros$primary();
                    }
                    if (layout.neomacros$secondary() != null) {
                        secondary = layout.neomacros$secondary();
                    }
                }
                state.put("primaryEffect", primary != null ? effectId(primary) : null);
                state.put("secondaryEffect", secondary != null ? effectId(secondary) : null);
                state.put("hasPayment", beacon.hasPayment());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu brewing) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("fuel", brewing.getFuel());
                state.put("brewingTicks", brewing.getBrewingTicks());
                //? if >=26.3 {
                state.put("totalFuel", brewing.getTotalFuel());
                state.put("totalBrewingTicks", brewing.getTotalBrewingTicks());
                //?} else {
                /* state.put("totalFuel",
                        net.minecraft.world.level.block.entity.BrewingStandBlockEntity.FUEL_USES);
                state.put("totalBrewingTicks",
                        net.minecraft.world.item.alchemy.PotionBrewing.BREWING_TIME_SECONDS * 20); */
                //?}
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
                state.put("canRestock", merchant.canRestock());
                state.put("leveled", merchant.showProgressBar());
                return state;
            }
            if (menu instanceof net.minecraft.world.inventory.HorseInventoryMenu) {
                java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
                var mount = ((ink.tuanzi.neomacros.adapter.mixin.HorseMenuAccessor) menu)
                        .neomacros$mount();
                if (mount instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse) {
                    state.put("canBeSaddled", horse.canUseSlot(
                            net.minecraft.world.entity.EquipmentSlot.SADDLE));
                    state.put("isSaddled", !horse.getItemBySlot(
                            net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty());
                    state.put("hasArmorSlot", horse.canUseSlot(
                            net.minecraft.world.entity.EquipmentSlot.BODY));
                    boolean chested = horse
                            instanceof net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
                    state.put("hasChest", chested
                            && ((net.minecraft.world.entity.animal.equine.AbstractChestedHorse) horse)
                                    .hasChest());
                    state.put("inventorySize", chested
                            ? ((net.minecraft.world.entity.animal.equine.AbstractChestedHorse) horse)
                                    .getInventoryColumns() * 3
                            : 0);
                    state.put("horse", horse.getUUID().toString());
                }
                return state;
            }
            return java.util.Map.of();
        });
    }

    /**
     * The enchanting table's three clues as {@code {id, level}} entries in
     * option order; an unresolved clue reads as {@code null}.
     */
    private static java.util.List<ink.tuanzi.neomacros.port.model.EnchantmentData> enchantmentClues(
            net.minecraft.world.inventory.EnchantmentMenu menu) {
        var connection = Minecraft.getInstance().getConnection();
        net.minecraft.core.Registry<net.minecraft.world.item.enchantment.Enchantment> registry =
                connection == null ? null
                        : connection.registryAccess().lookupOrThrow(
                                net.minecraft.core.registries.Registries.ENCHANTMENT);
        java.util.List<ink.tuanzi.neomacros.port.model.EnchantmentData> out =
                new java.util.ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            final int option = i;
            if (registry == null) {
                out.add(null);
                continue;
            }
            out.add(registry.get(menu.enchantClue[option])
                    .map(holder -> new ink.tuanzi.neomacros.port.model.EnchantmentData(
                            holder.getRegisteredName(), menu.levelClue[option]))
                    .orElse(null));
        }
        return out;
    }

    /** The stonecutter's visible recipe results, in selection-index order. */
    private static java.util.List<ItemStackData> stonecutterResults(
            net.minecraft.world.inventory.StonecutterMenu menu) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return java.util.List.of();
        }
        var context = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
        java.util.List<ItemStackData> out = new java.util.ArrayList<>();
        for (var entry : menu.getVisibleRecipes().entries()) {
            out.add(stackDataOf(entry.recipe().optionDisplay().resolveForFirstStack(context)));
        }
        return out;
    }

    /** The lower bound of the experience a grindstone run would award. */
    private static int simulatedGrindXp(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu.getItems().size() < 2) {
            return 0;
        }
        int total = grindXp(menu.getItems().get(0)) + grindXp(menu.getItems().get(1));
        return total > 0 ? (int) Math.ceil(total / 2.0) : 0;
    }

    /** Sums the minimum cost of every non-curse enchantment on one stack. */
    private static int grindXp(net.minecraft.world.item.ItemStack stack) {
        int total = 0;
        var enchants = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getEnchantmentsForCrafting(stack);
        for (var entry : enchants.entrySet()) {
            if (!entry.getKey().is(net.minecraft.tags.EnchantmentTags.CURSE)) {
                total += entry.getKey().value().getMinCost(entry.getIntValue());
            }
        }
        return total;
    }

    @Override
    public Optional<ItemStackData> carriedItem() {
        return MainThread.run(() -> {
            // The creative screen carries its cursor on its own menu, not
            // the player menu underneath it.
            var screen = FabricLifecyclePort.currentScreen();
            if (screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative) {
                var carried = creative.getMenu().getCarried();
                return carried.isEmpty()
                        ? Optional.empty()
                        : Optional.of(stackDataOf(carried));
            }
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

    // === container common face ===

    @Override
    public java.util.Optional<Object> rawContainer() {
        return MainThread.run(() -> {
            LocalPlayer player = player();
            return java.util.Optional.ofNullable(player != null ? player.containerMenu : null);
        });
    }

    @Override
    public java.util.Optional<Integer> slotUnderMouse() {
        return MainThread.run(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container)) {
                return java.util.Optional.empty();
            }
            var slot = ((ink.tuanzi.neomacros.adapter.mixin.AbstractContainerScreenAccessor) container)
                    .neomacros$hoveredSlot();
            if (slot == null) {
                return java.util.Optional.empty();
            }
            int index = container.getMenu().slots.indexOf(slot);
            if (index < 0) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(index);
        });
    }

    @Override
    public java.util.Optional<ink.tuanzi.neomacros.port.model.Pos2d> slotPos(int slot) {
        return MainThread.run(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container)) {
                return java.util.Optional.empty();
            }
            var slots = container.getMenu().slots;
            if (slot < 0 || slot >= slots.size()) {
                return java.util.Optional.empty();
            }
            var layout = (ink.tuanzi.neomacros.adapter.mixin.AbstractContainerScreenAccessor) container;
            var target = slots.get(slot);
            return java.util.Optional.of(new ink.tuanzi.neomacros.port.model.Pos2d(
                    layout.neomacros$leftPos() + target.x, layout.neomacros$topPos() + target.y));
        });
    }

    @Override
    public java.util.Optional<String> regionOf(int slot) {
        return MainThread.run(() -> {
            LocalPlayer player = player();
            var menu = player != null ? player.containerMenu : null;
            if (menu == null) {
                return java.util.Optional.empty();
            }
            int size = menu.getItems().size();
            if (slot < 0 || slot >= size) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.ofNullable(regionName(menuKind(menu), size, slot));
        });
    }

    /**
     * Maps one menu slot onto its region name: the player rows sit at the end
     * of every menu (last 36), and the leading container rows are keyed per
     * menu family. The player's own menu reports its fixed 46-slot layout.
     * Names follow the established InvMapId vocabulary so region strings
     * carry across tooling unchanged.
     */
    private static String regionName(String kind, int size, int slot) {
        if ("player".equals(kind)) {
            if (slot == 0) {
                return "craft_out";
            }
            if (slot <= 4) {
                return "crafting_in";
            }
            if (slot == 5) {
                return "helmet";
            }
            if (slot == 6) {
                return "chestplate";
            }
            if (slot == 7) {
                return "leggings";
            }
            if (slot == 8) {
                return "boots";
            }
            if (slot <= 35) {
                return "main";
            }
            if (slot <= 44) {
                return "hotbar";
            }
            // The creative screen appends its delete slot past the offhand.
            if (slot == 46 && size > 46) {
                return "delete";
            }
            return "offhand";
        }
        if (size > 36) {
            if (slot >= size - 9) {
                return "hotbar";
            }
            if (slot >= size - 36) {
                return "main";
            }
        }
        return switch (kind) {
            case "crafting" -> slot == size - 46 ? "output" : "input";
            case "furnace" -> slot == size - 37 ? "output" : slot == size - 38 ? "fuel" : "input";
            case "brewing" -> slot == size - 37 ? "fuel" : slot == size - 38 ? "input" : "output";
            case "enchanting" -> slot == size - 37 ? "lapis" : "item";
            case "loom" -> switch (slot - (size - 40)) {
                case 0 -> "banner";
                case 1 -> "dye";
                case 2 -> "pattern";
                default -> "output";
            };
            case "stonecutter", "anvil", "villager", "smithing", "grindstone", "cartography" ->
                    slot == size - 37 ? "output" : "input";
            case "beacon" -> "slot";
            case "horse" -> {
                if (slot == 0) {
                    yield "saddle";
                }
                if (slot == 1) {
                    yield "armor";
                }
                yield "container";
            }
            default -> "container";
        };
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> open(String kind) {
        return dispatch(() -> {
            LocalPlayer player = player();
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            var screen = FabricLifecyclePort.currentScreen();
            if ("player".equals(kind)) {
                if (!(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
                    Minecraft.getInstance().gui.setScreen(
                            new net.minecraft.client.gui.screens.inventory.InventoryScreen(player));
                }
                return;
            }
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
                return;
            }
            throw new IllegalStateException(
                    "container screen is closed; reopen the container before opening this handle");
        });
    }

    @Override
    public Optional<ItemStackData> playerMenuSlot(int index) {
        return MainThread.run(() -> {
            LocalPlayer player = player();
            var menu = player != null ? player.inventoryMenu : null;
            if (menu == null || index < 0 || index >= menu.getItems().size()) {
                return Optional.empty();
            }
            var stack = menu.getItems().get(index);
            if (stack.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(stackDataOf(stack));
        });
    }

    @Override
    public int craftingWidth() {
        return craftingDimension(true);
    }

    @Override
    public int craftingHeight() {
        return craftingDimension(false);
    }

    /** Reads the open crafting menu's reported grid size, or 0 without one. */
    private int craftingDimension(boolean width) {
        Integer value = MainThread.run(() -> {
            var menu = player() != null ? player().containerMenu : null;
            if (!(menu instanceof net.minecraft.world.inventory.AbstractCraftingMenu crafting)) {
                return 0;
            }
            return width ? crafting.getGridWidth() : crafting.getGridHeight();
        });
        return value != null ? value : 0;
    }

    @Override
    public Boolean recipeBookOpen() {
        return MainThread.run(() -> {
            LocalPlayer player = player();
            var screen = FabricLifecyclePort.currentScreen();
            if (player == null
                    || !(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen)
                    || !(player.containerMenu instanceof net.minecraft.world.inventory.RecipeBookMenu menu)) {
                return null;
            }
            return player.getRecipeBook().isOpen(menu.getRecipeBookType());
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setRecipeBookOpen(boolean open) {
        return dispatch(() -> {
            LocalPlayer player = player();
            var screen = FabricLifecyclePort.currentScreen();
            if (player == null
                    || !(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen)
                    || !(player.containerMenu instanceof net.minecraft.world.inventory.RecipeBookMenu menu)) {
                return;
            }
            var book = player.getRecipeBook();
            var type = menu.getRecipeBookType();
            if (book.isOpen(type) == open) {
                return;
            }
            // Mirror the recipe-book button: persist the setting, tell the
            // server, then re-run the screen init so the widget and the
            // container origin pick the new state up.
            book.setOpen(type, open);
            player.connection.send(new net.minecraft.network.protocol.game
                    .ServerboundRecipeBookChangeSettingsPacket(type, open, book.isFiltering(type)));
            //? if >=26.1 {
            screen.resize(screen.width, screen.height);
            //?} else {
            /* screen.resize(Minecraft.getInstance(), screen.width, screen.height); */
            //?}
        });
    }

    // === container menu face ===

    @Override
    public java.util.Optional<String> anvilItemName() {
        return java.util.Optional.ofNullable(MainThread.run(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen anvil)) {
                return null;
            }
            return ((ink.tuanzi.neomacros.adapter.mixin.AnvilScreenAccessor) anvil)
                    .neomacros$name().getValue();
        }));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setAnvilItemName(String name) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen anvil)) {
                return;
            }
            ((ink.tuanzi.neomacros.adapter.mixin.AnvilScreenAccessor) anvil)
                    .neomacros$name().setValue(name == null ? "" : name);
        });
    }

    @Override
    public Boolean isFuel(ItemStackData stack) {
        if (stack == null) {
            return null;
        }
        return MainThread.run(() -> {
            var menu = openMenu();
            if (!(menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu furnace)) {
                return null;
            }
            return ((ink.tuanzi.neomacros.adapter.mixin.AbstractFurnaceMenuAccessor) furnace)
                    .neomacros$isFuel(mcStack(stack));
        });
    }

    @Override
    public Boolean isSmeltable(ItemStackData stack) {
        if (stack == null) {
            return null;
        }
        return MainThread.run(() -> {
            var menu = openMenu();
            if (!(menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu furnace)) {
                return null;
            }
            return ((ink.tuanzi.neomacros.adapter.mixin.AbstractFurnaceMenuAccessor) furnace)
                    .neomacros$canSmelt(mcStack(stack));
        });
    }

    @Override
    public java.util.Map<String, Integer> fuelValues() {
        //? if <26.3 {
        /* return MainThread.run(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return java.util.Map.<String, Integer>of();
            }
            var values = level.fuelValues();
            java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
            for (net.minecraft.world.item.Item item : values.fuelItems()) {
                out.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),
                        values.burnDuration(new net.minecraft.world.item.ItemStack(item)));
            }
            return out;
        }); */
        //?}
        //? if >=26.3 {
        return MainThread.run(FabricInventoryPort::fuelValuesFromComponents);
        //?}
    }

    @Override
    public Boolean isBrewablePotion(ItemStackData potion) {
        if (potion == null) {
            return null;
        }
        return MainThread.run(() -> {
            var contents = mcStack(potion)
                    .get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (contents == null || contents.potion().isEmpty()) {
                return Boolean.FALSE;
            }
            //? if >=26.3 {
            var recipes = brewingRecipes();
            if (recipes.isEmpty()) {
                return null;
            }
            return recipes.stream().anyMatch(recipe -> {
                var output = recipe.getOutput().create()
                        .get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                return output != null && output.potion().equals(contents.potion());
            });
            //?} else {
            /* var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return null;
            }
            return connection.potionBrewing().isBrewablePotion(contents.potion().get()); */
            //?}
        });
    }

    @Override
    public Boolean isValidIngredient(ItemStackData ingredient) {
        if (ingredient == null) {
            return null;
        }
        return MainThread.run(() -> {
            var stack = mcStack(ingredient);
            //? if >=26.3 {
            var recipes = brewingRecipes();
            if (recipes.isEmpty()) {
                return null;
            }
            return recipes.stream().anyMatch(recipe -> recipe.getReagent().test(stack));
            //?} else {
            /* var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return null;
            }
            return connection.potionBrewing().isIngredient(stack); */
            //?}
        });
    }

    @Override
    public Boolean isValidRecipe(ItemStackData potion, ItemStackData ingredient) {
        if (potion == null || ingredient == null) {
            return null;
        }
        return MainThread.run(() -> {
            var potionStack = mcStack(potion);
            var ingredientStack = mcStack(ingredient);
            //? if >=26.3 {
            var recipes = brewingRecipes();
            if (recipes.isEmpty()) {
                return null;
            }
            var input = new net.minecraft.world.item.crafting.BrewingInput(
                    potionStack, ingredientStack);
            return recipes.stream().anyMatch(recipe -> recipe.matches(input));
            //?} else {
            /* var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return null;
            }
            return connection.potionBrewing().hasMix(potionStack, ingredientStack); */
            //?}
        });
    }

    @Override
    public java.util.Optional<ItemStackData> previewPotion(
            ItemStackData potion, ItemStackData ingredient) {
        if (potion == null || ingredient == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(MainThread.run(() -> {
            var potionStack = mcStack(potion);
            var ingredientStack = mcStack(ingredient);
            net.minecraft.world.item.ItemStack result;
            //? if >=26.3 {
            var recipes = brewingRecipes();
            if (recipes.isEmpty()) {
                return null;
            }
            var input = new net.minecraft.world.item.crafting.BrewingInput(
                    potionStack, ingredientStack);
            result = recipes.stream().filter(recipe -> recipe.matches(input))
                    .findFirst()
                    .map(recipe -> recipe.assemble(input))
                    .orElse(potionStack);
            //?} else {
            /* var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return null;
            }
            result = connection.potionBrewing().mix(ingredientStack, potionStack); */
            //?}
            return stackDataOf(result);
        }));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> selectBeaconPrimaryEffect(String id) {
        return dispatchValue(() -> selectBeaconEffect(id, false));
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> selectBeaconSecondaryEffect(String id) {
        return dispatchValue(() -> selectBeaconEffect(id, true));
    }

    /**
     * Mirrors the beacon screen's own effect search for one selection slot:
     * the primary searches the tiers the level unlocks up to the second, the
     * secondary needs level 3 or above and otherwise mirrors the primary.
     * The change stays on the screen until the effects are applied.
     */
    private static boolean selectBeaconEffect(String id, boolean secondary) {
        if (id == null) {
            return false;
        }
        var screen = FabricLifecyclePort.currentScreen();
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.BeaconScreen beacon)
                || !(openMenu() instanceof net.minecraft.world.inventory.BeaconMenu menu)) {
            return false;
        }
        var layout = (ink.tuanzi.neomacros.adapter.mixin.BeaconScreenAccessor) beacon;
        int level = menu.getLevels();
        if (!secondary) {
            for (int tier = 0; tier < Math.min(level, 2); tier++) {
                var match = beaconEffect(tier, id);
                if (match != null) {
                    layout.neomacros$primary(match);
                    return true;
                }
            }
            return false;
        }
        if (level < 3) {
            return false;
        }
        var primary = layout.neomacros$primary();
        if (primary != null && id.equals(effectId(primary))) {
            layout.neomacros$secondary(primary);
            return true;
        }
        int tiers = Math.min(level,
                net.minecraft.world.level.block.entity.BeaconBlockEntity.BEACON_EFFECTS.size());
        for (int tier = 0; tier < tiers; tier++) {
            var match = beaconEffect(tier, id);
            if (match == null) {
                continue;
            }
            if (primary != null && "minecraft:regeneration".equals(effectId(match))) {
                layout.neomacros$primary(match);
            } else {
                layout.neomacros$primary(match);
                layout.neomacros$secondary(match);
                return true;
            }
        }
        return false;
    }

    /** One effect of the given beacon tier matching the registry id. */
    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> beaconEffect(
            int tier, String id) {
        var tiers = net.minecraft.world.level.block.entity.BeaconBlockEntity.BEACON_EFFECTS;
        if (tier < 0 || tier >= tiers.size()) {
            return null;
        }
        return tiers.get(tier).stream()
                .filter(effect -> id.equals(effectId(effect)))
                .findFirst()
                .orElse(null);
    }

    /** The registry id of one effect holder, or an empty string when unregistered. */
    private static String effectId(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> holder) {
        var key = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
        return key == null ? "" : key.toString();
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> applyBeaconEffects() {
        return dispatchValue(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.BeaconScreen beacon)
                    || !(openMenu() instanceof net.minecraft.world.inventory.BeaconMenu menu)
                    || !menu.hasPayment()) {
                return Boolean.FALSE;
            }
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return Boolean.FALSE;
            }
            var layout = (ink.tuanzi.neomacros.adapter.mixin.BeaconScreenAccessor) beacon;
            connection.send(new net.minecraft.network.protocol.game.ServerboundSetBeaconPacket(
                    java.util.Optional.ofNullable(layout.neomacros$primary()),
                    java.util.Optional.ofNullable(layout.neomacros$secondary())));
            var player = player();
            if (player != null) {
                player.closeContainer();
            }
            return Boolean.TRUE;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> doEnchant(int index) {
        return dispatchValue(() -> {
            var menu = openMenu();
            if (!(menu instanceof net.minecraft.world.inventory.EnchantmentMenu)) {
                return Boolean.FALSE;
            }
            var player = player();
            var gameMode = Minecraft.getInstance().gameMode;
            if (player == null || gameMode == null || !menu.clickMenuButton(player, index)) {
                return Boolean.FALSE;
            }
            gameMode.handleInventoryButtonClick(menu.containerId, index);
            return Boolean.TRUE;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> selectStonecutterRecipe(int index) {
        return dispatch(() -> {
            var menu = openMenu();
            if (!(menu instanceof net.minecraft.world.inventory.StonecutterMenu stonecutter)) {
                return;
            }
            if (index < 0 || index >= stonecutter.getNumberOfVisibleRecipes()) {
                return;
            }
            var player = player();
            var gameMode = Minecraft.getInstance().gameMode;
            if (player == null || gameMode == null) {
                return;
            }
            stonecutter.clickMenuButton(player, index);
            gameMode.handleInventoryButtonClick(menu.containerId, index);
        });
    }

    // === container specialized face ===

    @Override
    public List<ink.tuanzi.neomacros.port.InventoryPort.CreativeTab> creativeTabs() {
        return MainThread.run(() -> {
            List<ink.tuanzi.neomacros.port.InventoryPort.CreativeTab> out = new ArrayList<>();
            for (net.minecraft.world.item.CreativeModeTab tab
                    : net.minecraft.world.item.CreativeModeTabs.allTabs()) {
                out.add(new ink.tuanzi.neomacros.port.InventoryPort.CreativeTab(
                        tab.getDisplayName().getString(), tab.getType().name()));
            }
            return out;
        });
    }

    @Override
    public List<ItemStackData> creativeShownItems() {
        return MainThread.run(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative)) {
                return List.of();
            }
            List<ItemStackData> out = new ArrayList<>();
            for (ItemStack stack : creative.getMenu().items) {
                out.add(stackDataOf(stack));
            }
            return out;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeScrollBy(double amount) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative)) {
                return;
            }
            var layout = (ink.tuanzi.neomacros.adapter.mixin.CreativeScreenAccessor) creative;
            if (!layout.neomacros$canScroll()) {
                return;
            }
            float target = clamp01(layout.neomacros$scrollOffs() + (float) amount);
            layout.neomacros$scrollOffs(target);
            creative.getMenu().scrollTo(target);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeScrollTo(double position) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative)) {
                return;
            }
            var layout = (ink.tuanzi.neomacros.adapter.mixin.CreativeScreenAccessor) creative;
            if (!layout.neomacros$canScroll()) {
                return;
            }
            float target = clamp01(position);
            layout.neomacros$scrollOffs(target);
            creative.getMenu().scrollTo(target);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeSearch(String text) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative)) {
                return;
            }
            var layout = (ink.tuanzi.neomacros.adapter.mixin.CreativeScreenAccessor) creative;
            var tab = layout.neomacros$selectedTab();
            if (tab == null
                    || tab.getType() != net.minecraft.world.item.CreativeModeTab.Type.SEARCH) {
                return;
            }
            var box = layout.neomacros$searchBox();
            if (box == null) {
                return;
            }
            box.setValue(text == null ? "" : text);
            layout.neomacros$refreshSearchResults();
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeSelectTab(int index) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative)) {
                return;
            }
            var tabs = net.minecraft.world.item.CreativeModeTabs.allTabs();
            if (index < 0 || index >= tabs.size()) {
                return;
            }
            ((ink.tuanzi.neomacros.adapter.mixin.CreativeScreenAccessor) creative)
                    .neomacros$selectTab(tabs.get(index));
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeDestroyHeld() {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative)) {
                return;
            }
            creative.getMenu().setCarried(ItemStack.EMPTY);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeDestroyAll() {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) {
                return;
            }
            var player = player();
            var gameMode = Minecraft.getInstance().gameMode;
            if (player == null || gameMode == null) {
                return;
            }
            var inventoryMenu = player.inventoryMenu;
            for (int i = 0; i < inventoryMenu.getItems().size(); i++) {
                inventoryMenu.getSlot(i).set(ItemStack.EMPTY);
                gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, i);
            }
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeSetCursor(ItemStackData stack) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen creative)) {
                return;
            }
            creative.getMenu().setCarried(mcStack(stack));
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeSaveHotbar(int index) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)
                    || index < 0 || index > 8) {
                return;
            }
            net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
                    .handleHotbarLoadOrSave(Minecraft.getInstance(), index, false, true);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> creativeRestoreHotbar(int index) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen
                    instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)
                    || index < 0 || index > 8) {
                return;
            }
            net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
                    .handleHotbarLoadOrSave(Minecraft.getInstance(), index, true, false);
        });
    }

    @Override
    public List<ItemStackData> creativeSavedHotbar(int index) {
        return MainThread.run(() -> {
            if (index < 0 || index > 8) {
                return List.of();
            }
            var mc = Minecraft.getInstance();
            var connection = mc.getConnection();
            if (connection == null) {
                return List.of();
            }
            List<ItemStackData> out = new ArrayList<>();
            for (ItemStack stack : mc.getHotbarManager().get(index)
                    .load(connection.registryAccess())) {
                out.add(stackDataOf(stack));
            }
            return out;
        });
    }

    @Override
    public List<String> loomPatterns() {
        return MainThread.run(() -> {
            var menu = openMenu();
            if (!(menu instanceof net.minecraft.world.inventory.LoomMenu loom)) {
                return List.of();
            }
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return List.of();
            }
            var registry = connection.registryAccess().lookupOrThrow(
                    net.minecraft.core.registries.Registries.BANNER_PATTERN);
            List<String> out = new ArrayList<>();
            for (var holder : loom.getSelectablePatterns()) {
                var key = registry.getKey(holder.value());
                out.add(key != null ? key.toString() : "");
            }
            return out;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> loomSelectPattern(int index) {
        return dispatchValue(() -> {
            var menu = openMenu();
            if (!(menu instanceof net.minecraft.world.inventory.LoomMenu loom)
                    || index < 0 || index >= loom.getSelectablePatterns().size()) {
                return Boolean.FALSE;
            }
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.LoomScreen)
                    || !((ink.tuanzi.neomacros.adapter.mixin.LoomScreenAccessor) screen)
                            .neomacros$displayPatterns()) {
                return Boolean.FALSE;
            }
            var player = player();
            var gameMode = Minecraft.getInstance().gameMode;
            if (player == null || gameMode == null || !loom.clickMenuButton(player, index)) {
                return Boolean.FALSE;
            }
            gameMode.handleInventoryButtonClick(menu.containerId, index);
            return Boolean.TRUE;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> selectVillagerTrade(int index) {
        return dispatch(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen)
                    || !(openMenu() instanceof net.minecraft.world.inventory.MerchantMenu menu)
                    || index < 0 || index >= menu.getOffers().size()) {
                return;
            }
            var layout = (ink.tuanzi.neomacros.adapter.mixin.MerchantScreenAccessor) screen;
            layout.neomacros$shopItem(index);
            layout.neomacros$postButtonClick();
        });
    }

    /** Clamps a scroll fraction into {@code 0..1}. */
    private static float clamp01(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }

    //? if >=26.3 {
    /** The vanilla brewing recipe set, generated once from the bundled provider. */
    private static volatile java.util.List<net.minecraft.world.item.crafting.BrewingRecipe> brewingRecipeCache;

    private static java.util.List<net.minecraft.world.item.crafting.BrewingRecipe> brewingRecipes() {
        var cached = brewingRecipeCache;
        if (cached != null) {
            return cached;
        }
        java.util.List<net.minecraft.world.item.crafting.BrewingRecipe> out = new java.util.ArrayList<>();
        try {
            new net.minecraft.data.recipes.packs.VanillaBrewingProvider(
                    new net.minecraft.data.recipes.RecipeOutput() {
                        @Override
                        public void accept(
                                net.minecraft.resources.ResourceKey<
                                        net.minecraft.world.item.crafting.Recipe<?>> key,
                                net.minecraft.world.item.crafting.Recipe<?> recipe,
                                net.minecraft.advancements.AdvancementHolder advancement) {
                            if (recipe instanceof net.minecraft.world.item.crafting.BrewingRecipe brewing) {
                                out.add(brewing);
                            }
                        }

                        @Override
                        public net.minecraft.advancements.Advancement.Builder advancement() {
                            return net.minecraft.advancements.Advancement.Builder.recipeAdvancement();
                        }

                        @Override
                        public <S> net.minecraft.core.HolderGetter<S> lookup(
                                net.minecraft.resources.ResourceKey<
                                        ? extends net.minecraft.core.Registry<? extends S>> key) {
                            throw new UnsupportedOperationException(
                                    "brewing recipes carry no registry lookups");
                        }

                        @Override
                        public <S> java.util.stream.Stream<net.minecraft.core.Holder.Reference<S>>
                                listContextElements(net.minecraft.resources.ResourceKey<
                                        ? extends net.minecraft.core.Registry<? extends S>> key) {
                            throw new UnsupportedOperationException(
                                    "brewing recipes carry no registry lookups");
                        }
                    }) {
            }.buildRecipes();
        } catch (RuntimeException e) {
            return java.util.List.of();
        }
        brewingRecipeCache = java.util.List.copyOf(out);
        return brewingRecipeCache;
    }

    /**
     * Resolves fuel burn ticks from item components, which on 26.3 carry a
     * data-driven provider tree that needs a loot context. Remote servers
     * expose no server level to build that context with, so they read back
     * as an empty map.
     */
    private static java.util.Map<String, Integer> fuelValuesFromComponents() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return java.util.Map.of();
        }
        try {
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(server.overworld())
                    .withParameter(
                            net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE,
                            net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState())
                    .withOptionalParameter(
                            net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                            net.minecraft.world.phys.Vec3.ZERO)
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
            var context = new net.minecraft.world.level.storage.loot.LootContext.Builder(params)
                    .create(java.util.Optional.empty());
            java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
            for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                var fuel = item.components().get(net.minecraft.core.component.DataComponents.COOKING_FUEL);
                if (fuel == null) {
                    continue;
                }
                int ticks = fuel.burnTime().get(context, 0);
                if (ticks > 0) {
                    out.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),
                            ticks);
                }
            }
            return out;
        } catch (RuntimeException e) {
            return java.util.Map.of();
        }
    }
    //?}

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

    @Override
    public void onClickSlot(java.util.function.Function<ClickSlotInfo, Boolean> sink) {
        InventoryClickHub.register(sink);
    }

    @Override
    public void onSlotUpdate(java.util.function.Consumer<SlotUpdateInfo> sink) {
        InventoryEventHubs.onSlotUpdate(sink);
    }

    @Override
    public void onHeldUpdate(java.util.function.Consumer<HeldUpdateInfo> sink) {
        InventoryEventHubs.onHeldUpdate(sink);
    }

    @Override
    public void onDropSlot(java.util.function.Function<DropSlotInfo, Boolean> sink) {
        InventoryEventHubs.onDrop(sink);
    }

    @Override
    public void onItemPickup(java.util.function.Consumer<ItemPickupInfo> sink) {
        InventoryEventHubs.onPickup(sink);
    }
}