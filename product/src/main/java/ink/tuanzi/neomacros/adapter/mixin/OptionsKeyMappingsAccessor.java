package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Runtime keybinding registration: exposes the options keyMappings array
 * so mappings created after the options screen exists can be spliced in;
 * fabric api only accepts registrations during client init.
 */
@Mixin(Options.class)
public interface OptionsKeyMappingsAccessor {

    @Accessor("keyMappings")
    KeyMapping[] neomacros$keyMappings();

    @Mutable
    @Accessor("keyMappings")
    void neomacros$keyMappings(KeyMapping[] value);
}
