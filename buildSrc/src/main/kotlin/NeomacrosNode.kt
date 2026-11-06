import dev.kikugie.stonecutter.build.StonecutterBuildExtension

// Per-node source replacements: the sources shared across nodes are written
// against the active (newest) node's API spelling and rewritten for older
// nodes here. The active node compiles raw sources without preprocessing.
// Stonecutter applies each pair from->to on nodes matching the condition and
// the inverse (to->from) on the rest, so every pair's `to` spelling must not
// appear anywhere in the shared sources; pairs that cannot satisfy that
// (overlapping or ubiquitous strings) live in //? comment swaps instead.
fun StonecutterBuildExtension.neomacrosReplacements() {
    // 26.1 started the per-species entity packages (animal/sheep/, npc/villager/,
    // monster/zombie/, ...); pre-26.1 nodes keep the flat animal/ and npc/ trees.
    replacements.string(current.parsed < "26.1") {
        replace("world.entity.animal.feline.Cat", "world.entity.animal.Cat")
        replace("world.entity.animal.cow.MushroomCow", "world.entity.animal.MushroomCow")
        replace("world.entity.monster.zombie.Zombie", "world.entity.monster.Zombie")
        replace("world.entity.npc.villager.Villager", "world.entity.npc.Villager")
        replace("world.entity.animal.equine.Horse", "world.entity.animal.horse.Horse")
        replace("world.entity.animal.equine.AbstractHorse", "world.entity.animal.horse.AbstractHorse")
        replace("world.entity.animal.equine.AbstractChestedHorse", "world.entity.animal.horse.AbstractChestedHorse")
        replace("world.entity.animal.equine.Donkey", "world.entity.animal.horse.Donkey")
        replace("world.entity.animal.equine.Llama", "world.entity.animal.horse.Llama")
        replace("world.entity.animal.golem.IronGolem", "world.entity.animal.IronGolem")
        replace("world.entity.monster.spider.Spider", "world.entity.monster.Spider")
        replace("world.entity.animal.rabbit.Rabbit", "world.entity.animal.Rabbit")
        replace("world.entity.animal.fox.Fox", "world.entity.animal.Fox")
        replace("world.entity.animal.panda.Panda", "world.entity.animal.Panda")
        replace("world.entity.animal.parrot.Parrot", "world.entity.animal.Parrot")
        replace("world.entity.animal.polarbear.PolarBear", "world.entity.animal.PolarBear")
        replace("world.entity.animal.golem.SnowGolem", "world.entity.animal.SnowGolem")
        replace("world.entity.animal.turtle.Turtle", "world.entity.animal.Turtle")
        replace("world.entity.animal.dolphin.Dolphin", "world.entity.animal.Dolphin")

        replace("world.entity.animal.bee.Bee", "world.entity.animal.Bee")
        replace("world.entity.animal.cow.Cow", "world.entity.animal.Cow")
        replace("world.entity.animal.fish.AbstractSchoolingFish", "world.entity.animal.AbstractSchoolingFish")
        replace("world.entity.animal.fish.AbstractFish", "world.entity.animal.AbstractFish")
        replace("world.entity.animal.fish.Salmon", "world.entity.animal.Salmon")
        replace("world.entity.animal.fish.Pufferfish", "world.entity.animal.Pufferfish")
        replace("world.entity.animal.fish.TropicalFish", "world.entity.animal.TropicalFish")
        replace("world.entity.animal.fish.Cod", "world.entity.animal.Cod")
        replace("world.entity.monster.illager.Pillager", "world.entity.monster.Pillager")
        replace("world.entity.monster.illager.Evoker", "world.entity.monster.Evoker")
        replace("world.entity.npc.wanderingtrader.WanderingTrader", "world.entity.npc.WanderingTrader")
        // The command tag accessor renamed after 1.21.6; the 26.x era keeps
        // the entityTags() spelling everywhere else.
        replace("living.entityTags()", "living.getTags()")
    }
    // Pre-1.21.11 nodes.
    replacements.string(current.parsed < "1.21.11") {
        // fabric-api renamed the command helper and the keybind helper.
        replace("ClientCommands", "ClientCommandManager")
        replace("keymapping.v1.KeyMappingHelper", "keybinding.v1.KeyBindingHelper")
        replace("KeyMappingHelper", "KeyBindingHelper")
        replace("registerKeyMapping", "registerKeyBinding")
        // The outline helper takes width/height before the record rewrite.
        replace(
            "extractor.outline(x, y, x + width, y + height, color)",
            "extractor.renderOutline(x, y, width, height, color)",
        )
        // SoundInstance renamed its id accessor.
        replace("sound.getIdentifier()", "sound.getLocation()")
        // Chat text draws through drawString on the pre-extract pipeline.
        replace("extractor.text(", "extractor.drawString(")
        // Level/ResourceKey accessor renames. ChunkPos became a record in
        // the new era; the old node carries public final fields.
        replace("getPos().x()", "getPos().x")
        replace("getPos().z()", "getPos().z")
        // Level/ResourceKey accessor renames.
        replace("getDefaultClockTime()", "getDayTime()")
        replace("dimension().identifier()", "dimension().location()")
        // GameProfile keeps bean-style accessors on the old authlib line.
        replace("profile.id()", "profile.getId()")
        replace("profile.name()", "profile.getName()")
        // Minecraft renamed ResourceLocation to Identifier in 1.21.11; this
        // blanket rename must stay last within the block.
        replace("Identifier", "ResourceLocation")
    }
    // 26.3 renamed the keyboard input type constant and moved held-item
    // dropping onto the interaction mode.
    replacements.string(current.parsed < "26.3") {
        replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM")
        replace("world.entity.monster.Enderman", "world.entity.monster.EnderMan")
        replace(
            "Minecraft.getInstance().gameMode.dropItem(player, all);",
            "player.drop(all);",
        )
    }
    // Before 26.2 the chat surface, the overlay message, the title overlay,
    // and the toast manager lived on Gui directly; 26.2 moved them under the
    // hud sub-panel. The toast manager accessor itself renamed too.
    replacements.string(current.parsed < "26.2") {        replace("gui.hud.getChat()", "gui.getChat()")
        replace("gui.hud.setOverlayMessage", "gui.setOverlayMessage")
        replace("gui.hud.setTitle(", "gui.setTitle(")
        replace("gui.hud.setSubtitle(", "gui.setSubtitle(")
        replace("gui.hud.setTimes(", "gui.setTimes(")
        replace("gui.hud.clearTitles()", "gui.clearTitles()")
        replace("gui.hud.resetTitleTimes()", "gui.resetTitleTimes()")
        replace("gui.toastManager()", "getToastManager()")
        // The main framebuffer accessor moved onto the game renderer.
        replace("gameRenderer.mainRenderTarget()", "getMainRenderTarget()")
    }
}
