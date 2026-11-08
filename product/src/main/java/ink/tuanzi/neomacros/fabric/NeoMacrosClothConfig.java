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

        builder.getOrCreateCategory(Component.translatable("neomacros.config.category.web"))
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.web.enabled"),
                                NeoMacrosFabric.configOptions().web.enabled)
                        .setDefaultValue(false)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().web.enabled = value;
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.web.auto_start"),
                                NeoMacrosFabric.configOptions().web.autoStart)
                        .setDefaultValue(false)
                        .setTooltip(Component.translatable(
                                "neomacros.config.web.auto_start_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().web.autoStart = value;
                        })
                        .build())
                .addEntry(entries.startIntField(
                                Component.translatable("neomacros.config.web.port"),
                                NeoMacrosFabric.configOptions().web.port)
                        .setMin(0)
                        .setMax(65535)
                        .setDefaultValue(30636)
                        .setTooltip(Component.translatable("neomacros.config.web.port_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().web.port = value;
                        })
                        .build())
                .addEntry(entries.startStrField(
                                Component.translatable("neomacros.config.web.listen"),
                                NeoMacrosFabric.configOptions().web.listen)
                        .setDefaultValue("127.0.0.1")
                        .setErrorSupplier(value -> java.util.List
                                .of("127.0.0.1", "0.0.0.0", "::1", "loopback", "lan")
                                .contains(value == null ? "" : value.strip())
                                ? java.util.Optional.<Component>empty()
                                : java.util.Optional.of(Component.translatable(
                                        "neomacros.config.web.listen_invalid")))
                        .setTooltip(Component.translatable("neomacros.config.web.listen_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().web.listen = value.strip();
                        })
                        .build())
                .addEntry(entries.startStrField(
                                Component.translatable("neomacros.config.web.panel_url"),
                                NeoMacrosFabric.configOptions().web.panelUrl)
                        .setDefaultValue("https://neomacro-editor.tuanzi.ink")
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().web.panelUrl = value;
                        })
                        .build())
                .addEntry(entries.startStrList(
                                Component.translatable("neomacros.config.web.allowed_origins"),
                                NeoMacrosFabric.configOptions().web.allowedOrigins)
                        .setDefaultValue(java.util.List.of("*"))
                        .setTooltip(Component.translatable(
                                "neomacros.config.web.allowed_origins_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().web.allowedOrigins =
                                    java.util.List.copyOf(value);
                        })
                        .build());

        builder.getOrCreateCategory(Component.translatable("neomacros.config.category.mcp"))
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.mcp.enabled"),
                                NeoMacrosFabric.configOptions().mcp.enabled)
                        .setDefaultValue(false)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.enabled = value;
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.mcp.auto_start"),
                                NeoMacrosFabric.configOptions().mcp.autoStart)
                        .setDefaultValue(false)
                        .setTooltip(Component.translatable(
                                "neomacros.config.mcp.auto_start_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.autoStart = value;
                        })
                        .build())
                .addEntry(entries.startIntField(
                                Component.translatable("neomacros.config.mcp.port"),
                                NeoMacrosFabric.configOptions().mcp.port)
                        .setMin(0)
                        .setMax(65535)
                        .setDefaultValue(30637)
                        .setTooltip(Component.translatable("neomacros.config.mcp.port_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.port = value;
                        })
                        .build())
                .addEntry(entries.startStrField(
                                Component.translatable("neomacros.config.mcp.listen"),
                                NeoMacrosFabric.configOptions().mcp.listen)
                        .setDefaultValue("127.0.0.1")
                        .setErrorSupplier(value -> java.util.List
                                .of("127.0.0.1", "0.0.0.0", "::1", "loopback", "lan")
                                .contains(value == null ? "" : value.strip())
                                ? java.util.Optional.<Component>empty()
                                : java.util.Optional.of(Component.translatable(
                                        "neomacros.config.web.listen_invalid")))
                        .setTooltip(Component.translatable("neomacros.config.mcp.listen_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.listen = value.strip();
                        })
                        .build())
                .addEntry(entries.startStrField(
                                Component.translatable("neomacros.config.mcp.log_capture"),
                                NeoMacrosFabric.configOptions().mcp.logCapture)
                        .setDefaultValue("mod")
                        .setErrorSupplier(value -> java.util.List
                                .of("mod", "all")
                                .contains(value == null ? "" : value.strip().toLowerCase(
                                        java.util.Locale.ROOT))
                                ? java.util.Optional.<Component>empty()
                                : java.util.Optional.of(Component.translatable(
                                        "neomacros.config.mcp.log_capture_invalid")))
                        .setTooltip(Component.translatable(
                                "neomacros.config.mcp.log_capture_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.logCapture =
                                    value.strip().toLowerCase(java.util.Locale.ROOT);
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.mcp.group_logs"),
                                NeoMacrosFabric.configOptions().mcp.groupLogs)
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.groupLogs = value;
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.mcp.group_state"),
                                NeoMacrosFabric.configOptions().mcp.groupState)
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.groupState = value;
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.mcp.group_control"),
                                NeoMacrosFabric.configOptions().mcp.groupControl)
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.groupControl = value;
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.mcp.group_eval"),
                                NeoMacrosFabric.configOptions().mcp.groupEval)
                        .setDefaultValue(true)
                        .setTooltip(Component.translatable(
                                "neomacros.config.mcp.group_eval_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().mcp.groupEval = value;
                        })
                        .build());

        builder.getOrCreateCategory(Component.translatable("neomacros.config.category.update"))
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.update.enabled"),
                                NeoMacrosFabric.configOptions().update.enabled)
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().update.enabled = value;
                        })
                        .build())
                .addEntry(entries.startIntSlider(
                                Component.translatable("neomacros.config.update.interval"),
                                NeoMacrosFabric.configOptions().update.intervalHours, 1, 168)
                        .setDefaultValue(12)
                        .setTooltip(Component.translatable(
                                "neomacros.config.update.interval_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().update.intervalHours = value;
                        })
                        .build())
                .addEntry(entries.startStrField(
                                Component.translatable("neomacros.config.update.channel"),
                                NeoMacrosFabric.configOptions().update.channel)
                        .setDefaultValue("auto")
                        .setErrorSupplier(value -> java.util.List
                                .of("auto", "release", "dev", "canary")
                                .contains(value == null ? "" : value.strip().toLowerCase(
                                        java.util.Locale.ROOT))
                                ? java.util.Optional.<Component>empty()
                                : java.util.Optional.of(Component.translatable(
                                        "neomacros.config.update.channel_invalid")))
                        .setTooltip(Component.translatable(
                                "neomacros.config.update.channel_hint"))
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().update.channel =
                                    value.strip().toLowerCase(java.util.Locale.ROOT);
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.update.notify_in_game"),
                                NeoMacrosFabric.configOptions().update.notifyInGame)
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().update.notifyInGame = value;
                        })
                        .build())
                .addEntry(entries.startBooleanToggle(
                                Component.translatable("neomacros.config.update.notify_editor"),
                                NeoMacrosFabric.configOptions().update.notifyEditor)
                        .setDefaultValue(true)
                        .setSaveConsumer(value -> {
                            NeoMacrosFabric.configOptions().update.notifyEditor = value;
                        })
                        .build());

        builder.setSavingRunnable(() -> {
            NeoMacrosFabric.saveConfig();
            NeoMacrosFabric.restartWebIfRunning();
            NeoMacrosFabric.restartMcpIfRunning();
        });
        return builder.build();
    }
}
