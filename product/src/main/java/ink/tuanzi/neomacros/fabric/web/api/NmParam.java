package ink.tuanzi.neomacros.fabric.web.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a handler parameter from the request's path or query. The value
 * names the template parameter ({@code {name}} or {@code {name+}}) or the
 * query key; path bindings win over query values.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface NmParam {

    /** The template parameter or query key. */
    String value();

    /** Whether this is the multi-segment ({@code +}) path parameter form. */
    boolean multi() default false;
}
