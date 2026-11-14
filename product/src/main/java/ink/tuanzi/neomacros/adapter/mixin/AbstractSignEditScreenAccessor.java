package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the state behind the sign editor so the screen surface can read
 * the text under edit and the write face can fill it: {@code sign} is the
 * block entity behind the editor, {@code messages} is the editor's four-line
 * buffer (the vanilla editor renders from it and submits it to the server
 * when it closes). Both field names are identical on every supported node
 * (1.21.6 / 26.1.2 / 26.2 / 26.3, javap-verified).
 */
@Mixin(net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {

    @Accessor("sign")
    SignBlockEntity neomacros$sign();

    /** The editor's four-line buffer; mutating the array writes the editor. */
    @Accessor("messages")
    String[] neomacros$messages();
}
