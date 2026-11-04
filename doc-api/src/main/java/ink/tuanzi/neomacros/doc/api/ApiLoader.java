package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States the Fabric Loader version range an annotated element is available
 * in.
 *
 * <p>The value shares the {@link ApiMC} expression grammar (a comparison, an
 * inclusive range, or {@code *}). The binding builder evaluates it against
 * the actual running loader version when the guest tree is installed, so
 * members above the running loader's floor stay invisible; the documentation
 * labels the member with the same expression.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ApiLoader {

    /** Availability expression: a comparison or an inclusive Fabric Loader version range. */
    String value();
}
