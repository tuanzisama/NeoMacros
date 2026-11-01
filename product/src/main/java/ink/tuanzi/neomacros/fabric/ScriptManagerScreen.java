package ink.tuanzi.neomacros.fabric;

import com.mojang.blaze3d.platform.InputConstants;
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

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Script management panel: lists the discovered script folders grouped by
 * category (keypress/event/services) with invalid folders and their reasons,
 * opens the editor for a script on row click, captures a pressed key for
 * keypress bindings, and offers new-script scaffolds, reload-all, trust, and
 * bind actions.
 */
public final class ScriptManagerScreen extends Screen {

    /**
     * One display row. Header rows group categories; error rows carry a
     * rejection reason; script rows open the editor on click. {@code name}
     * is the script identity (empty for header/error rows).
     */
    public record RowData(String name, String title, String state, boolean untrusted,
                          boolean header, boolean bindable, java.nio.file.Path entryFile) {
    }

    private final Supplier<List<RowData>> rowsProvider;
    private final Map<String, String> pendingTrusts;
    private final TrustManager trustManager;
    private final BiConsumer<String, String> onBind;
    private final Consumer<String> onUnbind;
    private final Consumer<String> onNewScript;
    private final Runnable onReload;
    //? if >=26.1 {
    private ScriptList list;
    //?} else {
    /*
    private String selectedScript;
    */
    //?}
    private String status = "";
    private boolean awaitingBind;

    private ScriptManagerScreen(Supplier<List<RowData>> rowsProvider, Map<String, String> pendingTrusts,
            TrustManager trustManager, BiConsumer<String, String> onBind, Consumer<String> onUnbind,
            Consumer<String> onNewScript, Runnable onReload) {
        super(Component.literal("[NeoMacros] macros"));
        this.rowsProvider = rowsProvider;
        this.pendingTrusts = pendingTrusts;
        this.trustManager = trustManager;
        this.onBind = onBind;
        this.onUnbind = onUnbind;
        this.onNewScript = onNewScript;
        this.onReload = onReload;
    }

    /** Builds the panel screen without showing it (ModMenu config factory). */
    public static ScriptManagerScreen create(Supplier<List<RowData>> rowsProvider,
            Map<String, String> pendingTrusts, TrustManager trustManager,
            BiConsumer<String, String> onBind, Consumer<String> onUnbind,
            Consumer<String> onNewScript, Runnable onReload) {
        return new ScriptManagerScreen(rowsProvider, pendingTrusts, trustManager,
                onBind, onUnbind, onNewScript, onReload);
    }

    /** Shows an already-built panel on the client thread. */
    public static void show(net.minecraft.client.gui.screens.Screen screen) {
        //? if <1.21.11 {
        /*
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(screen));
        */
        //?}
//? if >=1.21.11 {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(screen));
//?}
    }

    /** Opens the panel on the client thread. */
    public static void open(Supplier<List<RowData>> rowsProvider, Map<String, String> pendingTrusts,
            TrustManager trustManager, BiConsumer<String, String> onBind, Consumer<String> onUnbind,
            Consumer<String> onNewScript, Runnable onReload) {
        show(create(rowsProvider, pendingTrusts, trustManager,
                onBind, onUnbind, onNewScript, onReload));
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

    /** Adds the shared action rows: new scaffolds, reload, bind, trust, done. */
    private void addHeaderButtons() {
        int margin = 8;
        int y = this.height - 52;
        addRenderableWidget(Button.builder(Component.literal("New: Key"), button ->
                        onNewScript.accept("keypress")).bounds(margin, y, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("New: Event"), button ->
                        onNewScript.accept("event")).bounds(margin + 90, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("New: Service"), button ->
                        onNewScript.accept("services")).bounds(margin + 186, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reload All"), button -> {
                    onReload.run();
                    status = "reload requested";
                    repopulate();
                }).bounds(margin + 288, y, 90, 20).build());

        int y2 = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal("Bind Key"), button -> startBind())
                .bounds(margin, y2, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Unbind"), button -> unbindSelected())
                .bounds(margin + 90, y2, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Trust Selected"), button ->
                        trustSelected()).bounds(margin + 180, y2, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width - margin - 80, y2, 80, 20).build());
    }

    /** Arms key capture; the next pressed key becomes the selected script's binding. */
    private void startBind() {
        //? if >=26.1 {
        ScriptList.Row row = list.getSelected();
        if (row == null || row.data.name().isEmpty() || !row.data.bindable()) {
            status = "select a keypress script first";
            return;
        }
        awaitingBind = true;
        status = "press any key to bind '" + row.data.name() + "' (Esc cancels)";
        //?} else {
        /*
        status = "bind via /neomacros keypress bind <script> <key>";
        */
        //?}
    }

    private void unbindSelected() {
        //? if >=26.1 {
        ScriptList.Row row = list.getSelected();
        if (row == null || row.data.name().isEmpty() || !row.data.bindable()) {
            status = "select a keypress script first";
            return;
        }
        onUnbind.accept(row.data.name());
        status = "unbound " + row.data.name();
        repopulate();
        //?} else {
        /*
        status = "unbind via /neomacros keypress unbind <script>";
        */
        //?}
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
        if (name == null || name.isEmpty()) {
            status = "select a script first";
            return;
        }
        String hash = pendingTrusts.get(name);
        if (hash == null) {
            status = name + " has no pending trust request";
            return;
        }
        trustManager.trust(hash);
        status = "trusted " + name;
        repopulate();
    }

    /** Rebuilds the row list from the provider snapshot. */
    private void repopulate() {
        List<RowData> rows = rowsProvider.get();
        //? if >=26.1 {
        list.replaceRows(rows);
        //?} else {
        /*
        clearWidgets();
        int rowY = 24;
        for (RowData data : rows) {
            if (data.header() || data.entryFile() == null) {
                continue;
            }
            String name = data.name();
            addRenderableWidget(Button.builder(Component.literal(data.title()), button -> {
                        selectedScript = name;
                        ScriptEditorScreen.open(data.entryFile(), onReload);
                    }).bounds(8, rowY, 200, 16).build());
            addRenderableWidget(Button.builder(Component.literal(data.state()), button -> {
                        selectedScript = name;
                    }).bounds(212, rowY, 160, 16).build());
            rowY += 18;
        }
        addHeaderButtons();
        */
        //?}
        if (rows.stream().noneMatch(r -> !r.header())) {
            status = "no macros found";
        }
    }

    //? if <26.1 {
    /*
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (awaitingBind) {
            awaitingBind = false;
            if (keyCode == 256) { // Esc cancels
                status = "bind canceled";
                return true;
            }
            applyCapturedKey(InputConstants.getKey(keyCode, scanCode).getName());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    */
    //?}
    //? if >=26.1 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (awaitingBind) {
            awaitingBind = false;
            if (event.key() == 256) { // Esc cancels
                status = "bind canceled";
                return true;
            }
            applyCapturedKey(InputConstants.getKey(event).getName());
            return true;
        }
        return super.keyPressed(event);
    }
    //?}

    /** Applies a captured keyboard key name to the selected script's binding. */
    private void applyCapturedKey(String keyName) {
        if (!keyName.startsWith("key.keyboard.")) {
            status = "not a keyboard key: " + keyName;
            return;
        }
        //? if >=26.1 {
        ScriptList.Row row = list.getSelected();
        if (row != null && !row.data.name().isEmpty()) {
            onBind.accept(row.data.name(), keyName);
            status = "bound " + row.data.name() + " to " + keyName;
            repopulate();
        }
        //?}
    }

    //? if <1.21.11 {
    /*
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!status.isEmpty()) {
            graphics.drawString(this.font, status, 8, this.height - 62, 0xFF80FF80);
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
            extractor.text(this.font, status, 8, this.height - 66, 0xFF80FF80);
        }
    }
    //?}

    //? if >=26.1 {
    private final class ScriptList extends ObjectSelectionList<ScriptList.Row> {

        ScriptList() {
            super(Minecraft.getInstance(), ScriptManagerScreen.this.width,
                    ScriptManagerScreen.this.height - 76, 24, 16);
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

        /** One display row: script rows open the editor on click. */
        final class Row extends ObjectSelectionList.Entry<Row> {
            final RowData data;

            Row(RowData data) {
                this.data = data;
            }

            @Override
            public Component getNarration() {
                return Component.literal(data.title() + " " + data.state());
            }

            //? if <1.21.11 {
            /*
            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                drawRow(graphics, left, top, hovered);
            }
            */
            //?}
//? if >=1.21.11 {
            @Override
            public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                    boolean hovered, float partialTick) {
                drawRow(extractor, getX(), getY(), hovered);
            }
//?}

            private void drawRow(Object graphics, int left, int top, boolean hovered) {
                int color = hovered && !data.header() ? 0xFFFFFFA0 : 0xFFFFFFFF;
                if (data.header()) {
                    text(graphics, data.title(), left + 4, top + 2, 0xFF80D0FF);
                } else if (data.entryFile() == null) {
                    text(graphics, data.title(), left + 12, top + 2, 0xFFFF6060);
                    text(graphics, data.state(), left + getRowWidth() / 2, top + 2, 0xFFFF8080);
                } else {
                    text(graphics, data.title(), left + 12, top + 2, color);
                    text(graphics, data.state(), left + getRowWidth() - 160, top + 2,
                            data.untrusted() ? 0xFFFF8080 : 0xFF80C080);
                }
            }

            /** Version-agnostic text draw helper over the two graphics APIs. */
            private void text(Object graphics, String line, int x, int y, int color) {
                //? if <1.21.11 {
                /*
                ((GuiGraphics) graphics).drawString(font, line, x, y, color);
                */
                //?}
                //? if >=1.21.11 {
                ((GuiGraphicsExtractor) graphics).text(font, line, x, y, color);
                //?}
            }

            @Override
            public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
                if (data.entryFile() != null) {
                    ScriptEditorScreen.open(data.entryFile(), onReload);
                }
                return true;
            }
        }
    }
    //?}
}
