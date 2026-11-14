package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RenderApi over a recording port: the immediate primitives now flow
 * through the keyed {@code putShape} face (one shared layer model), layers
 * are exposed as retained {@link Draw3D} handles, and clearing unknown
 * layers stays a no-op that never materialises a handle.
 */
class RenderApiTest {

    /** Keyed-shape recorder standing in for the render port. */
    static final class RecordingDraw3D implements Draw3DPort {
        record Keyed(String op, String layerId, String shapeId, ShapeSpec spec) {
        }

        final List<Keyed> keyed = new CopyOnWriteArrayList<>();

        @Override
        public void putShape(String layerId, String shapeId, ShapeSpec spec) {
            keyed.add(new Keyed("put", layerId, shapeId, spec));
        }

        @Override
        public void removeShape(String layerId, String shapeId) {
            keyed.add(new Keyed("remove", layerId, shapeId, null));
        }

        @Override
        public void clearLayer(String layerId) {
            keyed.add(new Keyed("clear", layerId, null, null));
        }

        @Override
        public void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor) {
            throw new AssertionError("RenderApi must route primitives through putShape");
        }

        @Override
        public void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor) {
            throw new AssertionError("RenderApi must route primitives through putShape");
        }

        @Override
        public void addTraceLine(String layerId, java.util.UUID entityUuid, int argbColor) {
            throw new AssertionError("RenderApi must route primitives through putShape");
        }

        @Override
        public void drawSurface(String layerId, List<Pos3d> points, int argbColor) {
            throw new AssertionError("RenderApi must route primitives through putShape");
        }

        @Override
        public Supplier<List<String>> layerIds() {
            return () -> keyed.stream().map(Keyed::layerId).distinct().toList();
        }

        /** Live puts of one layer, in call order. */
        List<ShapeSpec> puts(String layerId) {
            return keyed.stream()
                    .filter(entry -> "put".equals(entry.op()) && entry.layerId().equals(layerId))
                    .map(Keyed::spec)
                    .toList();
        }

        boolean hasClear(String layerId) {
            return keyed.stream()
                    .anyMatch(entry -> "clear".equals(entry.op()) && entry.layerId().equals(layerId));
        }
    }

    private static RecordingDraw3D backing() {
        return new RecordingDraw3D();
    }

    @Test
    void delegatesShapesAndClearsPerLayer() {
        RecordingDraw3D backing = backing();
        RenderApi api = new RenderApi(backing);

        api.drawBox("mark", new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 64, 0),
                new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 65, 1), 0xFF00FF00);
        api.drawLine("path", new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 64, 0),
                new ink.tuanzi.neomacros.core.api.types.Pos3D(5, 64, 5), 0xFFFFFFFF);

        assertEquals(2, backing.puts("mark").size() + backing.puts("path").size());
        ShapeSpec.Box box = assertInstanceOf(ShapeSpec.Box.class, backing.puts("mark").get(0));
        assertEquals(0xFF00FF00, box.argbColor());
        assertEquals(0.0, box.min().x());
        assertEquals(1.0, box.max().x());
        ShapeSpec.Line line = assertInstanceOf(ShapeSpec.Line.class, backing.puts("path").get(0));
        assertEquals(0xFFFFFFFF, line.argbColor());
        assertEquals(List.of("mark", "path"),
                api.getLayers().stream().map(Draw3D::getId).toList());

        api.clearLayer("mark");
        assertTrue(backing.hasClear("mark"));
        assertFalse(backing.hasClear("path"));
        // Cleared layers keep their handle — an empty layer is still a layer.
        assertEquals(List.of("mark", "path"),
                api.getLayers().stream().map(Draw3D::getId).toList());
        assertTrue(api.getLayers().stream()
                .filter(layer -> layer.getId().equals("mark"))
                .findFirst().orElseThrow().getElements().isEmpty());
    }

    @Test
    void clearUnknownLayerIsNoopAndCreatesNoHandle() {
        RecordingDraw3D backing = backing();
        RenderApi api = new RenderApi(backing);
        api.clearLayer("missing");
        assertTrue(backing.hasClear("missing"));
        assertTrue(api.getLayers().isEmpty(), "clearing must not materialise a layer");
    }

    @Test
    void surfaceNeedsThreePointsAndDelegates() {
        RecordingDraw3D backing = backing();
        RenderApi api = new RenderApi(backing);

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> api.surface("tri", java.util.List.of(
                        new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 0, 0),
                        new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 0, 0)), 0xFFFF0000)) != null);

        api.surface("tri", java.util.List.of(
                new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 64, 0),
                new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 64, 0),
                new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 65, 1)), 0x80FFFFFF);
        ShapeSpec.Surface surface = assertInstanceOf(ShapeSpec.Surface.class,
                backing.puts("tri").get(0));
        assertEquals(0x80FFFFFF, surface.argbColor());
        assertEquals(3, surface.points().size());
    }

    @Test
    void layerIsIdempotentAndRejectsReservedIds() {
        RenderApi api = new RenderApi(backing());
        Draw3D first = api.layer("shared");
        assertEquals(first, api.layer("shared"));
        assertEquals("shared", first.getId());
        assertThrows(IllegalArgumentException.class, () -> api.layer("$legacy"));
        assertThrows(IllegalArgumentException.class, () -> api.layer(""));
        assertEquals(1, api.getLayers().size());
    }

    private static IllegalArgumentException assertThrows(Class<IllegalArgumentException> type,
            Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException error) {
            return error;
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
}
