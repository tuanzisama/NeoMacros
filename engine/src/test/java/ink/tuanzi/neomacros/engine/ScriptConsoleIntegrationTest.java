package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.console.ScriptConsole;
import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The console global end-to-end over a real session: browser-shaped methods
 * emit structured {@link ScriptConsoleEvent}s with call-site locations,
 * host values render through the component view hook, and the rate limiter
 * bounds a flood. Events arrive on the core pipeline thread, so the tests
 * drain a queue with a timeout.
 */
class ScriptConsoleIntegrationTest {

    @TempDir
    Path scriptsDir;

    private ScriptThreadPool pool;
    private ScriptRuntime runtime;
    private GraalJsEngine engine;
    private GraalJsEngine.GraalJsSession session;
    private ScriptContext context;
    private LinkedBlockingQueue<ScriptConsoleEvent> events;

    private final java.util.function.Consumer<ScriptConsoleEvent> dispatcher = event -> {
        if (event != null) {
            events.add(event);
        }
    };

    @BeforeEach
    void setUp() throws Exception {
        events = new LinkedBlockingQueue<>();
        ScriptConsole.installDispatcher(dispatcher);
        pool = new ScriptThreadPool(2);
        engine = new GraalJsEngine(testResolver());
        runtime = new ScriptRuntime(engine, null,
                new EventCatalog(), new EventBus(new EventCatalog(), Runnable::run), pool, 500);
        context = runtime.createContext("console");
        session = (GraalJsEngine.GraalJsSession) engine.openSession(context, scriptsDir);
    }

    @AfterEach
    void tearDown() {
        ScriptConsole.removeDispatcher(dispatcher);
        ScriptConsole.installComponentView(null);
        runtime.close();
        pool.close();
    }

    private ModuleResolver testResolver() {
        return new ModuleResolver() {
            @Override
            public Optional<String> virtualSource(String specifier) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> fileFor(String specifier) {
                return Optional.empty();
            }
        };
    }

    private Path writeModule(String fileName, String source) throws Exception {
        Path file = scriptsDir.resolve(fileName);
        Files.writeString(file, source);
        return file;
    }

    private ScriptConsoleEvent next() throws InterruptedException {
        ScriptConsoleEvent event = events.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "an event should arrive on the pipeline");
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arg(ScriptConsoleEvent event, int index) {
        return (Map<String, Object>) event.args().get(index);
    }

    @Test
    void basicsCarryLevelsAndCallSites() throws Exception {
        Path entry = writeModule("console-basic.mjs",
                "console.log('hello', 42, true);\n"
                + "console.warn('careful');\n");
        session.importModule("console-basic", entry);

        ScriptConsoleEvent first = next();
        assertEquals("log", first.kind());
        assertEquals("log", first.level());
        assertEquals("console", first.macro());
        assertEquals("console-basic.mjs", first.file());
        assertEquals(1, first.line());
        assertEquals("str", arg(first, 0).get("t"));
        assertEquals("hello", arg(first, 0).get("v"));
        assertEquals("num", arg(first, 1).get("t"));
        assertEquals(42, arg(first, 1).get("v"));
        assertEquals("bool", arg(first, 2).get("t"));
        assertEquals(Boolean.TRUE, arg(first, 2).get("v"));

        ScriptConsoleEvent second = next();
        assertEquals("warn", second.kind());
        assertEquals("warn", second.level());
        assertEquals(2, second.line());
    }

    @Test
    void objectsAndErrorsAreStructured() throws Exception {
        Path entry = writeModule("console-objects.mjs",
                "console.log({a: 1, b: 'x'});\n"
                + "console.log([10, 20]);\n"
                + "console.log(new Error('kaput'));\n");
        session.importModule("console-objects", entry);

        ScriptConsoleEvent object = next();
        assertEquals("obj", arg(object, 0).get("t"));
        @SuppressWarnings("unchecked")
        List<Object> props = (List<Object>) arg(object, 0).get("props");
        assertEquals(2, props.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> firstProp = (Map<String, Object>) props.get(0);
        assertEquals("a", firstProp.get("k"));
        @SuppressWarnings("unchecked")
        Map<String, Object> firstValue = (Map<String, Object>) firstProp.get("v");
        assertEquals(1, firstValue.get("v"));

        ScriptConsoleEvent array = next();
        assertEquals("arr", arg(array, 0).get("t"));
        assertEquals(2L, arg(array, 0).get("len"));

        ScriptConsoleEvent error = next();
        assertEquals("err", arg(error, 0).get("t"));
        assertEquals("Error", arg(error, 0).get("name"));
        assertEquals("kaput", arg(error, 0).get("msg"));
        assertNotNull(arg(error, 0).get("stack"));
    }

    @Test
    void nullUndefinedAndSpecials() throws Exception {
        Path entry = writeModule("console-specials.mjs",
                "console.log(null, undefined);\n"
                + "console.count('hits');\n"
                + "console.count('hits');\n"
                + "console.time('bench');\n"
                + "console.timeEnd('bench');\n"
                + "console.group('ctx');\n"
                + "console.log('nested');\n"
                + "console.groupEnd();\n"
                + "console.assert(false, 'failed');\n"
                + "console.trace('here');\n");
        session.importModule("console-specials", entry);

        ScriptConsoleEvent scalars = next();
        assertEquals("null", arg(scalars, 0).get("t"));
        assertEquals("undef", arg(scalars, 1).get("t"));

        ScriptConsoleEvent count1 = next();
        assertEquals("count", count1.kind());
        assertEquals("hits", count1.label());
        assertEquals(1, count1.count());

        ScriptConsoleEvent count2 = next();
        assertEquals(2, count2.count());

        ScriptConsoleEvent timeEnd = next();
        assertEquals("timeEnd", timeEnd.kind());
        assertEquals("bench", timeEnd.label());
        assertNotNull(timeEnd.ms());
        assertTrue(timeEnd.ms() >= 0);

        ScriptConsoleEvent group = next();
        assertEquals("group", group.kind());
        assertEquals(0, group.depth());
        assertEquals("ctx", arg(group, 0).get("v"));

        ScriptConsoleEvent nested = next();
        assertEquals(1, nested.depth(), "entries inside the group indent by one");

        ScriptConsoleEvent assertion = next();
        assertEquals("assert", assertion.kind());
        assertEquals("error", assertion.level());
        assertEquals("failed", arg(assertion, 0).get("v"));

        ScriptConsoleEvent trace = next();
        assertEquals("trace", trace.kind());
        assertNotNull(trace.stack());
        assertTrue(trace.stack().contains("console-specials.mjs"),
                "the guest stack names the script file");
    }

    @Test
    void hostValuesRenderThroughTheComponentViewAndReflection() throws Exception {
        ScriptConsole.installComponentView(value -> value instanceof Badge badge
                ? new ChatComponent(badge.getLabel(), "red", true, null, null, null, null,
                        List.of(ChatComponent.plain("!")))
                : null);
        Badge badge = new Badge("hi");
        session.installBindings(Map.of("probe", badge,
                "probeClass", Badge.class,
                "probeThrowable", new IllegalStateException("broken")));
        Path entry = writeModule("console-host.mjs",
                "console.log(neo.probe);\n"
                + "console.log(neo.probeClass);\n"
                + "console.log(neo.probeThrowable);\n");
        session.importModule("console-host", entry);

        ScriptConsoleEvent component = next();
        assertEquals("comp", arg(component, 0).get("t"));
        @SuppressWarnings("unchecked")
        Map<String, Object> rich = (Map<String, Object>) arg(component, 0).get("rich");
        assertEquals("hi", rich.get("text"));
        assertEquals("red", rich.get("color"));
        assertEquals(Boolean.TRUE, rich.get("bold"));

        ScriptConsoleEvent klass = next();
        assertEquals("class", arg(klass, 0).get("t"));
        assertEquals(Badge.class.getName(), arg(klass, 0).get("name"));

        ScriptConsoleEvent throwable = next();
        assertEquals("err", arg(throwable, 0).get("t"));
        assertEquals("java.lang.IllegalStateException", arg(throwable, 0).get("name"));
        assertEquals("broken", arg(throwable, 0).get("msg"));
    }

    @Test
    void floodsAreRateLimitedWithASuppressionNotice() throws Exception {
        Path entry = writeModule("console-flood.mjs",
                "for (let i = 0; i < 500; i++) { console.log('spam', i); }\n");
        session.importModule("console-flood", entry);

        int received = 0;
        boolean sawNotice = false;
        ScriptConsoleEvent event;
        while ((event = events.poll(2, TimeUnit.SECONDS)) != null) {
            received++;
            if (event.level().equals("warn") && String.valueOf(arg(event, 0).get("v"))
                    .contains("suppressed by rate limit")) {
                sawNotice = true;
            }
        }
        assertTrue(received < 400, "the flood must be bounded, got " + received);
        assertTrue(sawNotice, "a suppression notice must surface");
    }

    @Test
    void theConsoleGlobalIsFrozen() throws Exception {
        Path entry = writeModule("console-frozen.mjs",
                "console = null;\n");
        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                ink.tuanzi.neomacros.core.runtime.ScriptError.class,
                () -> session.importModule("console-frozen", entry));
        assertTrue(String.valueOf(failure.getMessage()).contains("console"),
                "assigning the console global must fail: " + failure.getMessage());
    }

    /** Host fixture standing in for a game type (never read from JS). */
    public static final class Badge {

        private final String label;

        public Badge(String label) {
            this.label = label;
        }

        /** Getter surfaced by the generic reflection fallback. */
        public String getLabel() {
            return label;
        }
    }
}
