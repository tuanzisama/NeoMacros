package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RenderApi over a recording port: shape delegation, per-layer clearing, and
 * layer id derivation.
 */
class RenderApiTest {

    /** Shape recorder standing in for the render port. */
    static final class RecordingDraw3D implements Draw3DPort {
        record Shape(String kind, String layerId, Pos3d a, Pos3d b, int color) {
        }

        final List<Shape> shapes = new CopyOnWriteArrayList<>();

        @Override
        public void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor) {
            shapes.add(new Shape("box", layerId, min, max, argbColor));
        }

        @Override
        public void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor) {
            shapes.add(new Shape("line", layerId, from, to, argbColor));
        }

        @Override
        public void addTraceLine(String layerId, java.util.UUID entityUuid, int argbColor) {
            shapes.add(new Shape("trace", layerId,
                    new Pos3d(0, 0, 0), new Pos3d(0, 0, 0), argbColor));
        }

        @Override
        public void drawSurface(String layerId, java.util.List<Pos3d> points, int argbColor) {
            shapes.add(new Shape("surface", layerId,
                    points.isEmpty() ? new Pos3d(0, 0, 0) : points.get(0),
                    points.size() > 1 ? points.get(points.size() - 1) : new Pos3d(0, 0, 0),
                    argbColor));
        }

        @Override
        public void clearLayer(String layerId) {
            shapes.removeIf(shape -> shape.layerId().equals(layerId));
        }

        @Override
        public Supplier<List<String>> layerIds() {
            return () -> shapes.stream().map(Shape::layerId).distinct().toList();
        }
    }

    @Test
    void delegatesShapesAndClearsPerLayer() {
        RecordingDraw3D backing = new RecordingDraw3D();
        RenderApi api = new RenderApi(backing);

        api.drawBox("mark", new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 64, 0), new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 65, 1), 0xFF00FF00);
        api.drawLine("path", new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 64, 0), new ink.tuanzi.neomacros.core.api.types.Pos3D(5, 64, 5), 0xFFFFFFFF);

        assertEquals(2, backing.shapes.size());
        assertEquals("box", backing.shapes.get(0).kind());
        assertEquals(0xFF00FF00, backing.shapes.get(0).color());
        assertEquals("line", backing.shapes.get(1).kind());
        assertEquals(List.of("mark", "path"), api.layerIds());

        api.clearLayer("mark");
        assertEquals(1, backing.shapes.size());
        assertEquals("line", backing.shapes.get(0).kind());
        assertEquals("path", backing.shapes.get(0).layerId());
        assertEquals(List.of("path"), api.layerIds());
    }

    @Test
    void clearUnknownLayerIsNoop() {
        RecordingDraw3D backing = new RecordingDraw3D();
        RenderApi api = new RenderApi(backing);
        api.clearLayer("missing");
        assertTrue(backing.shapes.isEmpty());
        assertEquals(List.of(), api.layerIds());
    }

    @Test
    void surfaceNeedsThreePointsAndDelegates() {
        RecordingDraw3D backing = new RecordingDraw3D();
        RenderApi api = new RenderApi(backing);

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> api.surface("tri", java.util.List.of(
                        new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 0, 0),
                        new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 0, 0)), 0xFFFF0000)) != null);

        api.surface("tri", java.util.List.of(
                new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 64, 0),
                new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 64, 0),
                new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 65, 1)), 0x80FFFFFF);
        assertEquals(1, backing.shapes.size());
        assertEquals("surface", backing.shapes.get(0).kind());
        assertEquals(0x80FFFFFF, backing.shapes.get(0).color());
    }

    private static IllegalArgumentException assertThrows(Class<IllegalArgumentException> type,
            Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException error) {
            return error;
        }
        return null;
    }
}
