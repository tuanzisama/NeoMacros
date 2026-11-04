package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States the NeoMacros API version an annotated element first appeared in.
 *
 * <p>This is the NeoMacros release timeline (e.g. {@code "0.1.0"}), rendered
 * as a "since" badge in the generated documentation. Minecraft version
 * availability is a separate timeline tracked by {@link ApiMC}; the two are
 * never mixed.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ApiSince {

    /** NeoMacros API version the element first appeared in, e.g. {@code 0.1.0}. */
    String value();
}
