package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The {@code neo.fs} facade: filesystem access relative to the game
 * directory. Paths are resolved against the game directory; absolute paths
 * and traversal above it raise {@link SecurityException}. Every function is
 * async (promise-returning) and requires the {@code fs} capability.
 */
@ApiModule(name = "fs", category = "系统")
public final class FsApi {

    private final FsBridge bridge;
    private final ExecutorService ioExecutor;

    /** Sandbox root and IO executor are provided by the bootstrap. */
    public FsApi(FsBridge bridge, ExecutorService ioExecutor) {
        this.bridge = bridge;
        this.ioExecutor = ioExecutor;
    }

    /**
     * Reads a UTF-8 text file.
     *
     * @param path path relative to the game directory
     * @return promise of the file content
     * @zh 读取 UTF-8 文本文件，Promise 以文件内容落定。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<string>")
    @ApiPermission("fs")
    public CompletableFuture<String> readText(String path) {
        return supply(() -> Files.readString(bridge.resolve(path), StandardCharsets.UTF_8));
    }

    /**
     * Writes a UTF-8 text file (creating parent directories).
     *
     * @zh 写入 UTF-8 文本文件（自动创建父目录）。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    @ApiPermission("fs")
    public CompletableFuture<Void> writeText(String path, String content) {
        return run(() -> {
            Path target = bridge.resolve(path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        });
    }

    /**
     * Lists one directory level as a sorted list of entry names.
     *
     * @zh 列出目录下一级的条目名（排序后返回）。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<Array<string>>")
    @ApiPermission("fs")
    public CompletableFuture<List<String>> list(String path) {
        return supply(() -> {
            List<String> names = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(bridge.resolve(path))) {
                for (Path entry : stream) {
                    names.add(entry.getFileName().toString());
                }
            }
            names.sort(Comparator.naturalOrder());
            return names;
        });
    }

    /**
     * Creates a directory (including parents).
     *
     * @zh 创建目录（含父目录）。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    @ApiPermission("fs")
    public CompletableFuture<Void> mkdir(String path) {
        return run(() -> Files.createDirectories(bridge.resolve(path)));
    }

    /**
     * Deletes a file or empty directory.
     *
     * @zh 删除文件或空目录。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    @ApiPermission("fs")
    public CompletableFuture<Void> delete(String path) {
        return run(() -> Files.deleteIfExists(bridge.resolve(path)));
    }

    /**
     * Returns whether a file or directory exists.
     *
     * @zh 判断路径是否存在。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<boolean>")
    @ApiPermission("fs")
    public CompletableFuture<Boolean> exists(String path) {
        return supply(() -> Files.exists(bridge.resolve(path)));
    }

    private <T> CompletableFuture<T> supply(IoCall<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ioExecutor.execute(() -> {
            try {
                future.complete(call.run());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private CompletableFuture<Void> run(IoTask task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ioExecutor.execute(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private interface IoCall<T> {
        T run() throws IOException;
    }

    private interface IoTask {
        void run() throws IOException;
    }

    /** Sandbox boundary: resolves and validates paths against the game dir. */
    public interface FsBridge {
        Path resolve(String path);
    }
}
