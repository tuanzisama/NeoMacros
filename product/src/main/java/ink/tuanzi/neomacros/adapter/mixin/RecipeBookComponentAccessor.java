package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the tab list a recipe book component was built with, so the recipe
 * book face can enumerate the same tabs the widget renders.
 */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {

    @Accessor("tabInfos")
    List<RecipeBookComponent.TabInfo> neomacros$tabInfos();
}
