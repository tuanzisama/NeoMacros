package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import ink.tuanzi.neomacros.fabric.web.openapi.DocsRoutes;
import ink.tuanzi.neomacros.fabric.web.openapi.OpenApiGenerator;
import ink.tuanzi.neomacros.fabric.web.v1.ApiV1;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        String checkedIn;
        try (InputStream input = OpenApiGoldenTest.class
                .getResourceAsStream("/web/openapi-golden.json")) {
            if (input == null) {
                throw new IllegalStateException(
                        "product/src/test/resources/web/openapi-golden.json is missing");
            }
            checkedIn = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Intended contract changes refresh the golden with
        // -Dneomacros.refreshGolden=true -Dneomacros.goldenPath=<source file>.
        if (Boolean.getBoolean("neomacros.refreshGolden")) {
            Path target = Path.of(System.getProperty("neomacros.goldenPath",
                    "src/test/resources/web/openapi-golden.json"));
            Files.writeString(target, generatedContract().strip() + "\n",
                    StandardCharsets.UTF_8);
            return;
        }
        assertEquals(checkedIn.strip(), generatedContract().strip(),
                "the generated contract drifted from the golden snapshot;"
                        + " if the change is intended, refresh"
                        + " product/src/test/resources/web/openapi-golden.json");
    }

    /** The contract document over the live route set, byte-stable. */
    private static String generatedContract() {
        List<ink.tuanzi.neomacros.fabric.web.http.ApiRoute> routes = new ArrayList<>();
        routes.addAll(ink.tuanzi.neomacros.fabric.web.http.ApiRoute.scan(
                new ApiV1(stubBackend(), DIRECT, null, null, null, null, null,
                        null, null,
                        new ink.tuanzi.neomacros.fabric.web.console.ConsoleRing(64),
                        new ink.tuanzi.neomacros.fabric.web.net.NetRing(64),
                        new ink.tuanzi.neomacros.fabric.web.memory.MemoryRing(64))));
        routes.addAll(ink.tuanzi.neomacros.fabric.web.http.ApiRoute.scan(
                new DocsRoutes(() -> null)));

        OpenApiGenerator generator = new OpenApiGenerator(routes,
                "NeoMacros Web Panel API", "0.0.0-golden", "26.3", 52841);
        return Jsons.stringify(generator.generate(), true);
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

            @Override
            public ink.tuanzi.neomacros.fabric.web.v1.TasksResponse tasks() {
                return null;
            }

            @Override
            public void stopTask(long taskId) {
            }

            @Override
            public FileContent readFile(String path) {
                return null;
            }

            @Override
            public ink.tuanzi.neomacros.fabric.web.v1.FilesResponse listFiles(String id) {
                return null;
            }

            @Override
            public void deleteFile(String path) {
            }

            @Override
            public void moveFile(String from, String to) {
            }

            @Override
            public void makeDirectory(String path) {
            }

            @Override
            public void reveal(String path) {
            }

            @Override
            public FileWriteResult writeFile(String path, byte[] content, String ifMatch) {
                return null;
            }

            @Override
            public ink.tuanzi.neomacros.fabric.web.v1.MacroView createMacro(
                    String category, String name, String description) {
                return null;
            }

            @Override
            public ink.tuanzi.neomacros.fabric.web.v1.MacroView moveMacro(
                    String from, String to) {
                return null;
            }

            @Override
            public void deleteMacro(String id) {
            }

            @Override
            public ink.tuanzi.neomacros.fabric.web.PanelBackend.EvalExecution evaluateConsole(
                    String target, String expression) {
                return null;
            }

            @Override
            public ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse eventCatalog() {
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
