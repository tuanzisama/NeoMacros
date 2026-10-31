package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an annotated element as experimental.
 *
 * <p>Experimental members carry an "experimental" badge in the generated
 * documentation and TypeScript definitions, telling script authors that the
 * member may change or disappear without a major version bump.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiExperimental {
}
