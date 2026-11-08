package ink.tuanzi.neomacros.core.runtime;

import ink.tuanzi.neomacros.core.thread.ContextEventLoop;

import java.util.concurrent.atomic.AtomicLong;

/**
 * One loaded script's execution environment: identity, the dedicated event
 * loop thread, and the async primitives bound to that loop. Scripts see a
 * single-threaded world; everything they touch funnels through this context.
 */
public final class ScriptContext implements AutoCloseable {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final long id;
    private final String name;
    private final ContextEventLoop loop;
    private final AsyncPrimitives async;
    private volatile boolean closed;

    ScriptContext(String name, ContextEventLoop loop, AsyncPrimitives async) {
        this.id = SEQUENCE.incrementAndGet();
        this.name = name;
        this.loop = loop;
        this.async = async;
    }

    /** Stable numeric identity within this runtime. */
    public long id() {
        return id;
    }

    /** Script module name (file stem). */
    public String name() {
        return name;
    }

    /** The context's event loop; all script-observable work runs on it. */
    public ContextEventLoop loop() {
        return loop;
    }

    /** The loop thread id ({@link ContextEventLoop#threadId()}); 0 before the thread exists. */
    public long threadId() {
        return loop.threadId();
    }

    /** Async primitives (sleep/ticks/nextTick/waitFor/runOnMain) bound to this context. */
    public AsyncPrimitives async() {
        return async;
    }

    /** Returns whether the context has been closed. */
    public boolean isClosed() {
        return closed;
    }

    /** Stops the loop; further submissions are rejected. */
    @Override
    public void close() {
        closed = true;
        loop.close();
    }
}
