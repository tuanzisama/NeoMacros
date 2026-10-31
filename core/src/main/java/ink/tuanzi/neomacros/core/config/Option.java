package ink.tuanzi.neomacros.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one config option on a section class. Field type determines the
 * persisted shape (boolean, int, long, double, String, and their lists);
 * the field name is the JSON key unless {@link #name()} overrides it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {

    /** JSON key; empty uses the field name. */
    String name() default "";

    /** Human-readable description used by the settings UI and generated docs. */
    String description() default "";

    /** Lower bound for numeric options; {@link #NO_BOUND} when unbounded. */
    double min() default NO_BOUND;

    /** Upper bound for numeric options; {@link #NO_BOUND} when unbounded. */
    double max() default NO_BOUND;

    /** Sentinel meaning "no bound". */
    double NO_BOUND = Double.NEGATIVE_INFINITY;
}
