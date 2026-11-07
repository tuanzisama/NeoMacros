package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an API class as one script-visible ES module.
 *
 * <p>The annotated class is exposed to scripts under two names that resolve
 * to the same binding object: the {@code neo.<name>} global namespace member
 * and the {@code @neomacros/<name>} ES module specifier. Modules with
 * {@link #global()} set to {@code false} are reachable only through the
 * ES module import.</p>
 *
 * <p>The module {@link #name()} is part of the public script surface and
 * follows the ECMAScript naming rules (camelCase); the {@link #category()}
 * is a display grouping used by the generated documentation.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiModule {

    /** ES module name and {@code neo.*} namespace member, e.g. {@code world}. */
    String name();

    /** Display grouping shown in the generated documentation; empty for uncategorized. */
    String category() default "";

    /** Whether the module is also reachable as a {@code neo.*} global member. */
    boolean global() default true;
}
