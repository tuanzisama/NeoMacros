package ink.tuanzi.neomacros.fabric.web.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A description string for a record component or handler parameter. Javadoc
 * is unreadable through runtime reflection, so schema descriptions live here.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.PARAMETER, ElementType.METHOD})
public @interface NmDesc {

    /** The description text. */
    String value();
}
