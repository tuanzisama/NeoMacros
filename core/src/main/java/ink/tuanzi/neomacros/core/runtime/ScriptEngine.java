package ink.tuanzi.neomacros.core.runtime;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Engine SPI implemented by the engine module (the only GraalJS
 * implementation). The interface exists so the runtime is testable with a
 * stub engine; engine dependencies stay isolated behind it.
 *
 * <p>Contract: ES module imports with {@code @neomacros/*} specifier
 * resolution, binding-tree injection, promise settlement scheduled onto the
 * context loop, engine exceptions translated to {@link ScriptError} (with
 * source location), and deterministic disposal.</p>
 */
public interface ScriptEngine extends AutoCloseable {

    /**
     * Opens an execution session for one script context. {@code moduleRoot}
     * is the script folder the session's module files must stay inside: the
     * engine confines real module resolution to that tree, so imports cannot
     * escape the loading module's folder.
     */
    EngineSession openSession(ScriptContext context, Path moduleRoot) throws ScriptError;

    /** Drops the module cache; the next import re-executes every module (reload semantics). */
    void clearModuleCache();

    /** Closes the engine and all sessions opened through it. */
    @Override
    void close();

    /**
     * Per-context execution surface. Every method runs on the context's event
     * loop thread (callers funnel through {@link ScriptContext#loop()});
     * promise settlements the engine schedules must go through the same
     * loop's executor.
     */
    interface EngineSession extends AutoCloseable {

        /** Injects the binding tree (the {@code neo.*} roots) into the engine context. */
        void installBindings(Map<String, Object> bindings) throws ScriptError;

        /** Imports one ES module and runs its top-level code. */
        void importModule(String moduleName, Path sourceFile) throws ScriptError;

        /**
         * Reads one object-valued export of the last imported module as a
         * neutral map. Engines that cannot read exports return an empty map.
         */
        default Map<String, Object> readExportedData(String exportName) throws ScriptError {
            return Map.of();
        }

        /**
         * Reads one function-valued export of the last imported module as a
         * runnable handle, invoked on the context loop. Engines that cannot
         * adapt functions return empty.
         */
        default java.util.Optional<Runnable> readExportedFunction(String exportName) throws ScriptError {
            return java.util.Optional.empty();
        }

        /**
         * Invokes one function-valued export of the last imported module with
         * a single payload argument, queued onto the context loop without
         * blocking the caller. Completes with false when the module does not
         * export an executable member of that name; completes exceptionally
         * when the invocation throws.
         */
        default java.util.concurrent.CompletableFuture<Boolean> invokeExport(String exportName, Object payload) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }

        /**
         * Evaluates one REPL expression in the session's global scope with
         * script semantics: top-level {@code let/const/function} persist in
         * the global lexical environment across evaluations, and a promise
         * completion value is awaited before the outcome is returned.
         * Guest-level failures (the expression threw) are reported through
         * {@link EvalOutcome}; engine or transport failures (busy loop,
         * unsupported engine) throw {@link ScriptError}.
         */
        default EvalOutcome eval(String expression) throws ScriptError {
            throw new ScriptError("this engine does not support REPL evaluation");
        }
    }
}
