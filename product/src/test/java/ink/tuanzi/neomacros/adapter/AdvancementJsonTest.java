package ink.tuanzi.neomacros.adapter;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-018: the advancement JSON face encodes a definition through the plain
 * {@code JsonOps} path the fabric adapter uses — the encode side never
 * needs registry ops, which is what makes {@code advancementJson} readable
 * without a server.
 */
class AdvancementJsonTest {

    @BeforeAll
    static void bootstrapRegistries() {
        // The advancement codec's static init registers the trigger
        // registry, which vanilla gates behind the bootstrap check.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void encodesParentAndTelemetryFlag() {
        var advancement = new Advancement(
                Optional.of(net.minecraft.resources.Identifier.parse(
                        "minecraft:story/root")),
                Optional.empty(),
                AdvancementRewards.EMPTY,
                criteria("test"),
                new AdvancementRequirements(
                        java.util.List.of(java.util.List.of("test"))),
                false);
        var encoded = Advancement.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, advancement);
        assertTrue(encoded.result().isPresent(),
                "encode failed: " + encoded);
        var json = encoded.result().get().toString();
        assertTrue(json.contains("minecraft:story/root"), json);
        assertTrue(json.contains("\"trigger\":"), json);
    }

    @Test
    void encodesRequirementGroupsAndRewardFields() {
        var advancement = new Advancement(
                Optional.of(net.minecraft.resources.Identifier.parse(
                        "minecraft:story/mine_stone")),
                Optional.empty(),
                rewards(),
                criteria("mined_cobblestone", "mined_stone"),
                new AdvancementRequirements(java.util.List.of(
                        java.util.List.of("mined_cobblestone"),
                        java.util.List.of("mined_stone"))),
                false);
        var encoded = Advancement.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, advancement);
        assertTrue(encoded.result().isPresent(),
                "encode failed: " + encoded);
        var json = encoded.result().get().toString();
        assertTrue(json.contains("mined_cobblestone"), json);
        assertTrue(json.contains("mined_stone"), json);
        assertTrue(json.contains("\"experience\":10"), json);
        assertTrue(json.contains("\"requirements\":"), json);
    }

    /**
     * Rewards worth 10 experience: all-default rewards are omitted from
     * the JSON, and the loot type changed to a holder set at 26.3.
     */
    //? if <26.3 {
    /*private static AdvancementRewards rewards() {
        return new AdvancementRewards(10, java.util.List.of(), java.util.List.of(),
                Optional.empty());
    }*/
    //?}
    //? if >=26.3 {
    private static AdvancementRewards rewards() {
        return new AdvancementRewards(10, net.minecraft.core.HolderSet.empty(),
                java.util.List.of(), Optional.empty());
    }
    //?}

    /**
     * One impossible criterion per requirement name: the advancement codec
     * rejects empty criteria maps whose names do not match the
     * requirements, and the trigger package moved at 26.3.
     */
    // The trigger package pivot: triggers/ at 26.2+, criterion/ at 26.1,
    // critereon/ below (same split as AdventureModePredicateAccessor).
    //? if >=26.2 {
    private static Map<String, net.minecraft.advancements.triggers.Criterion<?>> criteria(
            String... names) {
        // The registered trigger instance: encode wraps triggers as
        // registry holders, which only serialize with their key.
        var trigger = net.minecraft.advancements.triggers.CriteriaTriggers.IMPOSSIBLE;
        var instance =
                new net.minecraft.advancements.triggers.ImpossibleTrigger.TriggerInstance();
        Map<String, net.minecraft.advancements.triggers.Criterion<?>> out =
                new java.util.HashMap<>();
        for (String name : names) {
            out.put(name,
                    new net.minecraft.advancements.triggers.Criterion<>(trigger, instance));
        }
        return out;
    }
    //?} else if >=26.1 {
    /*private static Map<String, net.minecraft.advancements.Criterion<?>> criteria(
            String... names) {
        var trigger = net.minecraft.advancements.CriteriaTriggers.IMPOSSIBLE;
        var instance =
                new net.minecraft.advancements.criterion.ImpossibleTrigger.TriggerInstance();
        Map<String, net.minecraft.advancements.Criterion<?>> out = new java.util.HashMap<>();
        for (String name : names) {
            out.put(name, new net.minecraft.advancements.Criterion<>(trigger, instance));
        }
        return out;
    }*/
    //?} else {
    /*private static Map<String, net.minecraft.advancements.Criterion<?>> criteria(
            String... names) {
        var trigger = net.minecraft.advancements.CriteriaTriggers.IMPOSSIBLE;
        var instance =
                new net.minecraft.advancements.critereon.ImpossibleTrigger.TriggerInstance();
        Map<String, net.minecraft.advancements.Criterion<?>> out = new java.util.HashMap<>();
        for (String name : names) {
            out.put(name, new net.minecraft.advancements.Criterion<>(trigger, instance));
        }
        return out;
    }*/
    //?}
}
