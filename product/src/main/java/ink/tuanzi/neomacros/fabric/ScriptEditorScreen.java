package ink.tuanzi.neomacros.fabric;

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
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Editable script editor: loads one file below the scripts directory, lets
 * the player change its source, and saves back to disk with an optional
 * immediate reload callback. Saving a file that does not exist yet creates
 * it, so the same screen also serves "new script".
 */
public final class ScriptEditorScreen extends Screen {

    private final Path file;
    private final String initialContent;
    private final Runnable onSaved;
    private String status = "";
    private MultiLineEditBox editor;

    private ScriptEditorScreen(Path file, String initialContent, Runnable onSaved) {
        super(Component.translatable("neomacros.ui.editor.title", file.getFileName().toString()));
        this.file = file;
        this.initialContent = initialContent;
        this.onSaved = onSaved;
    }

    /**
     * Opens the editor on the client thread; the screen reads the file
     * itself when the content is not preloaded.
     */
    public static void open(Path file, Runnable onSaved) {
        String content;
        try {
            content = Files.exists(file)
                    ? Files.readString(file, StandardCharsets.UTF_8)
                    : "// new NeoMacros script\nexport function onEvent(ev) { }\n";
        } catch (IOException e) {
            content = "// cannot read: " + e.getMessage() + "\n";
        }
        Path finalFile = file;
        String finalContent = content;
        //? if <1.21.11 {
        /*
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(
                new ScriptEditorScreen(finalFile, finalContent, onSaved)));
        */
        //?}
//? if >=1.21.11 {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(
                new ScriptEditorScreen(finalFile, finalContent, onSaved)));
//?}
    }

    @Override
    protected void init() {
        int margin = 8;
        editor = MultiLineEditBox.builder()
                .setX(margin)
                .setY(margin)
                .build(this.font, this.width - margin * 2, this.height - 56,
                        Component.literal(""));
        editor.setValue(initialContent);
        addRenderableWidget(editor);

        int buttonY = this.height - 40;
        addRenderableWidget(Button.builder(Component.translatable("neomacros.ui.editor.save"), button -> save(false))
                .bounds(margin, buttonY, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("neomacros.ui.editor.save_reload"), button -> save(true))
                .bounds(margin + 88, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("neomacros.ui.done"), button -> onClose())
                .bounds(this.width - margin - 80, buttonY, 80, 20).build());
    }

    /** Writes the buffer back to disk and optionally triggers the reload hook. */
    private void save(boolean reload) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, editor.getValue(), StandardCharsets.UTF_8);
            status = UiText.tr(reload
                    ? "neomacros.ui.editor.saved_reloaded" : "neomacros.ui.editor.saved",
                    file.getFileName().toString());
            if (reload) {
                onSaved.run();
            }
        } catch (IOException e) {
            status = UiText.tr("neomacros.ui.editor.save_failed", e.getMessage());
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
}
