package ink.tuanzi.neomacros.fabric.web.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Route metadata feeding the OpenAPI generator: the OpenAPI tag, the human
 * summary, and the authentication style. Declared error responses come from
 * the repeatable {@link NmError}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NmApi {

    /** The OpenAPI tag grouping this route. */
    String tag();

    /** One-line summary shown in the generated contract. */
    String summary();

    /** The authentication the route requires. */
    NmAuth auth() default NmAuth.NM1;
}
