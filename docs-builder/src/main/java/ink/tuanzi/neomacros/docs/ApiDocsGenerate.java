package ink.tuanzi.neomacros.docs;

import jdk.javadoc.doclet.Doclet;

import javax.tools.Diagnostic;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
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
 * Command-line entry point that generates the API documentation from the
 * real facade sources of the main build (core + ports source trees), as
 * opposed to the fixture sources the test harness drives.
 *
 * <p>Usage: {@code ApiDocsGenerate <mdOutDir> <tsOutDir> <coreSourceRoot> <portsSourceRoot>}.
 * Writes {@code <mdOutDir>/api/*.md} (bilingual pages plus warnings.txt) and
 * {@code <tsOutDir>/types/neomacros.d.ts}. Exits non-zero when generation
 * fails, after printing all error diagnostics.
 */
public final class ApiDocsGenerate {

    private ApiDocsGenerate() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("usage: ApiDocsGenerate <mdOutDir> <tsOutDir> <coreSourceRoot> <portsSourceRoot>");
            System.exit(2);
        }
        Path mdOut = Path.of(args[0]);
        Path tsOut = Path.of(args[1]);
        Path coreRoot = Path.of(args[2]);
        Path portsRoot = Path.of(args[3]);

        List<Path> sources = facadeSources(coreRoot);
        if (sources.isEmpty()) {
            System.err.println("no facade sources found under " + coreRoot);
            System.exit(2);
        }

        Run md = generate(MDDoclet.class, mdOut, coreRoot, portsRoot, sources);
        Run ts = generate(TSDoclet.class, tsOut, coreRoot, portsRoot, sources);

        for (Run run : List.of(md, ts)) {
            run.errors().forEach(e -> System.err.println("error: " + e));
            run.warnings().forEach(w -> System.out.println("warning: " + w));
            if (!run.ok()) {
                System.err.println("tool output:");
                System.err.println(run.toolOutput());
            }
        }
        if (!md.ok() || !ts.ok()) {
            System.err.println("API documentation generation failed");
            System.exit(1);
        }
        System.out.println("generated " + md.filesWritten() + " markdown page(s) and "
                + ts.filesWritten() + " TypeScript definition file(s)");
    }

    /**
     * Runs one doclet over the facade sources. Mirrors the test harness:
     * the facade and referenced sources resolve through the source path,
     * while the doc-api annotations come from the published jar on the
     * classpath.
     */
    private static Run generate(Class<? extends Doclet> doclet, Path outDir,
                                Path coreRoot, Path portsRoot, List<Path> sources) throws IOException {
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
            fm.setLocation(StandardLocation.SOURCE_PATH, List.of(coreRoot.toFile(), portsRoot.toFile()));
            // The runtime classpath carries everything referenced by facade
            // signatures: the doc-api annotations and any library types the
            // transitive source resolution pulls in (e.g. gson).
            fm.setLocation(StandardLocation.CLASS_PATH, runtimeClasspath());
            DocumentationTool.DocumentationTask task = tool.getTask(output, fm, diagnostics::add,
                    doclet, options, fm.getJavaFileObjects(sources.toArray(Path[]::new)));
            Boolean ok = task.call();

            List<String> errors = diagnostics.stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> d.getMessage(Locale.ROOT))
                    .toList();
            List<String> warnings = diagnostics.stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.WARNING)
                    .map(d -> d.getMessage(Locale.ROOT))
                    .toList();
            long files = countFiles(outDir);
            return new Run(Boolean.TRUE.equals(ok) && errors.isEmpty(), errors, warnings, files,
                    output.toString());
        }
    }

    /** All Java sources under the core API package tree, in stable order. */
    private static List<Path> facadeSources(Path coreRoot) throws IOException {
        Path apiRoot = coreRoot.resolve("ink/tuanzi/neomacros/core/api");
        Path eventRoot = coreRoot.resolve("ink/tuanzi/neomacros/core/event/def");
        if (!Files.isDirectory(apiRoot)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(apiRoot);
             Stream<Path> eventFiles = Files.isDirectory(eventRoot)
                     ? Files.walk(eventRoot) : Stream.<Path>empty()) {
            return Stream.concat(files, eventFiles)
                    .filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** The JVM's own runtime classpath, used as the javadoc resolution classpath. */
    private static List<File> runtimeClasspath() {
        return Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(s -> !s.isBlank())
                .map(File::new)
                .toList();
    }

    private static long countFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Outcome of one generation run. */
    private record Run(boolean ok, List<String> errors, List<String> warnings, long filesWritten,
                       String toolOutput) {
    }
}
