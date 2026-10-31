package ink.tuanzi.neomacros.core.runtime;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stub engine driving the thread-model tests: records every SPI interaction
 * without executing real script code. Sessions run on the calling (context
 * loop) thread exactly like the real engine will.
 */
final class StubEngine implements ScriptEngine {

    final List<String> openedContexts = new ArrayList<>();
    final List<String> importedModules = new ArrayList<>();
    final List<Map<String, Object>> installedBindings = new ArrayList<>();
    final AtomicBoolean cacheCleared = new AtomicBoolean();
    final AtomicBoolean closed = new AtomicBoolean();
    final AtomicBoolean failImports = new AtomicBoolean();

    @Override
    public EngineSession openSession(ScriptContext context) throws ScriptError {
        openedContexts.add(context.name());
        return new EngineSession() {
            @Override
            public void installBindings(Map<String, Object> bindings) {
                installedBindings.add(bindings);
            }

            @Override
            public void importModule(String moduleName, Path sourceFile) throws ScriptError {
                if (failImports.get()) {
                    throw new ScriptError("stub import failure", null, moduleName, 3, 7);
                }
                importedModules.add(moduleName);
            }

            @Override
            public void close() {
                // Sessions are stateless in the stub.
            }
        };
    }

    @Override
    public void clearModuleCache() {
        cacheCleared.set(true);
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
