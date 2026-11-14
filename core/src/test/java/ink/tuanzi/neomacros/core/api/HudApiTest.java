package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.core.api.render.RecordingHudRender;
import ink.tuanzi.neomacros.port.model.WindowData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code neo.hud} facade over a recording port: idempotent layer handles,
 * owner namespacing with auto-clear on release (拍板③), z scheduling,
 * element lifecycle, and the frozen {@code addText}/{@code removeText}
 * sugar riding the implicit {@code $legacy} layer (拍板⑦).
 */
class HudApiTest {

    private static final WindowData WINDOW = new WindowData(640, 360, 640, 360);

    /** Fake owner scope capturing the release hook for the test. */
    private static final class FakeOwner implements HudApi.OwnerScope {
        private final String name;
        private Runnable hook;

        FakeOwner(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void onRelease(Runnable hook) {
            this.hook = hook;
        }

        /** Simulates the module unloading. */
        void release() {
            if (hook != null) {
                hook.run();
            }
        }
    }

    private final RecordingHudRender render = new RecordingHudRender();
    private final AtomicReference<HudApi.OwnerScope> owner = new AtomicReference<>();

    private HudApi newApi() {
        return new HudApi(render, Runnable::run, () -> WINDOW, owner::get);
    }

    @Test
    void layerIsIdempotentWithinTheOwner() {
        HudApi api = newApi();
        owner.set(new FakeOwner("modA"));

        HudLayer first = api.layer("coords", null);
        HudLayer again = api.layer("coords", null);
        assertSame(first, again, "same owner + same id returns the same handle");
        assertEquals(List.of("add:modA:coords:0"), render.events(),
                "the port sees exactly one registration");
        assertEquals(List.of(first), api.getLayers());
    }

    @Test
    void layerAcceptsZOptionAndRejectsReservedPrefixes() {
        HudApi api = newApi();
        owner.set(new FakeOwner("modA"));

        HudLayer layer = api.layer("overlay", java.util.Map.of("z", 7));
        assertEquals(7, layer.getZIndex());
        assertEquals(List.of("add:modA:overlay:7"), render.events());

        assertThrows(IllegalArgumentException.class, () -> api.layer("$legacy", null));
        assertThrows(IllegalArgumentException.class, () -> api.layer("", null));
        assertThrows(IllegalArgumentException.class, () -> api.removeLayer("$legacy"));
    }

    @Test
    void setZIndexSchedulesPortCallsIdempotently() {
        HudApi api = newApi();
        owner.set(new FakeOwner("modA"));
        HudLayer layer = api.layer("main", null);

        layer.setZIndex(5);
        layer.setZIndex(5);
        layer.setZIndex(7);

        assertEquals(List.of("add:modA:main:0", "setz:modA:main:5",
                "setz:modA:main:7"), render.events(),
                "an unchanged z is a no-op; each real change schedules one setLayerZ");
        assertEquals(7, layer.getZIndex());
    }

    @Test
    void setVisibleNeverTouchesThePort() {
        HudApi api = newApi();
        owner.set(new FakeOwner("modA"));
        HudLayer layer = api.layer("main", null);
        layer.text("t", "hello", null);
        assertEquals(1, render.frame(0).size());

        layer.setVisible(false);
        assertEquals(List.of("add:modA:main:0"), render.events(),
                "visibility is pure core state");
        assertEquals(List.of(), render.frame(0));
    }

    @Test
    void removeLayerDisposesHandlesAndForgetsTheKey() {
        HudApi api = newApi();
        owner.set(new FakeOwner("modA"));
        HudLayer layer = api.layer("main", null);
        var handle = layer.text("a", "a", null);

        api.removeLayer("main");
        assertTrue(layer.isDisposed());
        assertTrue(handle.isDisposed());
        assertNull(layer.getZIndex());
        assertNull(layer.element("a"));
        assertThrows(IllegalStateException.class, layer::clear);
        assertEquals(List.of("add:modA:main:0", "remove:modA:main"),
                render.events());
        assertEquals(List.of(), api.getLayers());

        HudLayer fresh = api.layer("main", null);
        assertNotSame(layer, fresh, "a disposed key is recreated on demand");
        assertFalse(fresh.isDisposed());
    }

    @Test
    void ownersIsolateLayersAndUnloadAutoClears() {
        FakeOwner ownerA = new FakeOwner("modA");
        FakeOwner ownerB = new FakeOwner("modB");
        HudApi api = newApi();

        owner.set(ownerA);
        HudLayer fromA = api.layer("coords", null);
        assertEquals(List.of(fromA), api.getLayers());

        owner.set(ownerB);
        assertEquals(List.of(), api.getLayers(), "layers are listed per owner");
        HudLayer fromB = api.layer("coords", null);
        assertNotSame(fromA, fromB, "the same public id is a different layer per owner");
        assertEquals(List.of("add:modA:coords:0", "add:modB:coords:0"),
                render.events());

        owner.set(ownerA);
        assertSame(fromA, api.layer("coords", null));

        ownerA.release();
        assertTrue(fromA.isDisposed(), "owner release disposes the owner's layers");
        assertFalse(fromB.isDisposed(), "other owners are untouched");
        assertEquals(List.of("add:modA:coords:0", "add:modB:coords:0",
                "remove:modA:coords"), render.events());

        owner.set(ownerA);
        assertEquals(List.of(), api.getLayers());
        owner.set(ownerB);
        assertEquals(List.of(fromB), api.getLayers());
    }

    @Test
    void ownerlessLayersUseBareKeysAndNeverAutoClear() {
        HudApi api = newApi();
        owner.set(null);

        HudLayer layer = api.layer("manual", null);
        assertEquals(List.of("add:manual:0"), render.events(),
                "without an owner the key stays bare");
        assertEquals(List.of(layer), api.getLayers());
        // No release hook exists to fire: the layer stays until removed.
        assertFalse(layer.isDisposed());
        api.removeLayer("manual");
        assertTrue(layer.isDisposed());
    }

    @Test
    void legacyAddTextSugarUpsertsOnTheHiddenLegacyLayer() throws Exception {
        HudApi api = newApi();
        owner.set(new FakeOwner("modA"));

        api.addText("greet", "hello", 1, 2, 0xFFFFFFFF).get();
        assertEquals(List.of("add:modA:$legacy:0"), render.events(),
                "the sugar rides one implicit $legacy layer per owner");
        assertEquals(List.of("text:1,2,hello,ffffffff"), render.frame(0));
        assertEquals(List.of(), api.getLayers(),
                "the $legacy layer never shows in hud.layers (拍板⑤)");

        api.addText("greet", "hello again", 1, 2, 0xFFFFFFFF).get();
        assertEquals(List.of("text:1,2,hello again,ffffffff"), render.frame(0),
                "re-adding the same id updates in place, no duplicate element");

        String chained = api.addText("other", "x", 0, 0, 0xFF00FF00)
                .thenApply(v -> "chained").get();
        assertEquals("chained", chained, "the Promise face keeps .then() chaining");
        assertEquals(List.of("text:1,2,hello again,ffffffff",
                "text:0,0,x,ff00ff00"), render.frame(0));

        api.removeText("greet").get();
        assertEquals(List.of("text:0,0,x,ff00ff00"), render.frame(0),
                "removeText drops only its own element");
        api.removeText("ghost").get();
        assertEquals(List.of("text:0,0,x,ff00ff00"), render.frame(0),
                "unknown ids resolve without effect");

        assertFalse(render.events().contains("remove:modA:$legacy"),
                "the legacy layer itself survives until the owner unloads");
    }

    @Test
    void legacyLayerDiesWithItsOwner() {
        FakeOwner ownerA = new FakeOwner("modA");
        HudApi api = newApi();
        owner.set(ownerA);

        api.addText("greet", "bye", 0, 0, 0xFFFFFFFF);
        assertEquals(1, render.frame(0).size());

        ownerA.release();
        assertEquals(List.of(), render.frame(0),
                "module unload clears the legacy texts (javadoc semantics)");
        assertEquals(List.of("add:modA:$legacy:0", "remove:modA:$legacy"),
                render.events());
    }
}
