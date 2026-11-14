package ink.tuanzi.neomacros.adapter.mixin;

//? if >=26.2 {
import net.minecraft.advancements.predicates.BlockPredicate;
//?} else if >=26.1 {
/*
import net.minecraft.advancements.criterion.BlockPredicate;
*/
//?} else {
/*
import net.minecraft.advancements.critereon.BlockPredicate;
*/
//?}
import net.minecraft.world.item.AdventureModePredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes the predicate list behind the adventure-mode restriction
 * component so item snapshots can read the accepted block identifiers of
 * can-destroy and can-place-on without re-serializing the component.
 */
@Mixin(AdventureModePredicate.class)
public interface AdventureModePredicateAccessor {

    @Accessor("predicates")
    List<BlockPredicate> neomacros$predicates();
}
