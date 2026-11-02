package ink.tuanzi.neomacros.fabric.web.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Route metadata feeding the OpenAPI generator: the OpenAPI tag, the human
 * summary, and the authentication style. Declared error responses come from
 * the repeatable {@link NmError}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NmApi {

    /** The OpenAPI tag grouping this route. */
    String tag();

    /** One-line summary shown in the generated contract. */
    String summary();

    /** The authentication the route requires. */
    NmAuth auth() default NmAuth.NM1;

    /**
     * The success status when the handler writes its own response
     * (streaming routes return void); 0 lets the generator infer it.
     */
    int success() default 0;

    /**
     * The success response content type for self-writing handlers; empty
     * keeps the SSE default for streaming routes.
     */
    String produces() default "";

    /**
     * The request content type for handlers that read the raw body instead
     * of a typed {@link ink.tuanzi.neomacros.fabric.web.api.NmBody} record;
     * empty emits no request body in the contract.
     */
    String consumes() default "";
}
