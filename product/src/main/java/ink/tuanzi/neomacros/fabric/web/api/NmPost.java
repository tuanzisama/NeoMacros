package ink.tuanzi.neomacros.fabric.web.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a controller method as a POST route. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NmPost {

    /** The route path template below the API root. */
    String value();
}
