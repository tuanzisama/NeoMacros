package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.bindgen.ApiModelExtractor;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.ModuleBinding;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import org.graalvm.polyglot.Value;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-path binding integration: a registry-built binding tree installed
 * into the GraalJS engine, exercised by real scripts — live properties,
 * sync and async functions, permission denial, and node-range trimming.
 */
class BindingIntegrationTest {

    /** Engine-side fixture facade (mirrors the core bindgen fixture shape). */
    @ApiModule(name = "fixture", category = "test")
    @ApiSince("0.1.0")
    public static class FixtureFacade {
        private final AtomicInteger reads = new AtomicInteger();

        /** Live counter property. @zh 活计数器属性。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public int getReads() {
            return reads.incrementAndGet();
        }

        /** Sync function. @zh 同步函数。 */
        @ApiSince("0.1.0")
        public String greet(String name) {
            return "hi " + name;
        }

        /** Async function mapping to a promise. @zh 异步函数。 */
        @ApiSince("0.1.0")
        public CompletableFuture<String> fetch(String what) {
            return CompletableFuture.completedFuture("got:" + what);
        }

        /** Permission-gated function. @zh 权限门控函数。 */
        @ApiSince("0.1.0")
        @ink.tuanzi.neomacros.doc.api.ApiPermission("fs")
        public String secret() {
            return "s3cret";
        }

        /** Modern-node-only function. @zh 仅新节点可用。 */
        @ApiSince("0.1.0")
        @ink.tuanzi.neomacros.doc.api.ApiMC(">=1.20.6")
        public String futureOnly() {
            return "modern";
        }
    }

    @TempDir
    Path scriptsDir;

    private ScriptThreadPool pool;
    private ScriptRuntime runtime;
    private GraalJsEngine engine;
    private GraalJsEngine.GraalJsSession session;

    @BeforeEach
    void setUp() throws Exception {
        pool = new ScriptThreadPool(2);
        BindingRegistry registry = new BindingRegistry("26.3");
        registry.register(FixtureFacade.class);
        engine = new GraalJsEngine(testResolver());
        runtime = new ScriptRuntime(engine, null,
                new EventCatalog(), new EventBus(new EventCatalog(), Runnable::run), pool, 500);
        var context = runtime.createContext("bindings");
        session = (GraalJsEngine.GraalJsSession) engine.openSession(context);
        session.installBindings(registry.neoTree(PermissionView.of(Set.of())));
    }

    @AfterEach
    void tearDown() {
        runtime.close();
        pool.close();
    }

    private ModuleResolver testResolver() {
        return new ModuleResolver() {
            @Override
            public Optional<String> virtualSource(String specifier) {
                if ("@neomacros/core".equals(specifier)) {
                    return Optional.of("const m = globalThis.neo.fixture;\n"
                            + "export const fixture = m;\n");
                }
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

    private Value sessionGlobal(String name) throws Exception {
        return session.global(name);
    }

    @Test
    void livePropertyInvokesGetterPerAccess() throws Exception {
        Path entry = writeModule("live-prop.mjs",
                "globalThis.__first = neo.fixture.reads;\n"
                + "globalThis.__second = neo.fixture.reads;\n");

        session.importModule("live-prop", entry);
        assertEquals(1, sessionGlobal("__first").asInt());
        assertEquals(2, sessionGlobal("__second").asInt(),
                "property reads invoke the live getter, not a snapshot");
    }

    @Test
    void syncFunctionCallsWithConvertedArguments() throws Exception {
        Path entry = writeModule("sync-fn.mjs",
                "globalThis.__greeting = neo.fixture.greet('world');\n");

        session.importModule("sync-fn", entry);
        assertEquals("hi world", sessionGlobal("__greeting").asString());
    }

    @Test
    void asyncFunctionMapsToAwaitablePromise() throws Exception {
        Path entry = writeModule("async-fn.mjs",
                "const result = await neo.fixture.fetch('coffee');\n"
                + "globalThis.__result = result;\n");

        session.importModule("async-fn", entry);
        assertEquals("got:coffee", sessionGlobal("__result").asString());
    }

    @Test
    void permissionDeniedSurfacesAsError() throws Exception {
        Path entry = writeModule("denied.mjs",
                "try {\n"
                + "    neo.fixture.secret();\n"
                + "    globalThis.__caught = 'not-reached';\n"
                + "} catch (e) {\n"
                + "    globalThis.__caught = String(e.message || e);\n"
                + "}\n");

        session.importModule("denied", entry);
        String caught = sessionGlobal("__caught").asString();
        assertTrue(caught.contains("permission"),
                () -> "permission denial surfaces: " + caught);
    }

    @Test
    void permissionGrantedCallableWhenDeclared() throws Exception {
        BindingRegistry registry = new BindingRegistry("26.3");
        registry.register(FixtureFacade.class);
        // Fresh session: neo installs once per context.
        var grantedContext = runtime.createContext("granted");
        var grantedSession = (GraalJsEngine.GraalJsSession) engine.openSession(grantedContext);
        grantedSession.installBindings(registry.neoTree(PermissionView.of(Set.of("fs"))));

        Path entry = writeModule("granted.mjs",
                "globalThis.__secret = neo.fixture.secret();\n");

        grantedSession.importModule("granted", entry);
        assertEquals("s3cret", grantedSession.global("__secret").asString());
    }

    @Test
    void esmChannelSeesTheSameBindingObject() throws Exception {
        Path entry = writeModule("identity.mjs",
                "import { fixture } from '@neomacros/core';\n"
                + "globalThis.__same = fixture === neo.fixture;\n");

        session.importModule("identity", entry);
        assertTrue(sessionGlobal("__same").asBoolean(),
                "both channels resolve to the identical binding object");
    }
}
