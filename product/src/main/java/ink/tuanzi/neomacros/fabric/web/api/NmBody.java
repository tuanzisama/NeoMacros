package ink.tuanzi.neomacros.fabric.web.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a handler parameter to the decoded JSON request body. The parameter
 * type must be a record; a body that does not deserialize raises
 * {@code invalid_request}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface NmBody {
}
