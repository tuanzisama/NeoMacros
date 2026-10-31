package ink.tuanzi.neomacros.engine;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;

/**
 * Synthetic path representing one virtual {@code @neomacros/*} module inside
 * the sandbox file system. Instances only exist so the module loader can
 * address the generated module source; every file operation besides reading
 * is rejected.
 */
final class VirtualModulePath implements Path {

    static final String EXTENSION = ".mjs";

    private final String specifier;

    VirtualModulePath(String specifier) {
        this.specifier = specifier;
    }

    /** The bare module specifier behind this path (without the extension). */
    String specifier() {
        return specifier;
    }

    @Override
    public FileSystem getFileSystem() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAbsolute() {
        return true;
    }

    @Override
    public Path getRoot() {
        return null;
    }

    @Override
    public Path getFileName() {
        return new VirtualModulePath(specifier);
    }

    @Override
    public Path getParent() {
        return null;
    }

    @Override
    public int getNameCount() {
        return 1;
    }

    @Override
    public Path getName(int index) {
        if (index != 0) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        return this;
    }

    @Override
    public Iterator<Path> iterator() {
        return java.util.List.<Path>of(this).iterator();
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean startsWith(Path other) {
        return equals(other);
    }

    @Override
    public boolean startsWith(String other) {
        return toString().equals(other);
    }

    @Override
    public boolean endsWith(Path other) {
        return equals(other);
    }

    @Override
    public boolean endsWith(String other) {
        return toString().equals(other);
    }

    @Override
    public Path normalize() {
        return this;
    }

    @Override
    public Path resolve(Path other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path resolve(String other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path resolveSibling(Path other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path resolveSibling(String other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path relativize(Path other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path toAbsolutePath() {
        return this;
    }

    @Override
    public Path toRealPath(LinkOption... options) {
        return this;
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events,
                             WatchEvent.Modifier... modifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Path other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VirtualModulePath && ((VirtualModulePath) other).specifier.equals(specifier);
    }

    @Override
    public int hashCode() {
        return specifier.hashCode();
    }

    @Override
    public String toString() {
        return specifier + EXTENSION;
    }

    @Override
    public URI toUri() {
        return URI.create("virtual://neomacros/" + specifier);
    }
}
