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

        api.drawBox("mark", new Pos3d(0, 64, 0), new Pos3d(1, 65, 1), 0xFF00FF00);
        api.drawLine("path", new Pos3d(0, 64, 0), new Pos3d(5, 64, 5), 0xFFFFFFFF);

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
}
