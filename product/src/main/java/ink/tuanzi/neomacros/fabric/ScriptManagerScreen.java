package ink.tuanzi.neomacros.fabric;

import ink.tuanzi.neomacros.core.security.TrustManager;
import net.minecraft.client.Minecraft;
//? if <1.21.11 {
/*
import net.minecraft.client.gui.GuiGraphics;
*/
//?}
//? if >=1.21.11 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Script management panel: lists the scripts below the scripts directory
 * with their loaded and trust state, opens the editor for a script on row
 * click, and offers reload-all, trust-selected, and new-script actions.
 */
public final class ScriptManagerScreen extends Screen {

    private final Path scriptsDir;
    private final Supplier<Set<String>> loadedNames;
    private final Map<String, String> pendingTrusts;
    private final TrustManager trustManager;
    private final Runnable onReload;
    //? if >=26.1 {
    private ScriptList list;
    //?} else {
    /*
    private String selectedScript;
    */
    //?}
    private String status = "";

    private ScriptManagerScreen(Path scriptsDir, Supplier<Set<String>> loadedNames,
            Map<String, String> pendingTrusts, TrustManager trustManager, Runnable onReload) {
        super(Component.literal("[NeoMacros] scripts"));
        this.scriptsDir = scriptsDir;
        this.loadedNames = loadedNames;
        this.pendingTrusts = pendingTrusts;
        this.trustManager = trustManager;
        this.onReload = onReload;
    }

    /** Opens the panel on the client thread. */
    public static void open(Path scriptsDir, Supplier<Set<String>> loadedNames,
            Map<String, String> pendingTrusts, TrustManager trustManager, Runnable onReload) {
        //? if <1.21.11 {
        /*
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(
                new ScriptManagerScreen(scriptsDir, loadedNames, pendingTrusts,
                        trustManager, onReload)));
        */
        //?}
//? if >=1.21.11 {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(
                new ScriptManagerScreen(scriptsDir, loadedNames, pendingTrusts,
                        trustManager, onReload)));
//?}
    }

    @Override
    protected void init() {
        //? if >=26.1 {
        list = new ScriptList();
        repopulate();
        addRenderableWidget(list);
        addHeaderButtons();
        //?} else {
        /*
        repopulate();
        */
        //?}
    }

    /** Adds the shared bottom action row: new script, reload, trust, done. */
    private void addHeaderButtons() {
        int y = this.height - 40;
        int margin = 8;
        addRenderableWidget(Button.builder(Component.literal("New Script"), button -> {
                    ScriptEditorScreen.open(scriptsDir.resolve("new-script.js"), onReload);
                }).bounds(margin, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reload All"), button -> {
                    onReload.run();
                    status = "reload requested";
                }).bounds(margin + 98, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Trust Selected"), button -> {
                    trustSelected();
                }).bounds(margin + 196, y, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width - margin - 80, y, 80, 20).build());
    }

    /** Grants the pending trust request of the selected script, if any. */
    private void trustSelected() {
        //? if >=26.1 {
        ScriptList.Row row = list.getSelected();
        if (row == null) {
            status = "select a script first";
            return;
        }
        String name = row.data.name();
        //?} else {
        /*
        String name = selectedScript;
        if (name == null) {
            status = "open a script first";
            return;
        }
        */
        //?}
        String hash = pendingTrusts.get(name);
        if (hash == null) {
            status = name + " has no pending trust request";
            return;
        }
        trustManager.trust(hash);
        status = "trusted " + name;
        repopulate();
    }

    /** Rebuilds the row list from the scripts directory. */
    private void repopulate() {
        Set<String> loaded = loadedNames.get();
        List<Path> files = scriptFiles();
        //? if >=26.1 {
        list.replaceRows(files.stream()
                .map(file -> {
                    String name = file.getFileName().toString();
                    return new ScriptList.RowData(file, name,
                            loaded.contains(name), pendingTrusts.containsKey(name));
                })
                .toList());
        //?} else {
        /*
        clearWidgets();
        int rowY = 24;
        for (Path file : files) {
            String name = file.getFileName().toString();
            String marks = (loaded.contains(name) ? "loaded" : "stopped")
                    + (pendingTrusts.containsKey(name) ? "  [untrusted]" : "");
            addRenderableWidget(Button.builder(Component.literal(name), button -> {
                        selectedScript = name;
                        ScriptEditorScreen.open(file, onReload);
                    }).bounds(8, rowY, 200, 16).build());
            addRenderableWidget(Button.builder(Component.literal(marks), button -> {
                        selectedScript = name;
                        trustSelected();
                    }).bounds(212, rowY, 120, 16).build());
            rowY += 18;
        }
        addHeaderButtons();
        */
        //?}
        if (files.isEmpty()) {
            status = "no scripts in " + scriptsDir.getFileName();
        }
    }

    private List<Path> scriptFiles() {
        if (!Files.isDirectory(scriptsDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(scriptsDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".js"))
                    .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            status = "cannot list scripts: " + e.getMessage();
            return List.of();
        }
    }

    //? if <1.21.11 {
    /*
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!status.isEmpty()) {
            graphics.drawString(this.font, status, 8, this.height - 14, 0xFF80FF80);
        }
    }
    */
    //?}
    //? if >=1.21.11 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xC0101010);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        if (!status.isEmpty()) {
            extractor.text(this.font, status, 8, this.height - 14, 0xFF80FF80);
        }
    }
    //?}

    //? if >=26.1 {
    private final class ScriptList extends ObjectSelectionList<ScriptList.Row> {

        ScriptList() {
            super(Minecraft.getInstance(), ScriptManagerScreen.this.width,
                    ScriptManagerScreen.this.height - 64, 24, 16);
        }

        /** Immutable row payload handed from the screen to the list. */
        record RowData(Path file, String name, boolean loaded, boolean untrusted) {
        }

        /** Clears and rebuilds the rows from one snapshot. */
        void replaceRows(List<RowData> rows) {
            clearEntries();
            for (RowData data : rows) {
                addEntry(new Row(data));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 16;
        }

        /** One script row: click opens the editor. */
        final class Row extends ObjectSelectionList.Entry<Row> {
            final RowData data;

            Row(RowData data) {
                this.data = data;
            }

            @Override
            public Component getNarration() {
                return Component.literal(data.name() + (data.loaded() ? " loaded" : " unloaded"));
            }

            //? if <1.21.11 {
            /*
            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                int color = hovered ? 0xFFFFFFA0 : 0xFFFFFFFF;
                graphics.drawString(font, data.name(), left + 4, top + 2, color);
                String state = data.loaded() ? "loaded" : "stopped";
                String marks = state + (data.untrusted() ? "  [untrusted]" : "");
                graphics.drawString(font, marks, left + getRowWidth() - 110,
                        top + 2, data.untrusted() ? 0xFFFF8080 : 0xFF80C080);
            }
            */
            //?}
//? if >=1.21.11 {
            @Override
            public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                    boolean hovered, float partialTick) {
                int color = hovered ? 0xFFFFFFA0 : 0xFFFFFFFF;
                extractor.text(font, data.name(), getX() + 4, getY() + 2, color);
                String state = data.loaded() ? "loaded" : "stopped";
                String marks = state + (data.untrusted() ? "  [untrusted]" : "");
                extractor.text(font, marks, getX() + getRowWidth() - 110,
                        getY() + 2, data.untrusted() ? 0xFFFF8080 : 0xFF80C080);
            }
//?}

            @Override
            public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
                ScriptEditorScreen.open(data.file(), onReload);
                return true;
            }
        }
    }
    //?}
}
