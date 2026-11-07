package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import ink.tuanzi.neomacros.core.util.JsonLite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The {@code neo.fs} module: filesystem access relative to the game
 * directory. Paths are resolved against the game directory; absolute paths
 * and traversal above it raise {@link SecurityException}. Every function is
 * async (promise-returning) and requires the {@code fs} capability.
 *
 * @zh {@code neo.fs}：以游戏目录为根的文件系统访问。路径相对游戏目录解析；绝对路径与向上越界抛 SecurityException。全部函数异步返回 Promise，且需 fs 能力授权。
 */
@ApiModule(name = "fs", category = "系统")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<boolean>")
    @ApiPermission("fs")
    public CompletableFuture<Boolean> exists(String path) {
        return supply(() -> Files.exists(bridge.resolve(path)));
    }

    /**
     * Reads a JSON file into neutral values (objects, arrays, strings,
     * numbers, booleans).
     *
     * @zh 读取 JSON 文件为中性值（对象/数组/字符串/数字/布尔）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<any>")
    @ApiPermission("fs")
    public CompletableFuture<Object> readJSON(String path) {
        return supply(() -> JsonLite.parse(
                Files.readString(bridge.resolve(path), StandardCharsets.UTF_8)));
    }

    /**
     * Writes neutral values into a JSON file (creating parent directories).
     *
     * @zh 把中性值写入 JSON 文件（自动创建父目录）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    @ApiPermission("fs")
    public CompletableFuture<Void> writeJSON(String path, Object value) {
        return run(() -> {
            Path target = bridge.resolve(path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, JsonLite.write(value), StandardCharsets.UTF_8);
        });
    }

    /**
     * Reads one file as raw bytes (each element 0-255; the engine renders
     * the result as a Uint8Array).
     *
     * @zh 按字节读取文件（元素 0-255，引擎侧渲染为 Uint8Array）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<Uint8Array>")
    @ApiPermission("fs")
    public CompletableFuture<byte[]> readBytes(String path) {
        return supply(() -> Files.readAllBytes(bridge.resolve(path)));
    }

    /**
     * Writes raw bytes to a file (creating parent directories). The data
     * argument is an array-like of byte values.
     *
     * @zh 按字节写入文件（自动创建父目录），data 为字节值数组。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    @ApiPermission("fs")
    public CompletableFuture<Void> writeBytes(String path, Object data) {
        return run(() -> {
            Path target = bridge.resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, toBytes(data));
        });
    }

    /**
     * Returns file metadata: {@code size}, {@code mtimeMs}, {@code isDir},
     * {@code isFile}.
     *
     * @zh 返回文件元信息：size/mtimeMs/isDir/isFile。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<object>")
    @ApiPermission("fs")
    public CompletableFuture<Map<String, Object>> stat(String path) {
        return supply(() -> {
            Path target = bridge.resolve(path);
            var attributes = Files.readAttributes(target, java.nio.file.attribute.BasicFileAttributes.class);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("size", attributes.size());
            out.put("mtimeMs", attributes.lastModifiedTime().toMillis());
            out.put("isDir", attributes.isDirectory());
            out.put("isFile", attributes.isRegularFile());
            return out;
        });
    }

    /**
     * Moves a file or directory; fails when the target already exists.
     *
     * @zh 移动文件或目录；目标已存在时失败。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    @ApiPermission("fs")
    public CompletableFuture<Void> move(String from, String to) {
        return run(() -> {
            Path target = bridge.resolve(to);
            Files.createDirectories(target.getParent());
            Files.move(bridge.resolve(from), target);
        });
    }

    /**
     * Copies a file; fails when the target already exists.
     *
     * @zh 复制文件；目标已存在时失败。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    @ApiPermission("fs")
    public CompletableFuture<Void> copy(String from, String to) {
        return run(() -> {
            Path target = bridge.resolve(to);
            Files.createDirectories(target.getParent());
            Files.copy(bridge.resolve(from), target);
        });
    }

    /** Coerces an array-like payload into raw bytes. */
    private static byte[] toBytes(Object data) {
        if (data instanceof List<?> numbers) {
            byte[] out = new byte[numbers.size()];
            for (int i = 0; i < out.length; i++) {
                Object entry = numbers.get(i);
                if (!(entry instanceof Number number)) {
                    throw new IllegalArgumentException("byte data must be numbers");
                }
                out[i] = number.byteValue();
            }
            return out;
        }
        if (data instanceof String text) {
            return text.getBytes(StandardCharsets.ISO_8859_1);
        }
        throw new IllegalArgumentException("byte data must be an array of numbers");
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

    /**
     * Returns the path relative to the game directory root; paths outside
     * the root throw.
     *
     * @zh 返回相对游戏目录根的路径；越出根目录的路径抛错。
     * @zh-param path 绝对或游戏目录相对路径
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String toRelative(String path) {
        Path resolved = bridge.resolve(path);
        Path root = bridge.resolve("");
        return root.relativize(resolved).toString().replace('\\', '/');
    }

    /**
     * Opens one file handle for streamed reads, attribute queries, and the
     * java-gated raw path.
     *
     * @zh 打开一个文件句柄，用于流式读取、属性查询与 java 门控的原始路径。
     * @zh-param path 游戏目录相对路径
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public FileHandle open(String path) {
        return new FileHandle(bridge.resolve(path), ioExecutor);
    }

    /**
     * Live handle over one file inside the game directory. Reads run on the
     * IO executor; {@code raw} exposes the host path behind the {@code java}
     * permission.
     *
     * @zh 游戏目录内单个文件的活句柄。读取在 IO 线程池执行；{@code raw} 在 {@code java} 权限后暴露宿主路径。
     */
    @ApiClass(name = "FileHandle", kind = ApiClassKind.HANDLE)
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class FileHandle implements ScriptHandle {

        private final Path path;
        private final ExecutorService ioExecutor;

        FileHandle(Path path, ExecutorService ioExecutor) {
            this.path = path;
            this.ioExecutor = ioExecutor;
        }

        /**
         * Reads the whole file as bytes.
         *
         * @zh 读取整个文件为字节。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<Uint8Array>")
        public CompletableFuture<byte[]> readBytes() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return java.nio.file.Files.readAllBytes(path);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("cannot read: " + path, e);
                }
            }, ioExecutor);
        }

        /**
         * The file attributes as {@code {size, mtimeMs, isDir, isFile}}.
         *
         * @zh 文件属性 {@code {size, mtimeMs, isDir, isFile}}。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Map<String, Object> getAttributes() {
            try {
                var view = java.nio.file.Files.readAttributes(path,
                        java.nio.file.attribute.BasicFileAttributes.class);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("size", view.size());
                out.put("mtimeMs", view.lastModifiedTime().toMillis());
                out.put("isDir", view.isDirectory());
                out.put("isFile", view.isRegularFile());
                return out;
            } catch (java.io.IOException e) {
                throw new IllegalStateException("cannot stat: " + path, e);
            }
        }

        /**
         * The raw host {@code Path} object. Requires the {@code java}
         * permission.
         *
         * @zh 原始宿主 {@code Path} 对象。需 {@code java} 权限。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiPermission("java")
        public Object getRaw() {
            return path;
        }

        @Override
        public String toString() {
            return "FileHandle(" + path + ")";
        }
    }
}
