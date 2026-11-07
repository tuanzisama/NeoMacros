package ink.tuanzi.neomacros.engine;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;

/**
 * Sandboxed file system of the script engine: real module files are served
 * from the default file system, but only inside the owning module's folder
 * (the {@code moduleRoot} given at session open). Path parsing stays
 * permissive (it only builds address objects); every read operation checks
 * the boundary, so imports cannot escape the module folder in any shape
 * ({@code ..} segments, absolute paths, or {@code file:} URIs).
 */
final class ScriptSandboxFileSystem implements org.graalvm.polyglot.io.FileSystem {

    /** Module specifiers of the removed ESM import channel. */
    private static final String MODULE_PREFIX = "@neomacros";

    private final Path moduleRoot;
    private final org.graalvm.polyglot.io.FileSystem delegate =
            org.graalvm.polyglot.io.FileSystem.newDefaultFileSystem();

    ScriptSandboxFileSystem(Path moduleRoot) {
        this.moduleRoot = moduleRoot.toAbsolutePath().normalize();
    }

    /**
     * Rejects access to any real path outside the module folder. Windows
     * path comparison is case-insensitive, so drive-letter and casing drift
     * in URIs do not create a bypass. Specifiers of the removed module
     * import channel fail with a hint pointing at the {@code neo} global.
     */
    private void checkInsideModuleRoot(Path path) throws IOException {
        if (!path.isAbsolute() && path.getNameCount() > 0
                && path.getName(0).toString().startsWith(MODULE_PREFIX)) {
            String specifier = path.subpath(0, path.getNameCount()).toString().replace('\\', '/');
            throw new IOException("unknown module '" + specifier
                    + "': API modules live on the neo global object, not in imports");
        }
        Path absolute = path.isAbsolute() ? path.normalize() : path.toAbsolutePath().normalize();
        if (!absolute.startsWith(moduleRoot)) {
            throw new IOException("path escapes the module folder: " + path);
        }
    }

    @Override
    public Path parsePath(String path) {
        return delegate.parsePath(path);
    }

    @Override
    public Path parsePath(URI uri) {
        return delegate.parsePath(uri);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... options)
            throws IOException {
        checkInsideModuleRoot(path);
        delegate.checkAccess(path, modes, options);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs) throws IOException {
        checkInsideModuleRoot(path);
        return delegate.newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                    DirectoryStream.Filter<? super Path> filter) throws IOException {
        checkInsideModuleRoot(dir);
        return delegate.newDirectoryStream(dir, filter);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return delegate.toAbsolutePath(path);
    }

    @Override
    public Path toRealPath(Path path, LinkOption... options) throws IOException {
        checkInsideModuleRoot(path);
        return delegate.toRealPath(path, options);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
            throws IOException {
        checkInsideModuleRoot(path);
        return delegate.readAttributes(path, attributes, options);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        throw new IOException("the script sandbox is read-only");
    }

    @Override
    public void delete(Path path) throws IOException {
        throw new IOException("the script sandbox is read-only");
    }

    @Override
    public String getMimeType(Path path) {
        return delegate.getMimeType(path);
    }
}
