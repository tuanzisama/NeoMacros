package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a nested class as a live handle type handed out to scripts.
 *
 * <p>Handles are the live-reference form of game state: bean getters read
 * through the port layer in real time, {@code snapshot()} produces a
 * point-in-time copy, and a stale handle (e.g. an unloaded entity) returns
 * {@code undefined} from its getters instead of throwing. The documentation
 * generator lists handle types separately and splits getters from action
 * methods into two tables.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiHandle {

    /** Handle type name shown in the generated documentation and TypeScript definitions. */
    String value();
}
