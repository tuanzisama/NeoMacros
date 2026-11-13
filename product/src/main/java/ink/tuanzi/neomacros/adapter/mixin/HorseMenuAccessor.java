package ink.tuanzi.neomacros.adapter.mixin;

//? if >=26.1 {
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the mount entity behind the open horse-family menu; 26.1 moved
 * the field onto the shared mount menu base as {@code mount}.
 */
@Mixin(net.minecraft.world.inventory.AbstractMountInventoryMenu.class)
public interface HorseMenuAccessor {

    /** The mount the menu was built for. */
    @Accessor("mount")
    LivingEntity neomacros$mount();
}
//?} else {
/*import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the mount entity behind the open horse-family menu.
@Mixin(net.minecraft.world.inventory.HorseInventoryMenu.class)
public interface HorseMenuAccessor {

    // The mount the menu was built for.
    @Accessor("horse")
    AbstractHorse neomacros$mount();
}
*///?}
