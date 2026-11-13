package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the creative screen's scroll position, search box, and selected
 * tab so the creative inventory face can scroll, search, and switch tabs
 * exactly as the screen itself does. The selected tab stays a non-static
 * accessor over its static field, matching the vanilla reload contract.
 */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenAccessor {

    @Accessor("scrollOffs")
    float neomacros$scrollOffs();

    @Accessor("scrollOffs")
    void neomacros$scrollOffs(float position);

    @Accessor("searchBox")
    EditBox neomacros$searchBox();

    @Accessor("selectedTab")
    CreativeModeTab neomacros$selectedTab();

    @Invoker("selectTab")
    void neomacros$selectTab(CreativeModeTab tab);

    @Invoker("refreshSearchResults")
    void neomacros$refreshSearchResults();

    @Invoker("canScroll")
    boolean neomacros$canScroll();
}
