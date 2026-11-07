package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an API class as one script-visible API module, reachable from
 * scripts as the {@code neo.<name>} global namespace member.
 *
 * <p>The module {@link #name()} is part of the public script surface and
 * follows the ECMAScript naming rules (camelCase); the {@link #category()}
 * is a display grouping used by the generated documentation.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiModule {

    /** Module name and {@code neo.*} namespace member, e.g. {@code world}. */
    String name();

    /** Display grouping shown in the generated documentation; empty for uncategorized. */
    String category() default "";
}
