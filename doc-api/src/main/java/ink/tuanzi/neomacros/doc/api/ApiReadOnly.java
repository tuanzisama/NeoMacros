package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean getter or field as read-only for scripts.
 *
 * <p>Read-only members surface as {@code readonly} properties in the
 * generated TypeScript definitions and are labeled "read-only" in the
 * generated Markdown, telling the script author that assignment is not
 * supported.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface ApiReadOnly {
}
