package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * The {@code neo.storage} module: persistent per-script key-value storage.
 * Values must be JSON-serializable plain data (objects, arrays, strings,
 * numbers, booleans); each script owns an isolated store keyed by its source
 * file name, surviving reloads and game restarts.
 *
 * @zh {@code neo.storage}：按脚本隔离的持久键值存储。值必须是可 JSON 序列化的纯数据（对象/数组/字符串/数字/布尔）；每个脚本按源文件名拥有独立存储，跨重载与游戏重启保留。
 */
@ApiModule(name = "storage", category = "核心")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class StorageApi {

    /**
     * Reads one value, or null when absent.
     *
     * @zh 读取一个值；不存在时返回 null。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Object get(String key) {
        return storage().get(key);
    }

    /**
     * Writes one value (JSON-serializable plain data); null deletes the key.
     *
     * @zh 写入一个值；传 null 等同删除该键。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void put(String key, Object value) {
        storage().put(key, value);
    }

    /**
     * Removes one key.
     *
     * @zh 删除一个键。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void remove(String key) {
        storage().delete(key);
    }

    /**
     * Returns whether the key exists.
     *
     * @zh 判断键是否存在。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean has(String key) {
        return storage().has(key);
    }

    private ink.tuanzi.neomacros.core.storage.ModuleStorage storage() {
        LoadedModule owner = LoadTracker.current();
        if (owner == null) {
            throw new IllegalStateException(
                    "storage requires a script context (stores are isolated per script)");
        }
        return ScriptApiEnvironment.storageFor(owner.meta().name());
    }
}
