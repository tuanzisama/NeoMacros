package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the beacon screen's pending effect selection, which the screen
 * keeps locally until the payment slot confirms it and the selection is
 * sent to the server.
 */
@Mixin(BeaconScreen.class)
public interface BeaconScreenAccessor {

    @Accessor("primary")
    Holder<net.minecraft.world.effect.MobEffect> neomacros$primary();

    @Accessor("primary")
    void neomacros$primary(Holder<net.minecraft.world.effect.MobEffect> effect);

    @Accessor("secondary")
    Holder<net.minecraft.world.effect.MobEffect> neomacros$secondary();

    @Accessor("secondary")
    void neomacros$secondary(Holder<net.minecraft.world.effect.MobEffect> effect);
}
