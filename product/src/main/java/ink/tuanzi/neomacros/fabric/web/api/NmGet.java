package ink.tuanzi.neomacros.fabric.web.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a controller method as a GET route. The path template may embed
 * parameters: {@code {name}} matches one segment, {@code {name+}} greedily
 * matches the remaining segments.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NmGet {

    /** The route path template below the API root. */
    String value();
}
