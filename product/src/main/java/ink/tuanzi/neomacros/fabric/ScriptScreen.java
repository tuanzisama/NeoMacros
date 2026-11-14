package ink.tuanzi.neomacros.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import ink.tuanzi.neomacros.adapter.FabricScreenPort;
import ink.tuanzi.neomacros.port.ScreenPort;
import net.minecraft.client.Minecraft;
//? if <1.21.11 {
/*
import net.minecraft.client.gui.GuiGraphics;
*/
//?}
//? if >=1.21.11 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}
//? if >=26.1 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Host screen for sessions opened through {@code player.buildScreen}: the
 * vanilla lifecycle hooks (init/removed/onClose/shouldCloseOnEsc/
 * isPauseScreen) forward into the session sink, the render chain runs
 * backdrop → widgets → optional title → the script's {@code onRender}, and
 * screen-level input falls through to the sink only when no widget (nor
 * Esc) consumed it. Field-level flags mirror the declarative spec:
 * {@link #drawTitle}, {@link #shouldPause}, {@link #shouldCloseOnEsc}.
 */
public final class ScriptScreen extends Screen {

    private final FabricScreenPort port;
    private final long sessionId;
    private final ScreenPort.SessionSink sink;

    /** Draw the centered title after the widgets ({@code showTitle}). */
    public boolean drawTitle = true;

    /** Whether the game pauses behind this screen ({@code pause}). */
    public boolean shouldPause = false;

    /** Whether Esc closes this screen ({@code closeOnEsc}). */
    public boolean shouldCloseOnEsc = true;

    private final String returnTo;
    private final Screen parent;

    /** Wires one opened session (created by the port before the switch). */
    public ScriptScreen(FabricScreenPort port, long sessionId,
                        ScreenPort.SessionSink sink, String title,
                        String returnTo, Screen parent) {
        super(Component.literal(title != null ? title : ""));
        this.port = port;
        this.sessionId = sessionId;
        this.sink = sink;
        this.returnTo = returnTo != null ? returnTo : "current";
        this.parent = parent;
    }

    /** Adds one built widget (the port lives outside Screen's package). */
    public void attach(AbstractWidget widget) {
        addRenderableWidget(widget);
    }

    /** Rebuilds the widget tree (clear + init, adopting live state first). */
    public void rebuild() {
        rebuildWidgets();
    }

    /** The screen font for widget constructors on the port side. */
    public net.minecraft.client.gui.Font screenFont() {
        return this.font;
    }

    /** Close intent: the same path Esc takes (callback, then return). */
    public void requestClose() {
        port.closeIntent(sessionId);
    }

    @Override
    protected void init() {
        port.buildWidgets(this, sessionId);
    }

    @Override
    protected void clearWidgets() {
        // Adopt live widget state into the spec mirrors before the tree is
        // torn down, so resize/reload/rebuild never lose typed input.
        port.adoptLiveState(sessionId);
        super.clearWidgets();
    }

    @Override
    public void removed() {
        super.removed();
        // Replaced by another screen (vanilla/other script): notify only —
        // the replacement already owns the display, so no parent restore.
        port.replaced(sessionId);
    }

    @Override
    public void onClose() {
        requestClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return shouldCloseOnEsc;
    }

    @Override
    public boolean isPauseScreen() {
        return shouldPause;
    }

    // ------------------------------------------------------------------
    // Input: widgets first (super), then the screen-level sink
    // ------------------------------------------------------------------

    //? if >=26.1 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        return sink.mouseDown(new ScreenPort.MouseInput(event.button(), event.x(),
                event.y(), 0, 0, modifiers(), doubleClick ? Boolean.TRUE : null));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (super.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return sink.mouseDrag(new ScreenPort.MouseInput(event.button(), event.x(),
                event.y(), dragX, dragY, modifiers(), null));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (super.mouseReleased(event)) {
            return true;
        }
        return sink.mouseUp(new ScreenPort.MouseInput(event.button(), event.x(),
                event.y(), 0, 0, modifiers(), null));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        InputConstants.Key key = InputConstants.getKey(event);
        return sink.keyPress(new ScreenPort.KeyInput(key.getName(), key.getValue(),
                modifiers()));
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) {
            return true;
        }
        return sink.charTyped(new ScreenPort.CharInput(event.codepointAsString()));
    }
    //?} else {
    /*
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return sink.mouseDown(new ScreenPort.MouseInput(button, mouseX, mouseY,
                0, 0, modifiers(), null));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return sink.mouseDrag(new ScreenPort.MouseInput(button, mouseX, mouseY,
                dragX, dragY, modifiers(), null));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return sink.mouseUp(new ScreenPort.MouseInput(button, mouseX, mouseY,
                0, 0, modifiers(), null));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        return sink.keyPress(new ScreenPort.KeyInput(key.getName(), key.getValue(),
                modifiers()));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        }
        return sink.charTyped(new ScreenPort.CharInput(String.valueOf(codePoint)));
    }
    */
    //?}

    /** The held modifier names in the shared {@code shift/ctrl/alt} vocabulary. */
    private List<String> modifiers() {
        //? if >=26.1 {
        Minecraft minecraft = Minecraft.getInstance();
        List<String> names = new ArrayList<>(3);
        if (minecraft.hasShiftDown()) {
            names.add("shift");
        }
        if (minecraft.hasControlDown()) {
            names.add("ctrl");
        }
        if (minecraft.hasAltDown()) {
            names.add("alt");
        }
        return names;
        //?} else {
        /*
        List<String> names = new ArrayList<>(3);
        if (Screen.hasShiftDown()) {
            names.add("shift");
        }
        if (Screen.hasControlDown()) {
            names.add("ctrl");
        }
        if (Screen.hasAltDown()) {
            names.add("alt");
        }
        return names;
        */
        //?}
    }

    // ------------------------------------------------------------------
    // Render chain: backdrop → widgets → title → script onRender
    // ------------------------------------------------------------------

    //? if <1.21.11 {
    /*
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim backdrop first so widget extraction stays on top (T-301).
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (drawTitle) {
            String text = this.title.getString();
            graphics.drawString(this.font, text,
                    (this.width - this.font.width(text)) / 2, 10, 0xFFFFFFFF);
        }
        sink.render(new ink.tuanzi.neomacros.port.model.DrawContext2d() {
            @Override
            public void text(int x, int y, String text) {
                text(x, y, text, 0xFFFFFFFF);
            }

            @Override
            public void text(int x, int y, String text, int color) {
                graphics.drawString(ScriptScreen.this.font, text, x, y, color);
            }

            @Override
            public void rect(int x, int y, int width, int height, int color) {
                graphics.fill(x, y, x + width, y + height, color);
            }

            @Override
            public void outline(int x, int y, int width, int height, int color) {
                graphics.renderOutline(x, y, width, height, color);
            }

            @Override
            public void sprite(int x, int y, int width, int height, String textureId) {
                var texture = net.minecraft.resources.ResourceLocation.tryParse(textureId);
                if (texture != null) {
                    graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                            texture, x, y, 0f, 0f, width, height, width, height);
                }
            }
        }, mouseX, mouseY, partialTick);
    }
    */
    //?}
    //? if >=1.21.11 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // Dim backdrop first so widget extraction stays on top (T-301).
        extractor.fill(0, 0, this.width, this.height, 0xC0101010);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        if (drawTitle) {
            String text = this.title.getString();
            extractor.text(this.font, text,
                    (this.width - this.font.width(text)) / 2, 10, 0xFFFFFFFF);
        }
        sink.render(new ink.tuanzi.neomacros.port.model.DrawContext2d() {
            @Override
            public void text(int x, int y, String text) {
                text(x, y, text, 0xFFFFFFFF);
            }

            @Override
            public void text(int x, int y, String text, int color) {
                extractor.text(ScriptScreen.this.font, text, x, y, color);
            }

            @Override
            public void rect(int x, int y, int width, int height, int color) {
                extractor.fill(x, y, x + width, y + height, color);
            }

            @Override
            public void outline(int x, int y, int width, int height, int color) {
                extractor.outline(x, y, x + width, y + height, color);
            }

            @Override
            public void sprite(int x, int y, int width, int height, String textureId) {
                var texture = net.minecraft.resources.Identifier.tryParse(textureId);
                if (texture != null) {
                    extractor.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                            texture, x, y, 0f, 0f, width, height, width, height);
                }
            }
        }, mouseX, mouseY, partialTick);
    }
//?}
}
