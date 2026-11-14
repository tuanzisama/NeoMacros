package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.render.components3d.Box;
import ink.tuanzi.neomacros.core.api.render.components3d.Line3D;
import ink.tuanzi.neomacros.core.api.render.components3d.RenderElement3D;
import ink.tuanzi.neomacros.core.api.types.BlockPos;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The retained Draw3D container over a recording port: element lifecycle
 * (build / buildAndAdd / remove / reAddElement / clear), keyed upsert on
 * mutation, and the Box/Line3D property surface the G-033 member table
 * maps onto.
 */
class Draw3DContainerTest {

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
            putShape(layerId, "r-legacy", new ShapeSpec.Box(min, max, argbColor, 0, false, true));
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
        public java.util.function.Supplier<List<String>> layerIds() {
            return () -> keyed.stream().map(Keyed::layerId).distinct().toList();
        }

        List<Keyed> puts() {
            return keyed.stream().filter(entry -> "put".equals(entry.op())).toList();
        }

        Keyed lastPut() {
            List<Keyed> puts = puts();
            return puts.isEmpty() ? null : puts.get(puts.size() - 1);
        }

        boolean hasOp(String op, String layerId) {
            return keyed.stream().anyMatch(entry ->
                    op.equals(entry.op()) && layerId.equals(entry.layerId()));
        }
    }

    private final RecordingDraw3D backing = new RecordingDraw3D();
    private final RenderApi api = new RenderApi(backing);

    // ---- builder / attachment lifecycle -----------------------------------

    @Test
    void boxBuilderChainAttachesUnderOneKeyAndKeepsItsState() {
        Draw3D layer = api.layer("boxes");
        Box box = layer.boxBuilder()
                .setPos(0, 64, 0, 1, 65, 1)
                .setColor(0x00FF0000)
                .setFillColor(0x0000FF00, 128)
                .setFill(true)
                .cull(true)
                .buildAndAdd();

        assertNotSame(box, layer.boxBuilder()); // fresh builder each call
        assertEquals(1, backing.puts().size());
        RecordingDraw3D.Keyed put = backing.lastPut();
        assertEquals("boxes", put.layerId());
        assertTrue(put.shapeId().startsWith("e"), "elements get element ids");
        ShapeSpec.Box spec = assertInstanceOf(ShapeSpec.Box.class, put.spec());
        assertEquals(new Pos3d(0, 64, 0), spec.min());
        assertEquals(new Pos3d(1, 65, 1), spec.max());
        // zero-alpha packed colours become opaque (renderer rule), fill keeps 128.
        assertEquals(0xFFFF0000, spec.argbColor());
        assertEquals(0x8000FF00, spec.argbFillColor());
        assertTrue(spec.fill());
        assertTrue(spec.cull());
        assertEquals(1, layer.getElements().size());
        assertSame(box, layer.getElements().get(0));
    }

    @Test
    void mutatingAnAttachedElementUpsertsTheSameShapeId() {
        Draw3D layer = api.layer("live");
        Box box = layer.boxBuilder().setPos(0, 0, 0, 1, 1, 1).buildAndAdd();
        String shapeId = backing.lastPut().shapeId();
        int writesAfterAttach = backing.puts().size();

        box.setPos(2, 2, 2, 3, 3, 3).setAlpha(64);

        // One upsert per mutating setter, all under the same shape id.
        assertEquals(writesAfterAttach + 2, backing.puts().size());
        assertEquals(shapeId, backing.lastPut().shapeId(), "mutation re-puts the same id");
        ShapeSpec.Box spec = assertInstanceOf(ShapeSpec.Box.class, backing.lastPut().spec());
        assertEquals(new Pos3d(2, 2, 2), spec.min());
        assertEquals(0x40FFFFFF, spec.argbColor());
    }

    @Test
    void detachedElementsDoNotTouchThePort() {
        Draw3D layer = api.layer("detached");
        Box box = layer.boxBuilder().setPos(0, 0, 0, 1, 1, 1).build();
        assertTrue(backing.puts().isEmpty(), "build() alone must not draw");

        box.setColor(0x123456);
        assertTrue(backing.puts().isEmpty(), "detached mutations stay local");

        layer.reAddElement(box);
        assertEquals(1, backing.puts().size());
        layer.reAddElement(box); // idempotent
        assertEquals(1, backing.puts().size());
        assertEquals(1, layer.getElements().size());
    }

    @Test
    void removeDetachesAndStopsFurtherPushes() {
        Draw3D layer = api.layer("removable");
        Box box = layer.boxBuilder().buildAndAdd();
        String shapeId = backing.lastPut().shapeId();
        int writes = backing.puts().size();

        layer.remove(box);
        assertTrue(backing.hasOp("remove", "removable"));
        assertEquals(shapeId, backing.keyed.stream()
                .filter(entry -> "remove".equals(entry.op()))
                .findFirst().orElseThrow().shapeId());
        assertTrue(layer.getElements().isEmpty());

        box.setAlpha(1);
        assertEquals(writes, backing.puts().size(), "detached element stops pushing");

        // State survives; re-adding draws it again under a fresh id.
        layer.reAddElement(box);
        assertEquals(writes + 1, backing.puts().size());
        assertNotEquals(shapeId, backing.lastPut().shapeId());
    }

    @Test
    void clearEmptiesElementsAndTheWholePortLayer() {
        Draw3D layer = api.layer("wipe");
        layer.boxBuilder().buildAndAdd();
        layer.lineBuilder().setPos(0, 0, 0, 1, 0, 0).buildAndAdd();
        api.drawBox("wipe", new Pos3D(0, 0, 0), new Pos3D(1, 1, 1), 0xFF0000FF);
        assertEquals(3, backing.puts().size());

        layer.clear();
        assertTrue(backing.hasOp("clear", "wipe"));
        assertTrue(layer.getElements().isEmpty());
        assertEquals("wipe", layer.getId(), "clear keeps the layer itself");
        assertEquals(1, api.getLayers().size());
    }

    @Test
    void buildAndAddRequiresAnOriginAndSingleAttachment() {
        Draw3D layer = api.layer("strict");
        Box orphan = new Box();
        assertThrows(IllegalStateException.class, orphan::buildAndAdd);

        Box box = layer.boxBuilder().buildAndAdd();
        assertThrows(IllegalStateException.class, box::buildAndAdd);

        Draw3D other = api.layer("elsewhere");
        assertThrows(IllegalStateException.class, () -> other.reAddElement(box));
        assertThrows(IllegalArgumentException.class, () -> other.reAddElement(null));
    }

    @Test
    void legacyPrimitivesShareTheLayerButAreNotElements() {
        api.drawBox("shared", new Pos3D(0, 64, 0), new Pos3D(1, 65, 1), 0xFF112233);
        Draw3D layer = api.layer("shared"); // same underlying port layer
        layer.boxBuilder().buildAndAdd();

        assertEquals(2, backing.puts().size());
        assertTrue(layer.getElements().isEmpty() == false);
        assertEquals(1, layer.getElements().size(), "raw primitive is not an element");
        assertTrue(backing.puts().get(0).shapeId().startsWith("r"),
                "primitives get raw ids");
    }

    // ---- Box property surface ---------------------------------------------

    @Test
    void boxColourAndAlphaFollowTheJsMacrosRules() {
        Box box = new Box();
        // defaults: opaque white outline and fill, no fill, drawn over world
        assertEquals(0xFFFFFF, box.getColor());
        assertEquals(255, box.getAlpha());
        assertEquals(0xFFFFFF, box.getFillColor());
        assertEquals(255, box.getFillAlpha());
        assertFalse(box.isFilled());
        assertFalse(box.isCulled());

        box.setColor(0x00FF0000); // non-black RGB with zero alpha -> opaque
        assertEquals(0xFF0000, box.getColor());
        assertEquals(255, box.getAlpha());

        box.setColor(0x123456, 7); // explicit alpha wins, zero stays transparent
        assertEquals(0x123456, box.getColor());
        assertEquals(7, box.getAlpha());

        box.setAlpha(0);
        assertEquals(0, box.getAlpha());
        assertEquals(0x123456, box.getColor());

        box.setFillColor(0x0000FF00, 128);
        assertEquals(0xFF00, box.getFillColor());
        assertEquals(128, box.getFillAlpha());
        box.setFillAlpha(33);
        assertEquals(33, box.getFillAlpha());

        box.setFill(true);
        assertTrue(box.isFilled());
        box.cull(true);
        assertTrue(box.isCulled());
    }

    @Test
    void boxGeometryHelpersPlaceCornersAsDocumented() {
        Box box = new Box();
        box.setPosToBlock(new BlockPos(4, 5, 6));
        assertEquals(new Pos3D(4, 5, 6), box.getPos1());
        assertEquals(new Pos3D(5, 6, 7), box.getPos2());

        box.setPosToPoint(new Pos3D(10, 10, 10), 2.5);
        assertEquals(new Pos3D(7.5, 7.5, 7.5), box.getPos1());
        assertEquals(new Pos3D(12.5, 12.5, 12.5), box.getPos2());

        box.forBlock(new BlockPos(0, 64, 0));
        assertEquals(new Pos3D(0, 64, 0), box.getPos1());
        assertEquals(new Pos3D(1, 65, 1), box.getPos2());

        box.setPos1(new Pos3D(9, 9, 9));
        assertEquals(new Pos3D(9, 9, 9), box.getPos1());
        box.setPos2(new Pos3D(8, 8, 8));
        assertEquals(new Pos3D(8, 8, 8), box.getPos2());
        box.pos(new Pos3D(1, 2, 3), new Pos3D(4, 5, 6));
        assertEquals(new Pos3D(1, 2, 3), box.getPos1());
        assertEquals(new Pos3D(4, 5, 6), box.getPos2());
    }

    // ---- Line3D property surface ------------------------------------------

    @Test
    void lineKeepsOrderedEndpointsAndAlwaysOnTopMirrorsCull() {
        Draw3D layer = api.layer("lines");
        Line3D line = layer.lineBuilder()
                .setPos(0, 64, 0, 8, 72, 0)
                .setColor(0x00FF0000, 200)
                .buildAndAdd();

        ShapeSpec.Line spec = assertInstanceOf(ShapeSpec.Line.class, backing.lastPut().spec());
        assertEquals(new Pos3d(0, 64, 0), spec.from());
        assertEquals(new Pos3d(8, 72, 0), spec.to());
        assertEquals(0xC8FF0000, spec.argbColor());
        assertFalse(spec.cull());

        assertEquals(new Pos3D(0, 64, 0), line.getPos1());
        assertEquals(new Pos3D(8, 72, 0), line.getPos2());
        assertEquals(0xFF0000, line.getColor());
        assertEquals(200, line.getAlpha());

        line.setAlwaysOnTop(true);
        assertTrue(line.isAlwaysOnTop());
        assertFalse(line.isCulled());
        line.cull(true);
        assertTrue(line.isCulled());
        assertFalse(line.isAlwaysOnTop());
        line.setAlwaysOnTop(false);
        assertTrue(line.isCulled());

        line.setColor(0x0000FF); // zero-alpha rule -> opaque
        assertEquals(255, line.getAlpha());
        assertEquals(0x0000FF, line.getColor());
    }

    @Test
    void lineBuilderEndpointsTakePointsAndBlocks() {
        Draw3D layer = api.layer("line-builders");
        Line3D line = layer.lineBuilder()
                .setPos1(new Pos3D(1, 2, 3))
                .setPos2(new BlockPos(4, 5, 6))
                .build();
        assertEquals(new Pos3D(1, 2, 3), line.getPos1());
        assertEquals(new Pos3D(4, 5, 6), line.getPos2());

        Line3D viaPos = layer.lineBuilder()
                .pos(new Pos3D(0, 0, 0), new Pos3D(1, 1, 1))
                .build();
        assertEquals(new Pos3D(1, 1, 1), viaPos.getPos2());
        assertEquals(0xFFFFFF, viaPos.getColor());
        assertFalse(viaPos.isCulled());
    }

    // ---- ordering ----------------------------------------------------------

    @Test
    void elementsSortByClassThenByGeometry() {
        Box late = new Box().setPos(5, 5, 5, 6, 6, 6);
        Box early = new Box().setPos(1, 1, 1, 2, 2, 2);
        Line3D line = new Line3D();

        assertTrue(early.compareToSame(late) < 0);
        assertEquals(0, early.compareToSame(copyOf(early)));

        // Different classes order by canonical class name, not insertion.
        @SuppressWarnings({"unchecked", "rawtypes"})
        int across = ((RenderElement3D) early).compareTo((RenderElement3D) line);
        assertEquals(Integer.signum(early.getClass().getName()
                .compareTo(line.getClass().getName())), Integer.signum(across));
    }

    /** Minimal detached copy helper for equality-style comparisons. */
    private static Box copyOf(Box source) {
        return new Box().pos(source.getPos1(), source.getPos2());
    }
}
