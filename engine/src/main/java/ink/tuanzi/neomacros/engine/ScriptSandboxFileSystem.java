package ink.tuanzi.neomacros.engine;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
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
 * (the {@code moduleRoot} given at session open); {@code @neomacros/*}
 * specifiers resolve to virtual in-memory modules whose sources come from
 * the {@link ModuleResolver}. Path parsing stays permissive (it only builds
 * address objects); every read operation checks the boundary, so imports
 * cannot escape the module folder in any shape ({@code ..} segments,
 * absolute paths, or {@code file:} URIs).
 */
final class ScriptSandboxFileSystem implements org.graalvm.polyglot.io.FileSystem {

    static final String VIRTUAL_PREFIX = "@neomacros/";

    private final ModuleResolver resolver;
    private final Path moduleRoot;
    private final org.graalvm.polyglot.io.FileSystem delegate =
            org.graalvm.polyglot.io.FileSystem.newDefaultFileSystem();

    ScriptSandboxFileSystem(ModuleResolver resolver, Path moduleRoot) {
        this.resolver = resolver;
        this.moduleRoot = moduleRoot.toAbsolutePath().normalize();
    }

    /**
     * Rejects access to any real path outside the module folder. Windows
     * path comparison is case-insensitive, so drive-letter and casing drift
     * in URIs do not create a bypass.
     */
    private void checkInsideModuleRoot(Path path) throws IOException {
        if (path instanceof VirtualModulePath) {
            return;
        }
        Path absolute = path.isAbsolute() ? path.normalize() : path.toAbsolutePath().normalize();
        if (!absolute.startsWith(moduleRoot)) {
            throw new IOException("path escapes the module folder: " + path);
        }
    }

    @Override
    public Path parsePath(String path) {
        if (path.startsWith(VIRTUAL_PREFIX)) {
            return new VirtualModulePath(path);
        }
        return delegate.parsePath(path);
    }

    @Override
    public Path parsePath(URI uri) {
        if ("virtual".equals(uri.getScheme())) {
            String path = uri.getPath();
            return new VirtualModulePath(path.startsWith("/") ? path.substring(1) : path);
        }
        return delegate.parsePath(uri);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... options)
            throws IOException {
        if (path instanceof VirtualModulePath virtual) {
            if (resolver.virtualSource(virtual.specifier()).isEmpty()) {
                throw new IOException("unknown virtual module: " + virtual.specifier());
            }
            return;
        }
        checkInsideModuleRoot(path);
        delegate.checkAccess(path, modes, options);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs) throws IOException {
        if (path instanceof VirtualModulePath virtual) {
            String source = resolver.virtualSource(virtual.specifier())
                    .orElseThrow(() -> new IOException("unknown virtual module: " + virtual.specifier()));
            return new MemoryByteChannel(source.getBytes(StandardCharsets.UTF_8));
        }
        checkInsideModuleRoot(path);
        return delegate.newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                    DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (dir instanceof VirtualModulePath) {
            throw new IOException("virtual modules have no directory listing");
        }
        checkInsideModuleRoot(dir);
        return delegate.newDirectoryStream(dir, filter);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return path instanceof VirtualModulePath ? path : delegate.toAbsolutePath(path);
    }

    @Override
    public Path toRealPath(Path path, LinkOption... options) throws IOException {
        if (path instanceof VirtualModulePath) {
            return path;
        }
        checkInsideModuleRoot(path);
        return delegate.toRealPath(path, options);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
            throws IOException {
        if (path instanceof VirtualModulePath virtual) {
            byte[] content = resolver.virtualSource(virtual.specifier())
                    .orElseThrow(() -> new IOException("unknown virtual module"))
                    .getBytes(StandardCharsets.UTF_8);
            return Map.of("size", (Object) (long) content.length,
                    "isRegularFile", (Object) Boolean.TRUE,
                    "isDirectory", (Object) Boolean.FALSE);
        }
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
        if (path instanceof VirtualModulePath) {
            return "application/javascript+module";
        }
        return delegate.getMimeType(path);
    }

    /** Read-only in-memory channel over one virtual module source. */
    private static final class MemoryByteChannel implements SeekableByteChannel {
        private final byte[] data;
        private int position;
        private boolean closed;

        private MemoryByteChannel(byte[] data) {
            this.data = data;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public int read(java.nio.ByteBuffer dst) throws java.nio.channels.ClosedChannelException {
            if (closed) {
                throw new java.nio.channels.ClosedChannelException();
            }
            int remaining = data.length - position;
            if (remaining <= 0) {
                return -1;
            }
            int count = Math.min(remaining, dst.remaining());
            dst.put(data, position, count);
            position += count;
            return count;
        }

        @Override
        public int write(java.nio.ByteBuffer src) throws java.nio.channels.NonWritableChannelException {
            throw new java.nio.channels.NonWritableChannelException();
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            position = (int) newPosition;
            return this;
        }

        @Override
        public long size() {
            return data.length;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws java.nio.channels.NonWritableChannelException {
            throw new java.nio.channels.NonWritableChannelException();
        }
    }
}
