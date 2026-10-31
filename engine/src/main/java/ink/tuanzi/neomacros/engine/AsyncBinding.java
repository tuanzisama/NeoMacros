package ink.tuanzi.neomacros.engine;

import org.graalvm.polyglot.Value;

/**
 * Binding function of an asynchronous script API. The engine wraps it into a
 * JS Promise factory ({@code (...args) => new Promise(...)}), so scripts see
 * fetch-style Promises while the Java side keeps CompletableFuture semantics.
 *
 * <p>The wrapper invokes {@link #execute} synchronously during Promise
 * construction (on the context loop thread); the binding schedules its work
 * and must invoke exactly one of the resolver/rejector functions, on the
 * context loop thread, when the outcome is ready.</p>
 */
@FunctionalInterface
public interface AsyncBinding {

    /** Runs the async operation, resolving or rejecting the promise later. */
    void execute(Value[] args, Value onResolve, Value onReject);
}
