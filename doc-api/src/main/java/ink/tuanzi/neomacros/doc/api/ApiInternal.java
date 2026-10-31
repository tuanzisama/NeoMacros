package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an annotated element as internal runtime surface.
 *
 * <p>Internal members exist at runtime (e.g. test bridge bindings) but are
 * omitted from the generated documentation and TypeScript definitions: they
 * are not part of the public script API and carry no stability guarantee.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiInternal {
}
