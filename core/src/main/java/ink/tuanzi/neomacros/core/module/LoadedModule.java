package ink.tuanzi.neomacros.core.module;

import ink.tuanzi.neomacros.core.event.Subscription;
import ink.tuanzi.neomacros.core.runtime.EvalOutcome;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptError;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One loaded script module: its discovery entry, execution context, engine
 * session, dispose hook, and every subscription or release hook it acquired
 * while loading or running. Unloading runs the dispose hook (when present)
 * and tears down all tracked resources in registration order.
 */
public final class LoadedModule implements AutoCloseable {

    private final ScriptEntry entry;
    private final Path source;
    private final ScriptContext context;
    private volatile ScriptMeta meta;
    private volatile ScriptEngine.EngineSession session;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final List<Runnable> releaseHooks = new ArrayList<>();
    private volatile Runnable disposeHook;
    private volatile long taskId;

    LoadedModule(ScriptEntry entry, Path source, ScriptContext context) {
        this.entry = entry;
        this.source = source;
        this.context = context;
        this.meta = entry.meta();
    }

    /** Installs the engine session opened for this module (loading step). */
    void sessionInstalled(ScriptEngine.EngineSession session) {
        this.session = session;
    }

    /** The discovery entry this module was loaded from. */
    public ScriptEntry entry() {
        return entry;
    }

    /** The module manifest. */
    public ScriptMeta meta() {
        return meta;
    }

    /** The entry source file. */
    public Path source() {
        return source;
    }

    /** The module's execution context (its event loop). */
    public ScriptContext context() {
        return context;
    }

    /** The engine session running this module. */
    ScriptEngine.EngineSession session() {
        return session;
    }

    /**
     * Evaluates one DevTools console (REPL) expression in this module's
     * engine session: the expression runs on the module's context loop and
     * sees the module's injected bindings. Returns the formatted outcome, or
     * throws {@link ScriptError} when the engine does not support evaluation
     * or the loop did not answer.
     */
    public EvalOutcome evaluate(String expression) throws ScriptError {
        ScriptEngine.EngineSession current = session;
        if (current == null) {
            throw new ScriptError("module '" + entry.name() + "' has no engine session");
        }
        return current.eval(expression);
    }

    /** Tracks one event subscription for auto-unsubscribe on unload. */
    public void track(Subscription subscription) {
        subscriptions.add(subscription);
    }

    /** Tracks one synchronous subscription for auto-unsubscribe on unload. */
    public void trackSync(Subscription subscription) {
        subscriptions.add(subscription);
    }

    /** Registers a release hook invoked at unload (command/HUD/keybind teardown). */
    public void onRelease(Runnable hook) {
        releaseHooks.add(hook);
    }

    /** Sets the module's dispose hook (the {@code dispose} export). */
    public void disposeHook(Runnable hook) {
        this.disposeHook = hook;
    }

    /** The dispose hook, when the module exported one. */
    public Optional<Runnable> disposeHook() {
        return Optional.ofNullable(disposeHook);
    }

    /** The task-manager id of this activation; 0 when no task registry is wired. */
    public long taskId() {
        return taskId;
    }

    /** Assigns the task-manager id (loading step). */
    void taskId(long taskId) {
        this.taskId = taskId;
    }

    /**
     * Unloads: runs release hooks and the dispose hook on the context loop,
     * unsubscribes everything, then closes the context. Idempotent.
     */
    @Override
    public void close() {
        if (context.isClosed()) {
            return;
        }
        var done = new java.util.concurrent.CompletableFuture<Void>();
        context.loop().execute(() -> {
            try {
                for (Runnable hook : releaseHooks) {
                    try {
                        hook.run();
                    } catch (Throwable t) {
                        // hook failures must not block teardown of later hooks
                    }
                }
                releaseHooks.clear();
                Runnable hook = disposeHook;
                if (hook != null) {
                    try {
                        hook.run();
                    } catch (Throwable t) {
                        // dispose failures must not block context close
                    }
                }
            } finally {
                done.complete(null);
            }
        });
        try {
            done.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException ignored) {
            // teardown proceeds even when the loop is stuck
        }
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
        context.close();
        // Release the engine session too: without this the polyglot context
        // (and its heap) would stay reachable in the engine until shutdown.
        ScriptEngine.EngineSession engineSession = session;
        if (engineSession != null) {
            try {
                engineSession.close();
            } catch (Throwable ignored) {
                // unload must not fail because the engine session is already gone
            }
        }
    }
}
