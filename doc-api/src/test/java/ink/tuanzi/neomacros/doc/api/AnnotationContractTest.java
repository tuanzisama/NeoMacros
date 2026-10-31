package ink.tuanzi.neomacros.doc.api;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the structural contract of every annotation in this package:
 * runtime retention, documented presence, allowed targets, and member
 * defaults. The binding builder and the documentation generator both depend
 * on these properties, so a deviation breaks consumers rather than the
 * annotation jar itself.
 */
class AnnotationContractTest {

    private static final List<Class<?>> ALL_ANNOTATIONS = List.of(
            ApiModule.class, ApiEvent.class, ApiHandle.class, ApiReadOnly.class,
            ApiType.class, ApiSince.class, ApiMC.class, ApiPermission.class,
            ApiExperimental.class, ApiInternal.class);

    @Test
    void everyAnnotationHasRuntimeRetention() {
        for (Class<?> type : ALL_ANNOTATIONS) {
            Retention retention = type.getAnnotation(Retention.class);
            assertNotNull(retention, type.getSimpleName() + " must declare @Retention");
            assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                    type.getSimpleName() + " must be visible to the binding builder via reflection");
        }
    }

    @Test
    void everyAnnotationIsDocumented() {
        for (Class<?> type : ALL_ANNOTATIONS) {
            assertNotNull(type.getAnnotation(Documented.class),
                    type.getSimpleName() + " must carry @Documented so generated javadoc shows it");
        }
    }

    @Test
    void moduleTargetsFacadeClassesOnly() {
        assertArrayEquals(new ElementType[]{ElementType.TYPE},
                targetsOf(ApiModule.class));
    }

    @Test
    void eventTargetsRecordClassesOnly() {
        assertArrayEquals(new ElementType[]{ElementType.TYPE},
                targetsOf(ApiEvent.class));
    }

    @Test
    void handleTargetsNestedClassesOnly() {
        assertArrayEquals(new ElementType[]{ElementType.TYPE},
                targetsOf(ApiHandle.class));
    }

    @Test
    void readOnlyTargetsMethodsAndFields() {
        assertArrayEquals(new ElementType[]{ElementType.METHOD, ElementType.FIELD},
                targetsOf(ApiReadOnly.class));
    }

    @Test
    void typeTargetsMethodsParametersAndFields() {
        assertArrayEquals(new ElementType[]{ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD},
                targetsOf(ApiType.class));
    }

    @Test
    void timelineAndGatingAnnotationsTargetTypesAndMethods() {
        for (Class<?> type : List.of(ApiSince.class, ApiMC.class, ApiPermission.class,
                ApiExperimental.class, ApiInternal.class)) {
            assertArrayEquals(new ElementType[]{ElementType.TYPE, ElementType.METHOD},
                    targetsOf(type), type.getSimpleName() + " targets");
        }
    }

    @Test
    void moduleDefaults() {
        assertEquals("", defaultValue(ApiModule.class, "category"),
                "module without a category is uncategorized");
        assertEquals(true, defaultValue(ApiModule.class, "global"),
                "modules default to also being a neo.* global member");
        assertNull(defaultValue(ApiModule.class, "name"), "module name is mandatory");
    }

    @Test
    void eventDefaults() {
        assertEquals(false, defaultValue(ApiEvent.class, "cancellable"),
                "events default to the asynchronous channel only");
        assertNull(defaultValue(ApiEvent.class, "name"), "event name is mandatory");
    }

    @Test
    void singleValueAnnotationsHaveNoDefault() {
        for (Class<?> type : List.of(ApiHandle.class, ApiType.class, ApiSince.class,
                ApiMC.class, ApiPermission.class)) {
            assertNull(defaultValue(type, "value"), type.getSimpleName() + ".value is mandatory");
        }
    }

    @Test
    void markerAnnotationsDeclareNoMembers() {
        for (Class<?> type : List.of(ApiReadOnly.class, ApiExperimental.class, ApiInternal.class)) {
            assertEquals(0, type.getDeclaredMethods().length,
                    type.getSimpleName() + " is a pure marker annotation");
        }
    }

    private static ElementType[] targetsOf(Class<?> type) {
        Target target = type.getAnnotation(Target.class);
        assertNotNull(target, type.getSimpleName() + " must declare @Target");
        return target.value();
    }

    private static Object defaultValue(Class<?> annotation, String member) {
        try {
            return annotation.getMethod(member).getDefaultValue();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(annotation.getSimpleName() + "." + member + " missing", e);
        }
    }
}
