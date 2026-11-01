package ink.tuanzi.neomacros.fabric.web.openapi;

import com.google.gson.JsonElement;
import ink.tuanzi.neomacros.fabric.web.api.NmApi;
import ink.tuanzi.neomacros.fabric.web.api.NmAuth;
import ink.tuanzi.neomacros.fabric.web.api.NmGet;

/**
 * The generated-contract endpoint: serves the OpenAPI document the
 * generator builds over the live route set (itself included). The document
 * supplier is deferred — the generator needs this route's own metadata, so
 * the doc lands in the holder after the route scan.
 */
public final class DocsRoutes {

    private final java.util.function.Supplier<JsonElement> document;

    /** Creates the controller over a deferred document supplier. */
    public DocsRoutes(java.util.function.Supplier<JsonElement> document) {
        this.document = document;
    }

    @NmGet("/api-docs/openapi.json")
    @NmApi(tag = "api-docs", summary = "The generated OpenAPI contract for this API"
            + " (unsigned; import it into Apifox or Swagger UI)", auth = NmAuth.NONE)
    public JsonElement openapi() {
        return document.get();
    }
}
