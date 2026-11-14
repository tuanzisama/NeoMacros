package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.core.api.render.RecordingHudRender;
import ink.tuanzi.neomacros.core.api.types.Image;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.ItemStackBuilder;
import ink.tuanzi.neomacros.port.model.WindowData;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-034B component faces over a recording port: subclass upserts for
 * {@code Draw2DImage}/{@code Draw2DItem}, the component-specific state
 * (texture/region view, snapshot upload, stack and overlay faces), the
 * paint primitives they emit (tinted sprite, item icon, transform scopes)
 * and the disposal contract.
 */
class Draw2DImageItemTest {

    private static final WindowData WINDOW = new WindowData(640, 360, 640, 360);

    private final RecordingHudRender render = new RecordingHudRender();

    private HudLayer newLayer(String id) {
        return new HudLayer(id, id, 0, render, Runnable::run, () -> WINDOW);
    }

    /** A tiny opaque 2x2 PNG snapshot for the {@code image:} face. */
    private static Image pngSnapshot() {
        BufferedImage surface = new BufferedImage(2, 2,
                BufferedImage.TYPE_INT_ARGB);
        surface.setRGB(0, 0, 0xFF112233);
        surface.setRGB(1, 0, 0xFF445566);
        surface.setRGB(0, 1, 0xFF778899);
        surface.setRGB(1, 1, 0xFFAABBCC);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(surface, "png", bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new Image(2, 2, bytes.toByteArray());
    }

    @Test
    void upsertReturnsTheComponentSubclass() {
        HudLayer layer = newLayer("main");

        Draw2DImage image = layer.image("i", Map.of(
                "x", 1, "y", 2, "w", 16, "h", 16, "texture", "badge"));
        Draw2DItem item = layer.item("t", "minecraft:stone", Map.of());

        assertInstanceOf(Draw2DElement.class, image);
        assertInstanceOf(Draw2DElement.class, item);
        assertSame(image, layer.element("i"), "the element face hands back the subclass");
        assertSame(item, layer.element("t"));
        assertEquals(List.of("i", "t"), layer.getIds());
    }

    @Test
    void imageTextureRegionAndColorRoundTrip() {
        HudLayer layer = newLayer("main");
        Draw2DImage image = layer.image("i", Map.of(
                "x", 0, "y", 0, "w", 32, "h", 32, "texture", "badge",
                "region", Map.of("x", 4, "y", 5, "w", 6, "h", 7),
                "textureWidth", 64, "textureHeight", 64,
                "color", 0x80FF0000));

        assertEquals("minecraft:badge", image.getImage(),
                "a bare name takes the minecraft namespace (JsMacros parity)");
        assertEquals(4, image.getImageX());
        assertEquals(5, image.getImageY());
        assertEquals(6, image.getRegionWidth());
        assertEquals(7, image.getRegionHeight());
        assertEquals(64, image.getTextureWidth());
        assertEquals(64, image.getTextureHeight());
        assertEquals(0x80FF0000, image.getColor());
        assertEquals(0x80, image.getAlpha());

        assertSame(image, image.setImage("neomacros:image/badge", 1, 2, 3, 4,
                256, 256));
        assertEquals("neomacros:image/badge", image.getImage());
        assertEquals(1, image.getImageX());
        assertEquals(2, image.getImageY());
        assertEquals(3, image.getRegionWidth());
        assertEquals(4, image.getRegionHeight());
        assertEquals(256, image.getTextureWidth());
        assertEquals(256, image.getTextureHeight());
        assertEquals(32, image.getWidth(), "setImage leaves the destination size alone");

        // The folded color face: packed, color+alpha, rgb, rgba, string.
        image.setColor(0x11223344);
        assertEquals(0x11223344, image.getColor());
        image.setColor(0x00AABBCC, 0x80);
        assertEquals(0x80AABBCC, image.getColor());
        image.setColor(0x10, 0x20, 0x30);
        assertEquals(0xFF102030, image.getColor());
        image.setColor(0x10, 0x20, 0x30, 0x40);
        assertEquals(0x40102030, image.getColor());
        image.setColor("#80ff0000");
        assertEquals(0x80FF0000, image.getColor());

        // A null name clears the texture (the element then draws nothing).
        image.setImage(null, 0, 0, 0, 0, 256, 256);
        assertNull(image.getImage());
    }

    @Test
    void imageSnapshotUploadRegistersATexture() {
        HudLayer layer = newLayer("main");
        Image snapshot = pngSnapshot();

        Draw2DImage image = layer.image("s", Map.of(
                "x", 0, "y", 0, "w", 2, "h", 2, "image", snapshot));

        assertEquals(1, render.uploads().size(), "the snapshot uploads once");
        String upload = render.uploads().get(0);
        assertTrue(upload.endsWith(":2x2"), "upload carries the snapshot size: " + upload);
        String textureId = upload.substring(0, upload.length() - ":2x2".length());
        assertTrue(textureId.startsWith("neomacros:image/hud/"),
                "content-addressed id under the texture namespace: " + textureId);
        assertEquals(textureId, image.getImage());
        assertEquals(2, image.getTextureWidth(), "snapshot dims seed the texture dims");
        assertEquals(2, image.getTextureHeight());

        // Re-upserting the same content never re-uploads.
        layer.image("s", Map.of(
                "x", 0, "y", 0, "w", 2, "h", 2, "image", snapshot));
        assertEquals(1, render.uploads().size(), "identical snapshots share one upload");

        // An explicit texture id wins over the snapshot and skips the upload.
        Draw2DImage live = layer.image("live", Map.of(
                "x", 0, "y", 0, "w", 8, "h", 8,
                "texture", "neomacros:image/badge", "image", snapshot));
        assertEquals("neomacros:image/badge", live.getImage());
        assertEquals(1, render.uploads().size(), "texture: short-circuits the snapshot");
    }

    @Test
    void imagePaintEmitsTheSpritePrimitive() {
        HudLayer layer = newLayer("main");
        layer.image("i", Map.of(
                "x", 10, "y", 20, "w", 32, "h", 32,
                "texture", "neomacros:image/badge",
                "color", 0x80FF0000,
                "region", Map.of("x", 4, "y", 5, "w", 6, "h", 7)));

        assertEquals(List.of(
                "sprite:10,20,32,32,neomacros:image/badge,80ff0000,4,5,6,7,256,256"),
                render.frame(0),
                "the image reaches the tinted sprite primitive with its source rect");
    }

    @Test
    void imageRegionFallsBackToTheDestinationExtent() {
        HudLayer layer = newLayer("main");
        layer.image("i", Map.of(
                "x", 0, "y", 0, "w", 16, "h", 8,
                "texture", "neomacros:image/badge"));

        assertEquals(List.of(
                "sprite:0,0,16,8,neomacros:image/badge,ffffffff,0,0,16,8,256,256"),
                render.frame(0),
                "an unset region samples the destination size (JsMacros 2D rule)");
    }

    @Test
    void imageWithNoTexturePaintsNothing() {
        HudLayer layer = newLayer("main");
        layer.image("empty", Map.of("x", 0, "y", 0, "w", 8, "h", 8));

        assertEquals(List.of(), render.frame(0),
                "an image without a texture draws nothing (JsMacros parity)");
    }

    @Test
    void imageRotationScopesThePaint() {
        HudLayer layer = newLayer("main");
        layer.image("i", Map.of(
                "x", 10, "y", 20, "w", 16, "h", 16,
                "texture", "neomacros:image/badge", "rotation", 45));

        assertEquals(List.of(
                "transform:10,20,45,1,1",
                "sprite:10,20,16,16,neomacros:image/badge,ffffffff,0,0,16,16,256,256",
                "transform-pop"),
                render.frame(0),
                "a rotated image draws inside its transform scope");
    }

    @Test
    void itemUpsertSeedsTheBoxAndStack() {
        HudLayer layer = newLayer("main");
        Draw2DItem item = layer.item("t", "stone", Map.of("x", 5, "y", 6));

        assertEquals(5, item.getX());
        assertEquals(6, item.getY());
        assertEquals(16, item.getWidth(), "the icon box is the 16x16 footprint");
        assertEquals(16, item.getHeight());
        assertEquals(16, item.getScaledWidth());
        assertEquals("minecraft:stone", item.getItem().getId(),
                "a bare name takes the minecraft namespace");
        assertEquals(1, item.getItem().getCount());

        assertSame(item, item.setItem("diamond", 5));
        assertEquals("minecraft:diamond", item.getItem().getId());
        assertEquals(5, item.getItem().getCount());

        ItemStack stack = new ItemStackBuilder("stick").count(3).build();
        item.setItem(stack);
        assertEquals("minecraft:stick", item.getItem().getId());
        assertEquals(3, item.getItem().getCount());

        // The reported dimensions follow the scale (JsMacros parity).
        item.setScale(2);
        assertEquals(32, item.getScaledWidth());
        assertEquals(2f, item.getScale());

        // The stackless options-bag form: layer.item(id, opts) — the bag
        // rides the stack slot and opts stays null (the engine fills the
        // omitted third argument with null for scripts).
        Draw2DItem bare = layer.item("bare", Map.of("x", 7, "y", 8), null);
        assertEquals(7, bare.getX());
        assertNull(bare.getItem(), "no stack answers undefined");
    }

    @Test
    void itemOverlayFaces() {
        HudLayer layer = newLayer("main");
        Draw2DItem item = layer.item("t", "minecraft:stone", Map.of());

        assertEquals(false, item.shouldShowOverlay(), "decorations default off");
        assertSame(item, item.setOverlay(true));
        assertEquals(true, item.shouldShowOverlay());

        // setOverlayText does not enable the overlay (element contract).
        assertSame(item, item.setOverlayText("x64"));
        assertEquals(true, item.shouldShowOverlay());
        assertEquals("x64", item.getOverlayText());
        item.setOverlay(false);
        assertEquals(false, item.shouldShowOverlay(),
                "the label alone never turns decorations on");
        item.setOverlayText(null);
        assertNull(item.getOverlayText(), "null falls back to the stack count");
    }

    @Test
    void itemPaintEmitsTheItemPrimitive() {
        HudLayer layer = newLayer("main");
        layer.item("counted", "minecraft:stone", Map.of(
                "x", 10, "y", 20, "overlay", true));
        layer.item("labeled", "minecraft:dirt", Map.of(
                "x", 30, "y", 40, "overlay", true, "overlayText", "abc"));
        layer.item("bare", "minecraft:stick", Map.of("x", 50, "y", 60));
        layer.item("empty", null, Map.of("x", 70, "y", 80));

        assertEquals(List.of(
                "item:10,20,minecraft:stone,1,true",
                "item:30,40,minecraft:dirt,1,true,abc",
                "item:50,60,minecraft:stick,1,false"),
                render.frame(0),
                "overlay rides the primitive, a null stack draws nothing");
    }

    @Test
    void itemScaleScopesThePaint() {
        HudLayer layer = newLayer("main");
        layer.item("t", "minecraft:stone", Map.of(
                "x", 10, "y", 20, "scale", 2));

        assertEquals(List.of(
                "transform:10,20,0,2,2",
                "item:10,20,minecraft:stone,1,false",
                "transform-pop"),
                render.frame(0),
                "the scaled icon draws inside its transform scope");
    }

    @Test
    void componentFluentChainsReturnTheComponentInstance() {
        HudLayer layer = newLayer("main");

        Draw2DImage image = layer.image("i", Map.of(
                "x", 0, "y", 0, "w", 8, "h", 8));
        assertSame(image, image.setImage("a:b", 1, 2, 3, 4, 5, 6)
                .setColor(0x00FF00).setZIndex(3));
        assertEquals("a:b", image.getImage());
        assertEquals(3, image.getZIndex());

        Draw2DItem item = layer.item("t", "minecraft:stone", Map.of());
        assertSame(item, item.setItem("diamond", 2).setOverlay(true)
                .setOverlayText("x2").setZIndex(4));
        assertEquals("minecraft:diamond", item.getItem().getId());
        assertEquals(true, item.shouldShowOverlay());
        assertEquals("x2", item.getOverlayText());
        assertEquals(4, item.getZIndex());
    }

    @Test
    void componentMutatorsFollowTheDisposalContract() {
        HudLayer layer = newLayer("main");
        Draw2DImage image = layer.image("i", Map.of(
                "x", 0, "y", 0, "w", 8, "h", 8, "texture", "a:b"));
        Draw2DItem item = layer.item("t", "minecraft:stone", Map.of());

        layer.remove("i");
        layer.remove("t");

        assertTrue(image.isDisposed());
        assertThrows(IllegalStateException.class,
                () -> image.setImage("a:b", 0, 0, 0, 0, 1, 1));
        assertThrows(IllegalStateException.class, () -> image.setColor(0xFFFFFF));
        assertNull(image.getImage());
        assertNull(image.getImageX());
        assertNull(image.getRegionWidth());
        assertNull(image.getColor());
        assertNull(image.getAlpha());

        assertTrue(item.isDisposed());
        assertThrows(IllegalStateException.class,
                () -> item.setItem("diamond", 1));
        assertThrows(IllegalStateException.class, () -> item.setOverlay(true));
        assertThrows(IllegalStateException.class, () -> item.setOverlayText("x"));
        assertNull(item.getItem());
        assertNull(item.shouldShowOverlay());
        assertNull(item.getOverlayText());
    }

    @Test
    void kindReplacementSwapsTheComponentHandle() {
        HudLayer layer = newLayer("main");
        Draw2DImage image = layer.image("e", Map.of(
                "x", 0, "y", 0, "w", 8, "h", 8, "texture", "a:b"));

        Draw2DItem item = layer.item("e", "minecraft:stone", Map.of());

        assertTrue(image.isDisposed(), "the kind change disposes the old handle");
        assertNull(image.getImage());
        assertSame(item, layer.element("e"));
        assertThrows(IllegalStateException.class,
                () -> image.setColor(0xFFFFFF));

        // And back the other way.
        Draw2DImage again = layer.image("e", Map.of(
                "x", 0, "y", 0, "w", 8, "h", 8));
        assertTrue(item.isDisposed());
        assertSame(again, layer.element("e"));
    }
}
