package ink.tuanzi.neomacros.fabric;

import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Creates the macros folder layout on first install and seeds one runnable
 * example per category. Seeding happens only when the macros root itself is
 * missing, so deleting a single example is stable across restarts while
 * deleting the whole root resets to a fresh install. Missing category
 * directories are always recreated empty.
 */
public final class ExampleSeeder {

    /** Example files below {@code /neomacros-examples/} on the classpath. */
    private static final List<String> EXAMPLE_FILES = List.of(
            "keypress/hello-key/neomacro.yml",
            "keypress/hello-key/index.js",
            "event/hello-world/neomacro.yml",
            "event/hello-world/index.js",
            "services/uptime/neomacro.yml",
            "services/uptime/index.js");

    private ExampleSeeder() {
    }

    /** Prepares the layout, returning whether examples were seeded this run. */
    public static boolean ensureLayout(Path macrosRoot, java.util.function.Consumer<String> info) {
        if (!Files.isDirectory(macrosRoot)) {
            createCategoryDirs(macrosRoot);
            seedExamples(macrosRoot);
            info.accept("first install: created " + macrosRoot.getFileName()
                    + " with example macros (keypress/event/services)");
            return true;
        }
        for (ScriptCategory category : ScriptCategory.values()) {
            Path dir = macrosRoot.resolve(category.dirName());
            if (!Files.isDirectory(dir)) {
                try {
                    Files.createDirectories(dir);
                } catch (IOException e) {
                    info.accept("cannot create category directory " + category.dirName() + ": "
                            + e.getMessage());
                }
            }
        }
        return false;
    }

    private static void createCategoryDirs(Path macrosRoot) {
        for (ScriptCategory category : ScriptCategory.values()) {
            try {
                Files.createDirectories(macrosRoot.resolve(category.dirName()));
            } catch (IOException e) {
                throw new IllegalStateException("cannot create category directory: "
                        + category.dirName(), e);
            }
        }
    }

    private static void seedExamples(Path macrosRoot) {
        try {
            for (String example : EXAMPLE_FILES) {
                Path target = macrosRoot.resolve(example);
                Files.createDirectories(target.getParent());
                try (InputStream in = ExampleSeeder.class
                        .getResourceAsStream("/neomacros-examples/" + example)) {
                    if (in == null) {
                        throw new IOException("missing bundled example resource: " + example);
                    }
                    Files.copy(in, target);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot seed example macros", e);
        }
    }
}
