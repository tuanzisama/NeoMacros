package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import ink.tuanzi.neomacros.fabric.web.openapi.DocsRoutes;
import ink.tuanzi.neomacros.fabric.web.openapi.OpenApiGenerator;
import ink.tuanzi.neomacros.fabric.web.v1.ApiV1;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The generated contract must stay byte-identical to the checked-in golden
 * snapshot: any route, schema, or description change that alters the public
 * contract fails here first.
 */
class OpenApiGoldenTest {

    @Test
    void generatedContractMatchesTheGoldenSnapshot() throws IOException {
        List<ink.tuanzi.neomacros.fabric.web.http.ApiRoute> routes = new ArrayList<>();
        routes.addAll(ink.tuanzi.neomacros.fabric.web.http.ApiRoute.scan(
                new ApiV1(stubBackend(), DIRECT, null, null, null, null)));
        routes.addAll(ink.tuanzi.neomacros.fabric.web.http.ApiRoute.scan(
                new DocsRoutes(() -> null)));

        OpenApiGenerator generator = new OpenApiGenerator(routes,
                "NeoMacros Web Panel API", "0.0.0-golden", "26.3", 52841);
        String generated = Jsons.stringify(generator.generate(), true);

        String checkedIn;
        try (InputStream input = OpenApiGoldenTest.class
                .getResourceAsStream("/web/openapi-golden.json")) {
            if (input == null) {
                throw new IllegalStateException(
                        "product/src/test/resources/web/openapi-golden.json is missing");
            }
            checkedIn = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(checkedIn.strip(), generated.strip(),
                "the generated contract drifted from the golden snapshot;"
                        + " if the change is intended, refresh"
                        + " product/src/test/resources/web/openapi-golden.json");
    }

    private static PanelBackend stubBackend() {
        return new PanelBackend() {
            @Override
            public ink.tuanzi.neomacros.fabric.web.v1.Meta meta() {
                return null;
            }

            @Override
            public ink.tuanzi.neomacros.fabric.web.v1.StateSnapshot snapshot() {
                return null;
            }

            @Override
            public ink.tuanzi.neomacros.fabric.web.v1.MacroView macroAction(String id,
                    String action) {
                return null;
            }
        };
    }

    /** A direct GameRunner: the generator only needs the route metadata. */
    private static final GameRunner DIRECT = new GameRunner() {
        @Override
        public <T> T call(java.util.function.Supplier<T> task) {
            return task.get();
        }
    };
}
