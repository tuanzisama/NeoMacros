package ink.tuanzi.neomacros.doc.api.fixture;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiExperimental;
import ink.tuanzi.neomacros.doc.api.ApiHandle;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the annotations of the fixture facade back through reflection,
 * proving the annotations apply to the element kinds the binding builder
 * scans (types, methods, parameters, records, nested classes) and survive
 * at runtime with the declared values and defaults.
 */
class DocApiFixtureTest {

    @Test
    void moduleDeclarationIsReadable() {
        ApiModule module = DocApiFixture.class.getAnnotation(ApiModule.class);
        assertNotNull(module);
        assertEquals("fixture", module.name());
        assertEquals("test", module.category());
        assertTrue(module.global(), "default global must be present on the fixture");
    }

    @Test
    void classLevelTimelineAndGatingAnnotationsAreReadable() {
        assertEquals("0.1.0", DocApiFixture.class.getAnnotation(ApiSince.class).value());
        assertEquals(">=1.20.6", DocApiFixture.class.getAnnotation(ApiMC.class).value());
        assertEquals("fs", DocApiFixture.class.getAnnotation(ApiPermission.class).value());
        assertNotNull(DocApiFixture.class.getAnnotation(ApiExperimental.class),
                "fixture declares the experimental badge");
    }

    @Test
    void methodAnnotationsAreReadable() throws Exception {
        Method action = DocApiFixture.class.getMethod("action", String.class);

        assertEquals("0.1.0", action.getAnnotation(ApiSince.class).value());
        assertEquals("Promise<void>", action.getAnnotation(ApiType.class).value());

        Method hidden = DocApiFixture.class.getMethod("hidden");
        assertNotNull(hidden.getAnnotation(ApiInternal.class));
    }

    @Test
    void parameterAnnotationIsReadable() throws Exception {
        Method action = DocApiFixture.class.getMethod("action", String.class);
        ApiType argType = action.getParameters()[0].getAnnotation(ApiType.class);
        assertNotNull(argType);
        assertEquals("string", argType.value());
    }

    @Test
    void handleAndItsGetterAreReadable() throws Exception {
        Class<?> handle = Class.forName(DocApiFixture.class.getName() + "$FixtureHandle");
        assertEquals("FixtureHandle", handle.getAnnotation(ApiHandle.class).value());

        Method getter = handle.getMethod("getName");
        assertNotNull(getter.getAnnotation(ApiReadOnly.class),
                "bean getters carry the read-only marker");
    }

    @Test
    void eventRecordsAreReadableWithDefaults() throws Exception {
        Class<?> marked = Class.forName(DocApiFixture.class.getName() + "$Marked");
        ApiEvent markedEvent = marked.getAnnotation(ApiEvent.class);
        assertNotNull(markedEvent);
        assertEquals("fixture:marked", markedEvent.name());
        assertTrue(markedEvent.cancellable(), "declared cancellable must survive reflection");

        Class<?> plain = Class.forName(DocApiFixture.class.getName() + "$Plain");
        ApiEvent plainEvent = plain.getAnnotation(ApiEvent.class);
        assertNotNull(plainEvent);
        assertEquals("fixture:plain", plainEvent.name());
        assertEquals(false, plainEvent.cancellable(), "default must be cancellable=false");
    }
}
