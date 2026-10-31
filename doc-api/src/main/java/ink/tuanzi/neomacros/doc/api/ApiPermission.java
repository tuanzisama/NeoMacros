package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States the capability catalog entry a call requires.
 *
 * <p>Annotated bindings are wrapped by the permission proxy at binding build
 * time: a script whose declared {@code meta.permissions} do not contain the
 * catalog entry gets a {@code PermissionError} on call instead of reaching
 * the facade. Facade implementations must not re-check permissions
 * themselves; this annotation is the single gating mechanism.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiPermission {

    /** Capability catalog key required for the call, e.g. {@code fs} or {@code net}. */
    String value();
}
