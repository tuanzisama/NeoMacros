package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.types.CustomImage;
import ink.tuanzi.neomacros.core.api.types.Image;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.DialogPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CustomImage canvas: pixel read/write with boundary rejection, the
 * drawing/clip/transform face, PNG file faces over the installed root, and
 * the texture upload contract (construction best-effort plus the awaited
 * {@code update()}), driven through the API over a canned render port.
 */
class CustomImageTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    @TempDir
    Path root;

    private RecordingRender render;

    @BeforeEach
    void install() {
        ScriptApiEnvironment.install(null, null);
        render = null;
        CustomImage.installRoot(path -> {
            Path resolved = root.toAbsolutePath().normalize().resolve(path).normalize();
            if (!resolved.startsWith(root.toAbsolutePath().normalize())) {
                throw new SecurityException("path escapes the image root: " + path);
            }
            return resolved;
        });
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
        CustomImage.installRoot(null);
    }

    private static CustomImage image(int width, int height, String name) {
        return (CustomImage) new CustomImage().construct(new Object[]{width, height, name});
    }

    // ---- creation and identity ------------------------------------------

    @Test
    void blankCanvasCarriesNameAndIdentifier() {
        CustomImage img = image(8, 4, "badge");
        assertEquals("badge", img.getName());
        assertEquals("neomacros:image/badge", img.getIdentifier());
        assertEquals(8, img.getWidth());
        assertEquals(4, img.getHeight());
    }

    @Test
    void invalidNamesAndDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> image(4, 4, "Badge"));
        assertThrows(IllegalArgumentException.class, () -> image(4, 4, ""));
        assertThrows(IllegalArgumentException.class, () -> image(4, 4, "with:colon"));
        assertThrows(IllegalArgumentException.class, () -> image(0, 4, "ok"));
        assertThrows(IllegalArgumentException.class, () -> image(4, -1, "ok"));
        assertThrows(IllegalArgumentException.class,
                () -> new CustomImage().construct(new Object[]{"missing.png", "name"}));
        assertThrows(IllegalArgumentException.class,
                () -> new CustomImage().construct(new Object[]{4}));
    }

    // ---- pixel boundaries ------------------------------------------------

    @Test
    void pixelReadWriteRoundTripsAtCorners() {
        CustomImage img = image(4, 3, "pixels");
        img.setPixel(0, 0, 0x80112233);
        img.setPixel(3, 2, 0xFFAABBCC);
        assertEquals(0x80112233, img.getPixel(0, 0));
        assertEquals(0xFFAABBCC, img.getPixel(3, 2));
        assertEquals(0, img.getPixel(1, 1), "untouched pixels stay transparent");
    }

    @Test
    void pixelAccessOutsideTheCanvasThrows() {
        CustomImage img = image(4, 3, "bounds");
        int[][] outside = {{4, 0}, {0, 3}, {-1, 0}, {0, -1}, {4, 3}, {5, 5}};
        for (int[] point : outside) {
            int x = point[0];
            int y = point[1];
            assertThrows(IndexOutOfBoundsException.class, () -> img.getPixel(x, y),
                    "getPixel(" + x + ", " + y + ") must be rejected");
            assertThrows(IndexOutOfBoundsException.class, () -> img.setPixel(x, y, RED),
                    "setPixel(" + x + ", " + y + ") must be rejected");
        }
    }

    // ---- graphics state --------------------------------------------------

    @Test
    void graphicsColorIgnoresAlphaAndRoundTrips() {
        CustomImage img = image(2, 2, "color");
        assertTrue((img.getGraphicsColor() >>> 24) == 0xFF, "graphics color stays opaque");
        img.setGraphicsColor(0x00FF00FF);
        assertEquals(0xFFFF00FF, img.getGraphicsColor(), "alpha bits are ignored");
        img.fillRect(0, 0, 1, 1);
        assertEquals(0xFFFF00FF, img.getPixel(0, 0));
    }

    @Test
    void translateShiftsSubsequentDrawingOnly() {
        CustomImage img = image(4, 4, "shift");
        img.setGraphicsColor(RED);
        img.translate(1, 1).fillRect(0, 0, 1, 1);
        assertEquals(0xFFFF0000, img.getPixel(1, 1));
        assertNotEquals(0xFFFF0000, img.getPixel(0, 0), "existing pixels stay put");
    }

    @Test
    void clipRectIntersectsAndSetClipReplaces() {
        CustomImage img = image(8, 8, "clip");
        assertNull(img.getClipBounds(), "no clip starts as undefined");

        img.setClip(0, 0, 2, 2);
        assertEquals(0, img.getClipBounds().x());
        assertEquals(2, img.getClipBounds().width());

        img.clipRect(1, 1, 5, 5);
        var intersection = img.getClipBounds();
        assertEquals(1, intersection.x());
        assertEquals(1, intersection.width(), "clipRect intersects, never grows");

        img.setGraphicsColor(RED);
        img.fillRect(0, 0, 8, 8);
        assertEquals(0xFFFF0000, img.getPixel(1, 1), "inside the clip");
        assertEquals(0, img.getPixel(0, 0), "outside the clip stays untouched");
        assertEquals(0, img.getPixel(3, 3));
    }

    @Test
    void clearRectColorOverloadRestoresThePreviousBackground() {
        CustomImage img = image(4, 4, "clear");
        CustomImage fresh = image(4, 4, "fresh");
        fresh.clearRect(0, 0, 1, 1);

        img.setGraphicsColor(RED);
        img.fillRect(0, 0, 4, 4);
        img.clearRect(1, 1, 1, 1, (long) BLUE);
        assertEquals(BLUE, img.getPixel(1, 1));
        assertEquals(0xFFFF0000, img.getPixel(0, 0), "the rest of the fill stays");

        img.clearRect(2, 2, 1, 1);
        assertEquals(fresh.getPixel(0, 0), img.getPixel(2, 2),
                "the color overload restores the previous background");
        assertTrue((img.getPixel(2, 2) >>> 24) == 0xFF, "clearRect paints an opaque background");
    }

    @Test
    void paintModeToggleAfterXorModeRestoresExactFills() {
        CustomImage img = image(4, 4, "modes");
        img.setGraphicsColor(WHITE);
        img.setXorMode(0x00FFFFFF);
        img.fillRect(0, 0, 1, 1);
        img.setPaintMode();
        img.fillRect(1, 0, 1, 1);
        assertEquals(WHITE, img.getPixel(1, 0), "paint mode fills exactly again");
        assertEquals(0, img.getPixel(1, 1), "untouched pixels stay clear");
    }

    @Test
    void copyAreaMovesPixelsByOffset() {
        CustomImage img = image(6, 4, "copy");
        img.setGraphicsColor(RED);
        img.fillRect(0, 0, 2, 2);
        img.copyArea(0, 0, 2, 2, 3, 1);
        assertEquals(0xFFFF0000, img.getPixel(3, 1));
        assertEquals(0xFFFF0000, img.getPixel(4, 2));
        assertEquals(0xFFFF0000, img.getPixel(0, 0), "copyArea keeps the source pixels");
    }

    @Test
    void fillRectExcludesWidthAndHeight() {
        CustomImage img = image(8, 8, "bounds");
        img.setGraphicsColor(RED);
        img.fillRect(1, 1, 2, 2);
        assertEquals(0xFFFF0000, img.getPixel(2, 2));
        assertEquals(0, img.getPixel(3, 3), "fillRect excludes width/height");

        img.drawRect(0, 0, 7, 7);
        assertEquals(0xFFFF0000, img.getPixel(0, 0), "drawRect marks its border");
        assertEquals(0xFFFF0000, img.getPixel(7, 7), "the border reaches x+width");
    }

    @Test
    void polygonAndCurveDrawsPaintInsideTheirShapes() {
        CustomImage fill = image(8, 8, "polyfill");
        fill.setGraphicsColor(RED);
        fill.fillPolygon(new int[]{0, 4, 0}, new int[]{0, 0, 4});
        assertEquals(0xFFFF0000, fill.getPixel(1, 1), "fillPolygon covers its interior");
        assertEquals(0, fill.getPixel(3, 3), "outside the polygon stays clear");

        CustomImage stroke = image(8, 8, "curves");
        stroke.setGraphicsColor(RED);
        stroke.drawPolygonLine(new int[]{0, 7}, new int[]{0, 7});
        assertEquals(0xFFFF0000, stroke.getPixel(0, 0), "polyline starts at the first point");
        stroke.drawPolygon(new int[]{0, 7, 0}, new int[]{0, 0, 7});
        stroke.drawRoundRect(0, 0, 7, 7, 4, 4);
        stroke.fillRoundRect(2, 2, 3, 3, 2, 2);
        stroke.draw3DRect(0, 0, 7, 7, true);
        stroke.fill3DRect(0, 0, 7, 7, false);
        stroke.drawOval(0, 0, 7, 7);
        stroke.fillOval(0, 0, 7, 7);
        stroke.drawArc(0, 0, 7, 7, 0, 90);
        stroke.fillArc(0, 0, 7, 7, 0, 90);
        stroke.drawPolygonLine(new int[]{}, new int[]{0});
        stroke.drawPolygon(null, null);
        stroke.fillPolygon(null, new int[]{1});
        assertTrue((stroke.getPixel(4, 4) >>> 24) == 0xFF, "the filled oval covers the center");
    }

    @Test
    void stringWidthTracksContentAndDrawStringPaints() {
        CustomImage img = image(64, 16, "text");
        assertEquals(0, img.getStringWidth(""));
        assertTrue(img.getStringWidth("hello world") > img.getStringWidth("hi"));

        img.setGraphicsColor(RED);
        img.drawString(1, 12, "hi");
        assertTrue(countPainted(img) > 0, "drawString paints glyph pixels");
        assertThrows(IllegalArgumentException.class, () -> img.drawString(0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> img.getStringWidth(null));
    }

    private static int countPainted(CustomImage img) {
        int painted = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (img.getPixel(x, y) != 0) {
                    painted++;
                }
            }
        }
        return painted;
    }

    // ---- image snapshots and drawImage ----------------------------------

    @Test
    void getImageSnapshotsCurrentPixels() {
        CustomImage img = image(3, 2, "snap");
        img.setPixel(0, 0, 0xFF112233);
        Image snapshot = img.getImage();
        assertEquals(3, snapshot.getWidth());
        assertEquals(2, snapshot.getHeight());

        img.setPixel(1, 1, 0xFF445566);
        Image latest = img.getImage();
        assertEquals(0xFF112233, readPixel(latest, 0, 0));
        assertEquals(0xFF445566, readPixel(latest, 1, 1));
    }

    @Test
    void drawImageCompositesWholeAndCroppedSource() {
        CustomImage source = image(4, 4, "src");
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                source.setPixel(x, y, x < 2 ? RED : BLUE);
            }
        }
        Image payload = source.getImage();

        CustomImage target = image(6, 6, "dst");
        target.drawImage(payload, 1, 1, 2, 2);
        assertEquals(0xFFFF0000, target.getPixel(1, 1), "left column samples the red half");
        assertEquals(0xFF0000FF, target.getPixel(2, 1), "right column samples the blue half");
        assertEquals(0, target.getPixel(3, 3), "scaled draw stays in its box");

        target.drawImage(payload, 0, 0, 2, 2, 2, 0, 2, 2);
        assertEquals(0xFF0000FF, target.getPixel(0, 0), "source rectangle is honored");
        assertThrows(IllegalArgumentException.class,
                () -> target.drawImage(null, 0, 0, 1, 1));
    }

    @Test
    void nativeARGBFlipSwapsRedAndBlue() {
        CustomImage img = image(1, 1, "flip");
        assertEquals(0x11443322, img.nativeARGBFlip(0x11223344));
        assertEquals(0x00000000, img.nativeARGBFlip(0x00000000));
        assertEquals(0xFFFFFFFF, img.nativeARGBFlip(0xFFFFFFFF));
    }

    // ---- file faces ------------------------------------------------------

    @Test
    void saveImageWritesPngAndLoadImageReadsItBack() throws Exception {
        CustomImage img = image(5, 4, "file");
        img.setPixel(0, 0, 0xFF123456);
        img.saveImage("shots", "frame");

        Path written = root.resolve("shots/frame.png");
        assertTrue(Files.exists(written), "saveImage appends .png below the root");
        BufferedImage decoded = ImageIO.read(written.toFile());
        assertEquals(5, decoded.getWidth());
        assertEquals(4, decoded.getHeight());
        assertEquals(0xFF123456, decoded.getRGB(0, 0));

        Image loaded = img.loadImage("shots/frame.png");
        assertEquals(5, loaded.getWidth());
        assertEquals(4, loaded.getHeight());
        assertThrows(IndexOutOfBoundsException.class,
                () -> img.loadImage("shots/frame.png", 4, 0, 2, 4));
        Image crop = img.loadImage("shots/frame.png", 1, 1, 2, 2);
        assertEquals(2, crop.getWidth());
        assertEquals(2, crop.getHeight());
        assertNull(img.loadImage("shots/absent.png"), "missing files read as undefined");
        assertThrows(SecurityException.class, () -> img.loadImage("../escape.png"));
    }

    @Test
    void constructFromFileUsesFileDimensions() throws Exception {
        CustomImage seed = image(6, 2, "seed");
        seed.saveImage("", "seed");
        CustomImage loaded = (CustomImage) new CustomImage()
                .construct(new Object[]{"seed.png", "fromfile"});
        assertEquals(6, loaded.getWidth());
        assertEquals(2, loaded.getHeight());
        assertEquals("neomacros:image/fromfile", loaded.getIdentifier());
    }

    // ---- texture registration -------------------------------------------

    @Test
    void constructionRegistersTextureAndUpdateReuploads() throws Exception {
        render = new RecordingRender();
        ScriptApiEnvironment.install(null, new RenderOnlyAdapter(render));

        CustomImage img = image(4, 2, "upload");
        assertEquals(1, render.uploads.size(), "construction uploads best-effort");
        RecordingRender.Upload first = render.uploads.get(0);
        assertEquals("neomacros:image/upload", first.id());
        assertEquals(4, first.width());
        assertEquals(2, first.height());

        img.setPixel(0, 0, 0xFF0A0B0C);
        CustomImage settled = img.update().get(1, TimeUnit.SECONDS);
        assertSame(img, settled, "update resolves to the same image for chaining");
        assertEquals(2, render.uploads.size(), "update() is the awaited second upload");
        RecordingRender.Upload second = render.uploads.get(1);
        assertEquals(0xFF0A0B0C, second.argb()[0]);
        assertEquals("neomacros:image/upload", second.id());
        assertEquals(List.of("neomacros:image/upload"), render.textureIds().get());
    }

    @Test
    void updateWithoutRenderEnvironmentRejects() {
        CustomImage img = image(1, 1, "noenv");
        ScriptApiEnvironment.install(null, null);
        assertThrows(IllegalStateException.class, img::update);
    }

    // ---- helpers ---------------------------------------------------------

    private static int readPixel(Image img, int x, int y) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(img.getBytes()))
                    .getRGB(x, y);
        } catch (Exception e) {
            throw new AssertionError("snapshot must decode", e);
        }
    }

    /** Render port that records texture uploads; layers stay inert. */
    private static final class RecordingRender implements RenderPort {
        private record Upload(String id, int width, int height, int[] argb) {
        }

        private final List<Upload> uploads = new CopyOnWriteArrayList<>();

        @Override
        public void addLayer(String id, int z,
                BiConsumer<Integer, ink.tuanzi.neomacros.port.model.DrawContext2d> painter) {
            // layer painting is out of scope for this canvas test
        }

        @Override
        public void removeLayer(String id) {
            // layer painting is out of scope for this canvas test
        }

        @Override
        public void setLayerZ(String id, int z) {
            // layer painting is out of scope for this canvas test
        }

        @Override
        public Supplier<List<String>> layerIds() {
            return List::of;
        }

        @Override
        public CompletableFuture<Void> putTexture(String id, int width, int height, int[] argb) {
            uploads.add(new Upload(id, width, height, argb.clone()));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Supplier<List<String>> textureIds() {
            return () -> uploads.stream().map(Upload::id).distinct().toList();
        }
    }

    /** Adapter serving only the render port; the rest stay unreachable. */
    private record RenderOnlyAdapter(RecordingRender recording) implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityPort entity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlayerPort player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            return recording;
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
