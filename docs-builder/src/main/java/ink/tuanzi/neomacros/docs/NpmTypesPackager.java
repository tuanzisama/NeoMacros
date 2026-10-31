package ink.tuanzi.neomacros.docs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Packages the generated TypeScript definitions ({@code neomacros.d.ts}) into
 * an npm-publishable {@code @neomacros/types} package with a
 * {@code package.json} manifest and README.
 */
public final class NpmTypesPackager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private NpmTypesPackager() {
    }

    /** Creates an npm package layout from the generated TypeScript definitions. */
    public static void packageTypes(Path dtsSource, Path outputDir, String version) throws IOException {
        Files.createDirectories(outputDir);
        Files.copy(dtsSource, outputDir.resolve("neomacros.d.ts"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(outputDir.resolve("package.json"), packageJson(version),
                StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve("README.md"), readme(), StandardCharsets.UTF_8);
    }

    private static String packageJson(String version) {
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("name", "@neomacros/types");
        pkg.put("version", version);
        pkg.put("description",
                "TypeScript definitions for NeoMacros scripting API");
        pkg.put("types", "./neomacros.d.ts");
        pkg.put("license", "MIT");
        return GSON.toJson(pkg) + "\n";
    }

    private static String readme() {
        return """
                # @neomacros/types

                TypeScript definitions for the NeoMacros scripting API.

                ## Install

                ```bash
                npm install @neomacros/types
                ```

                ## Usage

                Add the package to your `tsconfig.json` `types` field or use a
                triple-slash reference in your script:

                ```ts
                /// <reference types="@neomacros/types" />
                ```
                """;
    }
}
