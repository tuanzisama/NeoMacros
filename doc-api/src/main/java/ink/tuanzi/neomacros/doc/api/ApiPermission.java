package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States the capability catalog entry a call requires.
 *
 * <p>Annotated members become gated bindings: every invocation consults the
 * script's live permission view, and a script whose declared
 * {@code meta.permissions} (plus grants) do not contain the catalog entry
 * gets a {@code PermissionError} on call instead of reaching the API. A
 * runtime grant (requestPermission acceptance, the trust command) takes
 * effect on the next call without a rebuild. API implementations must not
 * re-check permissions themselves; this annotation is the single gating
 * mechanism.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ApiPermission {

    /** Capability catalog key required for the call, e.g. {@code fs} or {@code net}. */
    String value();
}
