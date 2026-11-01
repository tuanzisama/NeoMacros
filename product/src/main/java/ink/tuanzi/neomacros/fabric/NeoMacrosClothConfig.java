package ink.tuanzi.neomacros.fabric;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen built on Cloth Config (optional dependency): exposes the
 * handful of tunables that live in options.json and saves them back
 * through the regular config manager. Only instantiated when the Cloth
 * mod is installed.
 */
public final class NeoMacrosClothConfig {

    private NeoMacrosClothConfig() {
    }

    /** Builds the Cloth-backed settings screen for the given parent. */
    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("neomacros.config.title"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        builder.getOrCreateCategory(Component.translatable("neomacros.config.category.macros"))
                .addEntry(entries.startStrField(
                                Component.translatable("neomacros.config.macros_dir"),
                                NeoMacrosFabric.configOptions().macrosDir)
                        .setTooltip(Component.translatable("neomacros.config.macros_dir_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().macrosDir = value;
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.script_watch"),
                                NeoMacrosFabric.configOptions().scriptWatch)
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().scriptWatch = value;
                        })
                        .build())
                .addEntry(entries.startIntSlider(
                                Component.translatable("neomacros.config.watch_interval"),
                                NeoMacrosFabric.configOptions().scriptWatchInterval, 20, 600)
                        .setDefaultValue(40)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().scriptWatchInterval = value;
                        })
                        .build());

        builder.getOrCreateCategory(Component.translatable("neomacros.config.category.engine"))
                .addEntry(entries.startIntField(
                                Component.translatable("neomacros.config.max_sync_time"),
                                NeoMacrosFabric.configOptions().maxSyncTime)
                        .setMin(0)
                        .setDefaultValue(500)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().maxSyncTime = value;
                        })
                        .build());

        builder.setSavingRunnable(NeoMacrosFabric::saveConfig);
        return builder.build();
    }
}
