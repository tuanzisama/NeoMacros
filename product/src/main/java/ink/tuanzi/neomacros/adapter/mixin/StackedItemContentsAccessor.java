package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the stacked-contents counter inside {@link StackedItemContents} so
 * the recipe book face can run the multi-craft pick (craftable amount) the
 * wrapper does not forward.
 */
@Mixin(StackedItemContents.class)
public interface StackedItemContentsAccessor {

    @Accessor("raw")
    StackedContents<Holder<Item>> neomacros$raw();
}
