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

/**
 * Read-only text viewer screen for one script source or the recent error
 * list; the error presentation chain lands here via clickable chat commands.
 */
public final class ScriptScreen extends Screen {

    private final String content;

    private ScriptScreen(Component title, String content) {
        super(title);
        this.content = content;
    }

    /** Opens the viewer on the client thread. */
    public static void open(String title, String content) {
        Component heading = Component.translatable("neomacros.ui.viewer.title", title);
        //? if <1.21.11 {
        /*
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(
                new ScriptScreen(heading, content)));
        */
        //?}
//? if >=1.21.11 {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(
                new ScriptScreen(heading, content)));
//?}
    }

    @Override
    protected void init() {
        int margin = 8;
        int boxWidth = this.width - margin * 2;
        int boxHeight = this.height - 40;
        MultiLineEditBox box = MultiLineEditBox.builder()
                .setX(margin)
                .setY(margin)
                .build(this.font, boxWidth, boxHeight, Component.literal(""));
        box.setValue(content);
        addRenderableWidget(box);

        addRenderableWidget(Button.builder(Component.translatable("neomacros.ui.done"), button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    //? if <1.21.11 {
    /*
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim backdrop first so widget extraction stays on top.
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    */
    //?}
    //? if >=1.21.11 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // Dim backdrop first so widget extraction stays on top.
        extractor.fill(0, 0, this.width, this.height, 0xC0101010);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }
//?}
}
