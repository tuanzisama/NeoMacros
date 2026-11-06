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
            return java.util.Map.of();
        });
    }
}