package ink.tuanzi.neomacros.core.util;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Classpath scanning for annotated event payload records over the built-in
 * definition packages (directory classpath entries in this module's tests).
 */
class AnnotatedClassScannerTest {

    private static final String CHAT_PACKAGE = "ink.tuanzi.neomacros.core.event.def.chat";
    private static final String LIFECYCLE_PACKAGE = "ink.tuanzi.neomacros.core.event.def.lifecycle";
    private static final String TICK_PACKAGE = "ink.tuanzi.neomacros.core.event.def.tick";

    @Test
    void findsAnnotatedRecordsInPackage() {
        List<Class<?>> found = AnnotatedClassScanner.scan(CHAT_PACKAGE, ApiEvent.class,
                AnnotatedClassScannerTest.class.getClassLoader());
        assertEquals(4, found.size());
        assertTrue(found.stream().anyMatch(t -> t.getSimpleName().equals("ChatReceiveEvent")));
        assertTrue(found.stream().anyMatch(t -> t.getSimpleName().equals("ChatSendEvent")));
        assertTrue(found.stream().anyMatch(t -> t.getSimpleName().equals("ChatTitleEvent")));
        assertTrue(found.stream().anyMatch(t -> t.getSimpleName().equals("ChatActionbarEvent")));
    }

    @Test
    void findsLifecycleRecords() {
        List<Class<?>> found = AnnotatedClassScanner.scan(LIFECYCLE_PACKAGE, ApiEvent.class,
                AnnotatedClassScannerTest.class.getClassLoader());
        assertEquals(5, found.size());
        assertTrue(found.stream().anyMatch(
                t -> t.getSimpleName().equals("GameResourcePackEvent")));
    }

    @Test
    void findsTickRecord() {
        List<Class<?>> found = AnnotatedClassScanner.scan(TICK_PACKAGE, ApiEvent.class,
                AnnotatedClassScannerTest.class.getClassLoader());
        assertEquals(1, found.size());
        assertEquals("TickEvent", found.get(0).getSimpleName());
    }

    @Test
    void annotationFilterApplies() {
        List<Class<?>> found = AnnotatedClassScanner.scan(CHAT_PACKAGE, Deprecated.class,
                AnnotatedClassScannerTest.class.getClassLoader());
        assertTrue(found.isEmpty(), "records are not @Deprecated");
    }

    @Test
    void unknownPackageYieldsNothing() {
        List<Class<?>> found = AnnotatedClassScanner.scan("ink.tuanzi.neomacros.core.event.def.nope",
                ApiEvent.class, AnnotatedClassScannerTest.class.getClassLoader());
        assertTrue(found.isEmpty());
    }
}
