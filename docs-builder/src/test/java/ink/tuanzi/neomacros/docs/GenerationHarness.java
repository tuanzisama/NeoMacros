package ink.tuanzi.neomacros.docs;

import jdk.javadoc.doclet.Doclet;

import javax.tools.Diagnostic;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Runs the javadoc tool in-process over the fixture sources with a given
 * doclet, wiring the doc-api annotation jar onto the classpath and collecting
 * diagnostics for assertions.
 */
final class GenerationHarness {

    private GenerationHarness() {
    }

    /** Outcome of one documentation generation run. */
    record Run(boolean ok, Path outDir, List<String> errors,
               List<String> warnings, String toolOutput) {
    }

    /** Generates documentation for the fixture API into {@code outDir}. */
    static Run generate(Class<? extends Doclet> doclet, Path outDir) throws IOException {
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();
        StringWriter output = new StringWriter();

        List<String> options = List.of(
                "-d", outDir.toString(),
                "-tag", "zh:a",
                "-tag", "zh-param:a",
                "-tag", "zh-return:a",
                "-tag", "zh-example:a");

        try (StandardJavaFileManager fm = tool.getStandardFileManager(diagnostics::add,
                Locale.ROOT, StandardCharsets.UTF_8)) {
            fm.setLocation(StandardLocation.SOURCE_PATH, List.of(fixturesRoot().toFile()));
            fm.setLocation(StandardLocation.CLASS_PATH, List.of(docApiJar()));
            DocumentationTool.DocumentationTask task = tool.getTask(output, fm, diagnostics::add,
                    doclet, options, fm.getJavaFileObjects(fixtureSources().toArray(Path[]::new)));
            Boolean ok = task.call();

            List<String> errors = diagnostics.stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> d.getMessage(Locale.ROOT))
                    .toList();
            List<String> warnings = diagnostics.stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.WARNING)
                    .map(d -> d.getMessage(Locale.ROOT))
                    .toList();
            return new Run(Boolean.TRUE.equals(ok) && errors.isEmpty(), outDir, errors, warnings,
                    output.toString());
        }
    }

    private static Path fixturesRoot() {
        return Path.of("src", "test", "fixtures");
    }

    private static List<Path> fixtureSources() throws IOException {
        try (Stream<Path> files = Files.walk(fixturesRoot())) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** Resolves the doc-api jar from the test classpath (published coordinates, not sources). */
    private static java.io.File docApiJar() {
        try {
            var source = Class.forName("ink.tuanzi.neomacros.doc.api.ApiModule")
                    .getProtectionDomain().getCodeSource();
            if (source == null) {
                throw new IllegalStateException("cannot locate the doc-api jar on the test classpath");
            }
            return new java.io.File(source.getLocation().toURI());
        } catch (ClassNotFoundException | java.net.URISyntaxException e) {
            throw new IllegalStateException("doc-api annotation jar missing from the test classpath", e);
        }
    }
}
