package ink.tuanzi.neomacros.fabric.web.http;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches a request against the scanned routes and invokes the winner.
 * Matching prefers the most specific template (fewest parameters) so
 * literal routes win over parameterized ones.
 */
public final class ApiRouter {

    private final List<ApiRoute> routes;

    /** Creates the router over the scanned routes of the given controllers. */
    public ApiRouter(Object... controllers) {
        List<ApiRoute> routes = new ArrayList<>();
        for (Object controller : controllers) {
            routes.addAll(ApiRoute.scan(controller));
        }
        routes.sort((a, b) -> Integer.compare(countParams(a), countParams(b)));
        this.routes = routes;
    }

    private static int countParams(ApiRoute route) {
        return route.templateParams().size();
    }

    /** All scanned routes (the generator's input). */
    public List<ApiRoute> routes() {
        return routes;
    }

    /** Finds and invokes the matching route for the request. */
    public void dispatch(ApiContext context) throws Exception {
        List<String> segments = ApiContext.segments(context.rawPath());
        ApiRoute matched = null;
        ApiRoute.Bindings bindings = null;
        for (ApiRoute route : routes) {
            ApiRoute.Bindings candidate = route.match(context.method(), segments);
            if (candidate != null) {
                matched = route;
                bindings = candidate;
                break;
            }
        }
        if (matched == null) {
            throw ApiError.notFound("no route for " + context.method() + " " + context.rawPath());
        }
        bindings.pathParams().forEach(context::pathParam);
        Object result = matched.invoke(context);
        if (context.handled()) {
            return;
        }
        if (result == null) {
            ApiContext.respond(context.exchange(), 204, "application/json", new byte[0]);
            return;
        }
        byte[] payload = ink.tuanzi.neomacros.fabric.web.json.Jsons
                .stringify(result).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ApiContext.respond(context.exchange(), 200, "application/json; charset=utf-8", payload);
    }
}
