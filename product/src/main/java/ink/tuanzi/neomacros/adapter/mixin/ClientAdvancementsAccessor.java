package ink.tuanzi.neomacros.adapter.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes the advancement tree and progress map behind the client
 * advancement tracker so world queries can read advancement state.
 */
@Mixin(ClientAdvancements.class)
public interface ClientAdvancementsAccessor {

    @Accessor("tree")
    AdvancementTree neomacros$tree();

    @Accessor("progress")
    Map<AdvancementHolder, AdvancementProgress> neomacros$progress();
}
