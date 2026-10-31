package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the TypeScript type emitted for the annotated member or
 * parameter.
 *
 * <p>The binding layer maps Java signatures onto ECMAScript types, but the
 * mapping is not always expressible from the Java signature alone. This
 * annotation states the script-visible type explicitly, e.g.
 * {@code "Promise<void>"} for an async action or {@code "Array<ItemSnapshot>"}
 * for a list result. The generated documentation and TypeScript definitions
 * both render the stated type verbatim.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
public @interface ApiType {

    /** TypeScript type expression replacing the inferred one, e.g. {@code Promise<void>}. */
    String value();
}
