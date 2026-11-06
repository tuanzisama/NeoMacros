package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as optional for scripts: the binding layer accepts
 * calls that omit the parameter (and trailing ones after it), and the
 * generated TypeScript definitions render it with a {@code ?} marker.
 *
 * <p>Omitted parameters arrive at the facade as {@code null}; the facade
 * documents its own defaults.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ApiOptional {
}
