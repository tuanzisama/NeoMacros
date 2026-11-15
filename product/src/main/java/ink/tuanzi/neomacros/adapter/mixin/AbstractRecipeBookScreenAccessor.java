package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the recipe book component of a recipe-book screen so the recipe
 * book face can read the screen's tab list exactly as the widget does.
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {

    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> neomacros$recipeBookComponent();
}
