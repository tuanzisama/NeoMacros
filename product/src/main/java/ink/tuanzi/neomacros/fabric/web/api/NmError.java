package ink.tuanzi.neomacros.fabric.web.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** One declared error response of a route, feeding the OpenAPI generator. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(NmError.List.class)
public @interface NmError {

    /** The HTTP status code. */
    int status();

    /** The machine-readable error code carried in the response body. */
    String code();

    /** When the error occurs. */
    String desc();

    /** Container for repeated {@link NmError} declarations. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        NmError[] value();
    }
}
