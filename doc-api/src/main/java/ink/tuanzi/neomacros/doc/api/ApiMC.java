package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States the Minecraft version range an annotated element is available in.
 *
 * <p>The value is a comparison ({@code ">=1.20.6"}) or an inclusive range
 * ({@code "1.18-1.21"}). The binding builder evaluates it against the current
 * milestone node and omits bindings outside the range, so scripts running on
 * a node below the threshold never see the member; the documentation labels
 * the member with the same expression.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ApiMC {

    /** Availability expression: a comparison or an inclusive Minecraft version range. */
    String value();
}
