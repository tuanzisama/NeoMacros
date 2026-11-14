package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.render.components3d.EntityTraceLine;
import ink.tuanzi.neomacros.core.api.render.components3d.RenderElement3D;
import ink.tuanzi.neomacros.core.api.render.components3d.Surface;
import ink.tuanzi.neomacros.core.api.render.components3d.TraceLine;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-033B retained components over a recording port: TraceLine /
 * EntityTraceLine / Surface property surfaces, their toSpec snapshots
 * (camera trace, entity trace with UUID + yOffset, panel placement), the
 * entity-binding path, and the Draw3D split members (addTraceLine /
 * addEntityTraceLine / addDraw2D / addSurface / removeTraceLine /
 * removeDraw2D + the three builder factories).
 */
class SurfaceTraceLineTest {

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
        public void addTraceLine(String layerId, UUID entityUuid, int argbColor) {
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

    private static EntityApi.Entity entity() {
        return new EntityApi.Entity(UUID.fromString("7b3c9d2e-1a4f-4c8b-9e6d-0a1b2c3d4e5f"));
    }

    // ---- TraceLine --------------------------------------------------------

    @Test
    void traceLineBuilderChainAttachesACameraTrace() {
        Draw3D layer = api.layer("traces");
        TraceLine line = layer.traceLineBuilder()
                .setPos(new Pos3D(1, 64, 2))
                .setColor(0x00FF0000, 200)
                .setAlwaysOnTop(true)
                .buildAndAdd();

        assertEquals(1, backing.puts().size());
        RecordingDraw3D.Keyed put = backing.lastPut();
        assertEquals("traces", put.layerId());
        assertTrue(put.shapeId().startsWith("e"), "elements get element ids");
        ShapeSpec.CameraTrace spec = assertInstanceOf(ShapeSpec.CameraTrace.class, put.spec());
        assertEquals(new Pos3d(1, 64, 2), spec.target());
        assertEquals(0xC8FF0000, spec.argbColor());
        assertFalse(spec.cull(), "alwaysOnTop means no depth testing");

        // Property surface: pos / color / alpha / alwaysOnTop.
        assertEquals(new Pos3D(1, 64, 2), line.getPos());
        assertEquals(0xFF0000, line.getColor());
        assertEquals(200, line.getAlpha());
        assertTrue(line.isAlwaysOnTop());
        assertSame(line, layer.getElements().get(0));
    }

    @Test
    void traceLineColourRulesAndLiveUpsert() {
        Draw3D layer = api.layer("trace-live");
        TraceLine line = layer.traceLineBuilder().buildAndAdd();
        String shapeId = backing.lastPut().shapeId();
        int writesAfterAttach = backing.puts().size();

        // Defaults: origin target, opaque white, over terrain.
        assertEquals(new Pos3D(0, 0, 0), line.getPos());
        assertEquals(0xFFFFFF, line.getColor());
        assertEquals(255, line.getAlpha());
        assertTrue(line.isAlwaysOnTop());

        // Zero-alpha rule: non-black RGB with zero alpha becomes opaque.
        line.setColor(0x0000FF00);
        assertEquals(0x00FF00, line.getColor());
        assertEquals(255, line.getAlpha());

        // Explicit alpha zero stays transparent.
        line.setColor(0x123456, 0);
        assertEquals(0, line.getAlpha());
        line.setAlpha(64);
        line.setAlwaysOnTop(false);

        // One upsert per mutating setter, all under the same shape id.
        assertEquals(writesAfterAttach + 4, backing.puts().size());
        assertEquals(shapeId, backing.lastPut().shapeId(), "mutation re-puts the same id");
        ShapeSpec.CameraTrace spec = assertInstanceOf(ShapeSpec.CameraTrace.class, backing.lastPut().spec());
        assertEquals(0x40123456, spec.argbColor());
        assertTrue(spec.cull(), "alwaysOnTop false means depth tested");
    }

    // ---- EntityTraceLine --------------------------------------------------

    @Test
    void entityTraceLineBindsAnEntityAndCarriesItsYOffset() {
        Draw3D layer = api.layer("entity-traces");
        EntityApi.Entity target = entity();

        // Statement style: TraceLine's fluent setters declare TraceLine
        // returns (same as element-side typing), so the chain
        // would widen the static type.
        EntityTraceLine line = layer.entityTraceLineBuilder();
        line.setEntity(target);
        line.setYOffset(2.5);
        line.setColor(0x00FF0000);
        line.buildAndAdd();

        assertEquals(1, backing.puts().size());
        ShapeSpec.EntityTrace spec = assertInstanceOf(ShapeSpec.EntityTrace.class, backing.lastPut().spec());
        assertEquals(target.getUuid(), spec.entityUuid().toString());
        assertEquals(2.5, spec.yOffset());
        assertEquals(0xFFFF0000, spec.argbColor(), "zero-alpha red becomes opaque");
        assertFalse(spec.cull());

        // Builder face reads: entity property + yOffset property.
        assertSame(target, line.getEntity());
        assertEquals(2.5, line.getYOffset());
        assertEquals(0xFF0000, line.getColor());

        // The default offset is 0.5 on a fresh line; rebinding to null
        // makes the spec draw nothing (null UUID).
        EntityTraceLine fresh = layer.entityTraceLineBuilder().build();
        assertEquals(0.5, fresh.getYOffset());
        assertNull(fresh.getEntity());
        fresh.setEntity(null);
        assertNull(assertInstanceOf(ShapeSpec.EntityTrace.class, fresh.toSpec()).entityUuid());

        // The quick form `.buildAndAdd(entity)` folds onto `.entity(e).buildAndAdd()`.
        EntityTraceLine chained = layer.entityTraceLineBuilder()
                .setEntity(target)
                .buildAndAdd();
        assertSame(chained, layer.getElements().get(layer.getElements().size() - 1));
    }

    @Test
    void entityTraceLineOrphanAndDoubleAttachAreRejected() {
        EntityTraceLine orphan = new EntityTraceLine();
        assertThrows(IllegalStateException.class, orphan::buildAndAdd);

        Draw3D layer = api.layer("strict-trace");
        EntityTraceLine line = layer.entityTraceLineBuilder().buildAndAdd();
        assertThrows(IllegalStateException.class, line::buildAndAdd);

        Draw3D other = api.layer("elsewhere-trace");
        assertThrows(IllegalStateException.class, () -> other.reAddElement(line));
    }

    // ---- Surface ----------------------------------------------------------

    @Test
    void surfaceBuilderChainFollowsTheFactoryDefaults() {
        Draw3D layer = api.layer("panels");
        Surface surface = layer.surfaceBuilder()
                .setPos(new Pos3D(0.5, 64, 0.5))
                .setSizes(1, 1)
                .setMinSubdivisions(200)
                .cull(false)
                .renderBack(true)
                .buildAndAdd();

        assertEquals(1, backing.puts().size());
        assertTrue(backing.lastPut().shapeId().startsWith("e"));
        ShapeSpec.Panel spec = assertInstanceOf(ShapeSpec.Panel.class, backing.lastPut().spec());
        assertEquals(new Pos3d(0.5, 64, 0.5), spec.origin());
        assertEquals(1.0, spec.width());
        assertEquals(1.0, spec.height());
        assertTrue(spec.rotateCenter(), "surfaceBuilder centres rotations");
        assertTrue(spec.renderBack(), "surfaceBuilder renders the back");
        assertFalse(spec.cull());
        assertEquals(0xFFFFFFFF, spec.argbColor(), "panels render opaque white");

        // Factory defaults read back: 10x10 / one subdivision before the
        // overrides above, centred pivot, full-bright (no colour face).
        assertEquals(new Pos2D(1, 1), surface.getSizes());
        assertEquals(200, surface.getMinSubdivisions());
        assertTrue(surface.isRotatingCenter());
        assertTrue(surface.shouldRenderBack());
        assertFalse(surface.isCulled());
        assertEquals(new Pos3D(0.5, 64, 0.5), surface.getPos());
        assertEquals(200, surface.getWidth(), "1x1 at 200 subdivisions is 200x200 pixels");
        assertEquals(200, surface.getHeight());

        Surface fresh = layer.surfaceBuilder().build();
        assertEquals(new Pos2D(10, 10), fresh.getSizes());
        assertEquals(1, fresh.getMinSubdivisions());
        assertEquals(1, fresh.getWidth(), "square panel at one subdivision is one pixel across");
        assertEquals(1, fresh.getHeight());
        assertEquals(0, fresh.getZIndex(), "surfaces do not order by z-index");
    }

    @Test
    void surfaceQuickFormUsesTheAddDraw2DDefaults() {
        Draw3D layer = api.layer("quick-panels");
        Surface surface = layer.addDraw2D(1, 64, 2);
        ShapeSpec.Panel spec = assertInstanceOf(ShapeSpec.Panel.class, backing.lastPut().spec());

        // Quick form: 1x1, no rotation, 200 subdivisions, no back
        // face, no depth culling, corner pivot.
        assertEquals(new Pos3d(1, 64, 2), spec.origin());
        assertEquals(1.0, spec.width());
        assertEquals(1.0, spec.height());
        assertEquals(0.0, spec.xRot());
        assertEquals(200, surface.getMinSubdivisions());
        assertFalse(spec.renderBack());
        assertFalse(spec.cull());
        assertFalse(spec.rotateCenter(), "quick form pivots at the corner");

        // Optional tail overrides geometry and flags.
        layer.addDraw2D(0, 70, 0, 0.0, 90.0, 0.0, 4.0, 2.0, 50, true, true);
        ShapeSpec.Panel full = assertInstanceOf(ShapeSpec.Panel.class, backing.lastPut().spec());
        assertEquals(4.0, full.width());
        assertEquals(2.0, full.height());
        assertEquals(90.0, full.yRot());
        assertTrue(full.renderBack());
        assertTrue(full.cull());
        assertEquals(50, assertInstanceOf(Surface.class, layer.getElements()
                .get(layer.getElements().size() - 1)).getMinSubdivisions());
    }

    @Test
    void surfaceEntityBindingReachesTheSpec() {
        Draw3D layer = api.layer("bound-panels");
        EntityApi.Entity target = entity();

        Surface surface = layer.surfaceBuilder()
                .bindToEntity(target)
                .setBoundOffset(new Pos3D(0, 1.5, 0))
                .buildAndAdd();

        ShapeSpec.Panel spec = assertInstanceOf(ShapeSpec.Panel.class, backing.lastPut().spec());
        assertEquals(target.getUuid(), spec.boundEntity().toString());
        assertEquals(new Pos3d(0, 1.5, 0), spec.boundOffset());
        assertSame(target, surface.getBoundEntity());
        assertEquals(new Pos3D(0, 1.5, 0), surface.getBoundOffset());

        surface.bindToEntity(null);
        ShapeSpec.Panel unbound = assertInstanceOf(ShapeSpec.Panel.class, backing.lastPut().spec());
        assertNull(unbound.boundEntity());
        assertNull(surface.getBoundEntity());
    }

    @Test
    void surfaceRotationPivotAndScaleState() {
        Draw3D layer = api.layer("rotated-panels");
        Surface surface = layer.surfaceBuilder()
                .setRotations(10, 20, 30)
                .setXRotation(1)
                .setYRotation(2)
                .setZRotation(3)
                .setRotateCenter(false)
                .setRotateToPlayer(true)
                .setZIndexScale(0.005)
                .setWorldLight()
                .setLight(8, 12)
                .setFullBrightLight()
                .buildAndAdd();

        assertEquals(new Pos3D(1, 2, 3), surface.getRotations());
        assertEquals(1.0, surface.getXRotation());
        assertEquals(2.0, surface.getYRotation());
        assertEquals(3.0, surface.getZRotation());
        assertFalse(surface.isRotatingCenter());
        assertTrue(surface.doesRotateToPlayer());
        assertEquals(0.005, surface.getZIndexScale());
        assertEquals(0, surface.getZIndex());

        ShapeSpec.Panel spec = assertInstanceOf(ShapeSpec.Panel.class, backing.lastPut().spec());
        assertTrue(spec.rotateToPlayer());
        assertFalse(spec.rotateCenter());
        assertEquals(3.0, spec.zRot());

        // Rotation comparison: position first, then rotations, then size.
        Surface a = new Surface().setPos(new Pos3D(1, 2, 3));
        Surface b = new Surface().setPos(new Pos3D(1, 2, 3)).setRotations(0, 0, 5);
        assertTrue(a.compareToSame(b) < 0);
        assertEquals(0, a.compareToSame(new Surface().setPos(new Pos3D(1, 2, 3))));
    }

    // ---- Draw3D split members ---------------------------------------------

    @Test
    void splitMembersAttachCreateAndRemoveElements() {
        Draw3D layer = api.layer("split");
        EntityApi.Entity target = entity();

        // Quick forms create attached elements and report them in elements.
        TraceLine quickTrace = layer.addTraceLine(new Pos3D(4, 64, 4), 0x00FF0000, 128);
        assertSame(quickTrace, layer.getElements().get(0));
        ShapeSpec.CameraTrace traceSpec =
                assertInstanceOf(ShapeSpec.CameraTrace.class, backing.lastPut().spec());
        assertEquals(new Pos3d(4, 64, 4), traceSpec.target());
        assertEquals(0x80FF0000, traceSpec.argbColor());

        EntityTraceLine quickEntity = layer.addEntityTraceLine(target, 0x0000FF00, 64, 3.0, false);
        assertSame(quickEntity, layer.getElements().get(1));
        ShapeSpec.EntityTrace entitySpec =
                assertInstanceOf(ShapeSpec.EntityTrace.class, backing.lastPut().spec());
        assertEquals(target.getUuid(), entitySpec.entityUuid().toString());
        assertEquals(3.0, entitySpec.yOffset());
        assertTrue(entitySpec.cull(), "alwaysOnTop false reaches the spec");
        assertEquals(3.0, quickEntity.getYOffset());

        Surface quickPanel = layer.addDraw2D(8, 64, 8);
        assertSame(quickPanel, layer.getElements().get(2));

        // addSurface attaches an existing detached element.
        Surface detached = layer.surfaceBuilder().build();
        layer.addSurface(detached);
        assertSame(detached, layer.getElements().get(3));

        // The folded attach face (addTraceLine(TraceLine)) is
        // reAddElement; attaching the same instance twice is a no-op.
        layer.reAddElement(quickTrace);
        assertEquals(4, layer.getElements().size());

        // removeTraceLine / removeDraw2D detach without dropping state.
        layer.removeTraceLine(quickTrace);
        assertTrue(backing.hasOp("remove", "split"));
        assertEquals(3, layer.getElements().size());
        quickTrace.setAlpha(10); // detached mutations stop pushing
        layer.removeDraw2D(quickPanel);
        assertEquals(2, layer.getElements().size());

        // A quick form without its required entity argument is rejected.
        assertThrows(IllegalArgumentException.class,
                () -> layer.addEntityTraceLine(null, 0xFFFFFF));
    }

    @Test
    void elementSortKeepsTheNewClassesInClassOrder() {
        TraceLine trace = new TraceLine().setPos(new Pos3D(1, 2, 3));
        Surface panel = new Surface().setPos(new Pos3D(1, 2, 3));
        EntityTraceLine entityLine = new EntityTraceLine();

        // Different classes order by canonical class name, not insertion.
        @SuppressWarnings({"unchecked", "rawtypes"})
        int across = ((RenderElement3D) trace).compareTo((RenderElement3D) panel);
        assertTrue(across != 0);
        assertEquals(0, trace.compareTo(new TraceLine().setPos(new Pos3D(1, 2, 3))));
        assertTrue(entityLine.compareToSame(new TraceLine()) == 0
                        && new TraceLine().compareToSame(entityLine) == 0,
                "EntityTraceLine shares TraceLine's target comparison");
    }
}
