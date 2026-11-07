package ink.tuanzi.neomacros.core.util;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans the classpath for classes of one package (subpackages included)
 * carrying a given annotation.
 *
 * <p>Used to collect annotated event payload records and API API classes
 * (the registration mechanism shared with the binding builder). Works over
 * directory and jar classpath entries; on launchers where the module classpath
 * is not reflected in {@code java.class.path} (e.g. nested mod jars), callers
 * fall back to explicit class registration.</p>
 */
public final class AnnotatedClassScanner {

    private AnnotatedClassScanner() {
    }

    /**
     * Returns the classes inside {@code packageName} (subpackages included)
     * that carry {@code annotation}, in name order. Classes whose loading
     * fails are skipped; scanning never throws for individual entries.
     */
    public static List<Class<?>> scan(String packageName, Class<? extends Annotation> annotation,
                                      ClassLoader loader) {
        List<String> names = new ArrayList<>();
        String packagePath = packageName.replace('.', '/');
        String[] classpathEntries = System.getProperty("java.class.path", "").split(java.io.File.pathSeparator);
        for (String entry : classpathEntries) {
            try {
                Path path = Path.of(entry);
                if (Files.isDirectory(path)) {
                    scanDirectory(path, packagePath, packageName, names);
                } else if (Files.isRegularFile(path) && entry.endsWith(".jar")) {
                    scanJar(path, packagePath, packageName, names);
                }
            } catch (IOException | RuntimeException e) {
                // Unreadable classpath entries are skipped; a usable entry provides the classes.
            }
        }
        List<Class<?>> out = new ArrayList<>();
        for (String name : names.stream().distinct().sorted().toList()) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                if (type.isAnnotationPresent(annotation)) {
                    out.add(type);
                }
            } catch (LinkageError | ClassNotFoundException | RuntimeException e) {
                // Dependencies of individual classes may be missing on a partial classpath.
            }
        }
        return List.copyOf(out);
    }

    private static void scanDirectory(Path root, String packagePath, String packageName,
                                      List<String> names) throws IOException {
        Path packageDir = root.resolve(packagePath);
        if (!Files.isDirectory(packageDir)) {
            return;
        }
        try (var stream = Files.walk(packageDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".class") && !fileName.contains("$")) {
                    String relative = packageDir.relativize(file).toString().replace('\\', '/');
                    String fqcn = packageName + "." + relative.substring(0, relative.length() - 6).replace('/', '.');
                    names.add(fqcn);
                }
            }
        }
    }

    private static void scanJar(Path jar, String packagePath, String packageName,
                                List<String> names) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String entryName = zipEntry.getName();
                if (!zipEntry.isDirectory()
                        && entryName.startsWith(packagePath + "/")
                        && entryName.endsWith(".class")) {
                    String relative = entryName.substring(packagePath.length() + 1);
                    String fileName = relative.substring(relative.lastIndexOf('/') + 1);
                    if (!fileName.contains("$")) {
                        String fqcn = packageName + "."
                                + relative.substring(0, relative.length() - 6).replace('/', '.');
                        names.add(fqcn);
                    }
                }
            }
        }
    }
}
