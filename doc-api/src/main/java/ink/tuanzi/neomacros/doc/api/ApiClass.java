package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a nested class as a script-facing type handed out to scripts.
 *
 * <p>The guest-side class tree (real ES classes supporting
 * {@code instanceof} and user subclassing) is generated from this
 * declaration. {@link #name()} is the script-visible type name; {@link
 * #kind()} splits live handles from plain data; {@link #wraps()} documents
 * the wrapped Minecraft class so script authors know which raw members the
 * escape layer ({@code raw} access) exposes.</p>
 *
 * <p>The documentation generator lists class types separately, splits
 * getters from action methods into two tables, and renders the wraps badge
 * and the module class tree.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiClass {

    /** Script-visible type name shown in the generated documentation and TypeScript definitions. */
    String name();

    /** Whether the class is a live handle or immutable plain data; defaults to a live handle. */
    ApiClassKind kind() default ApiClassKind.HANDLE;

    /** Fully-qualified name of the wrapped Minecraft class (Mojmap), or empty when the type is not a wrapper. */
    String wraps() default "";
}
