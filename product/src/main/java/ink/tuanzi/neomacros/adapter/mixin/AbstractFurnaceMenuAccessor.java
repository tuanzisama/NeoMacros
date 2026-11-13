package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the furnace menu's raw data slots (remaining and total burn time,
 * cook progress and cook total) plus the two input predicates the menu keeps
 * private, so the furnace face can report raw tick counts instead of only
 * the rounded progress bars.
 */
@Mixin(AbstractFurnaceMenu.class)
public interface AbstractFurnaceMenuAccessor {

    @Accessor("data")
    ContainerData neomacros$data();

    @Invoker("canSmelt")
    boolean neomacros$canSmelt(ItemStack stack);

    @Invoker("isFuel")
    boolean neomacros$isFuel(ItemStack stack);
}
