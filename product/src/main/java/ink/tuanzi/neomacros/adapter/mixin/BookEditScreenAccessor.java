package ink.tuanzi.neomacros.adapter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the book editor's pages and cursor page so the screen surface can
 * read the text under edit.
 */
@Mixin(net.minecraft.client.gui.screens.inventory.BookEditScreen.class)
public interface BookEditScreenAccessor {

    @Accessor("pages")
    List<String> neomacros$pages();

    @Accessor("currentPage")
    int neomacros$currentPage();
}
