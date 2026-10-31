package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a record class as an event descriptor of the script event catalog.
 *
 * <p>Annotated records are registered in the event catalog under their
 * namespaced {@link #name()} (e.g. {@code player:join}). The flag
 * {@link #cancellable()} controls whether the event may be received through
 * the synchronous channel ({@code events.onSync}); it also drives the
 * documentation, which lists the sync channel only for cancellable events.</p>
 *
 * <p>The record components form the event payload exactly as the script sees
 * it: point-in-time snapshot data, never live game references.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiEvent {

    /** Namespaced event name used for subscribing and wildcard matching, e.g. {@code player:join}. */
    String name();

    /** Whether the event supports synchronous subscription and cancellation ({@code events.onSync}). */
    boolean cancellable() default false;
}
