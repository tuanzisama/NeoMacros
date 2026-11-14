package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.core.api.types.CustomImage;
import ink.tuanzi.neomacros.core.api.types.Texture;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.model.WindowData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The {@code neo.hud} module: persistent overlay layers on top of the
 * vanilla HUD. {@link #layer(String, Map)} hands out id-keyed
 * {@link HudLayer} handles whose elements are retained in core and
 * re-projected every frame by the engine. Layers are namespaced per module
 * owner ({@code <module>:<id>}); unloading a module disposes every layer it
 * created, so overlays disappear with the script that made them.
 *
 * <p>{@link #addText} / {@link #removeText} are the frozen 0.1.0
 * compatibility face: they upsert/remove text elements on the implicit
 * {@code $legacy} layer of the calling module, preserving the original
 * {@code Promise} semantics the compat bridge chains on.</p>
 *
 * <p>{@link #texture(String, Map)} is the minimal texture registry
 * (G-001 拍板⑥): idempotent owner-namespaced {@link Texture} handles
 * whose GPU slots are released on {@code dispose()} or module unload.
 * Named {@link CustomImage} canvases self-register into the same
 * registry, so {@link #getTextures()} lists both kinds.</p>
 *
 * @zh {@code neo.hud} 模块：叠加在原版 HUD 之上的持久覆盖层。{@link #layer(String, Map)} 返回按 id 键控的 {@link HudLayer} 句柄，其元素保留于 core、由引擎每帧重新投影。层按模块 owner 命名空间隔离（{@code <模块>:<id>}）；卸载模块会失效其创建的全部层，覆盖层随脚本一同消失。
 * @zh {@link #addText} / {@link #removeText} 是冻结的 0.1.0 兼容面：在调用方模块的隐式 {@code $legacy} 层上 upsert/移除文本元素，保留兼容桥所链依赖的原始 {@code Promise} 语义。
 * @zh {@link #texture(String, Map)} 是最小纹理注册表（G-001 拍板⑥）：幂等、按 owner 命名空间隔离的 {@link Texture} 句柄，其 GPU 槽位在 {@code dispose()} 或模块卸载时释放。具名 {@link CustomImage} 画布自注册进同一注册表，因此 {@link #getTextures()} 同时列出两类纹理。
 */
@ApiModule(name = "hud", category = "界面")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class HudApi {

    /** Layer id used by the frozen {@link #addText} sugar. */
    private static final String LEGACY_LAYER = "$legacy";

    /**
     * The calling module's identity for layer namespacing: public id
     * isolation ({@code <owner>:<id>}) and automatic clear on unload. Host
     * side only — never crosses to scripts.
     */
    public interface OwnerScope {
        /** The owning module's name (its discovery entry name). */
        String name();

        /** Registers a hook invoked when the owner unloads. */
        void onRelease(Runnable hook);
    }

    /** One registered layer plus the owner it is namespaced under. */
    private record Entry(HudLayer layer, String ownerName) {
    }

    /** One registered texture plus the owner it is namespaced under. */
    private record TextureEntry(Texture texture, String ownerName) {
    }

    private final RenderPort render;
    private final Executor mainExecutor;
    private final Supplier<WindowData> window;
    private final Supplier<OwnerScope> ownerProbe;
    /** Keyed by the owner-qualified layer key; guarded by {@code lock}. */
    private final Map<String, Entry> layers = new LinkedHashMap<>();
    /** Keyed by the owner-qualified texture key; guarded by {@code lock}. */
    private final Map<String, TextureEntry> textures = new LinkedHashMap<>();
    private final Object lock = new Object();
    /** Owners that already wired their release hook; identity-keyed. */
    private final Set<OwnerScope> wiredOwners = ConcurrentHashMap.newKeySet();

    /**
     * Creates the API over the render port (bootstrap-provided).
     *
     * @param render       HUD render port receiving layer painters
     * @param mainExecutor executor hopping port calls onto the main thread
     * @param window       viewport supplier (scaled window pixels)
     */
    public HudApi(RenderPort render, Executor mainExecutor, Supplier<WindowData> window) {
        this(render, mainExecutor, window, HudApi::currentOwner);
    }

    /** Test seam: the same facade with an injectable owner probe. */
    HudApi(RenderPort render, Executor mainExecutor, Supplier<WindowData> window,
            Supplier<OwnerScope> ownerProbe) {
        this.render = render;
        this.mainExecutor = mainExecutor;
        this.window = window;
        this.ownerProbe = ownerProbe;
        // Named CustomImage canvases self-register into this registry so
        // hud.textures lists them and module unload reclaims them (G-038).
        CustomImage.installRegistry(this::registerCanvas);
    }

    /**
     * Returns the owner scope for the calling thread (a module's context
     * loop resolves its own module), or null outside module execution.
     */
    private static OwnerScope currentOwner() {
        LoadedModule module = LoadTracker.current();
        return module == null ? null : new ModuleOwner(module);
    }

    /** Production owner scope backed by the loaded module instance. */
    private record ModuleOwner(LoadedModule module) implements OwnerScope {
        @Override
        public String name() {
            return module.entry().name();
        }

        @Override
        public void onRelease(Runnable hook) {
            module.onRelease(hook);
        }
    }

    /**
     * Returns the layer under {@code id} for the calling module, creating
     * and registering it on first use. Idempotent: repeated calls with the
     * same owner and id hand back the same live handle. Layer ids are
     * namespaced per module, so two scripts may both use {@code "coords"}
     * without touching each other; unloading the module disposes its
     * layers.
     *
     * @param id   public layer id (must not start with {@code $})
     * @param opts layer options: {@code z} (cross-layer draw order,
     *             default {@code 0})
     * @return the live layer handle
     * @zh 返回调用方模块下 {@code id} 对应的层，首次调用时创建并注册。幂等：同 owner 同 id 重复调用返回同一存活句柄。层 id 按模块命名空间隔离，两个脚本可同时使用 {@code "coords"} 而互不干扰；模块卸载时其层全部失效。
     * @zh-param id 公开层 id（不得以 {@code $} 开头）
     * @zh-param opts 层选项：{@code z}（跨层绘制序，默认 {@code 0}）
     * @zh-return 存活的层句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public HudLayer layer(String id,
            @ApiOptional @ApiType("{ z?: number }") Map<String, Object> opts) {
        requirePublicId(id);
        int z = opts == null ? 0 : intOf(opts.get("z"), 0);
        return getOrCreate(id, z, ownerProbe.get());
    }

    /**
     * Every live layer of the calling module, ordered by z ascending then
     * id; implicit {@code $legacy} sugar layers are excluded. Empty when
     * the caller owns no layers.
     *
     * @zh 调用方模块的全部存活层，按 z 升序、id 次序排列；隐式 {@code $legacy} 糖层不列出。调用方没有层时为空。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<HudLayer> getLayers() {
        OwnerScope owner = ownerProbe.get();
        String ownerName = owner == null ? null : owner.name();
        List<HudLayer> out = new ArrayList<>();
        synchronized (lock) {
            for (Entry entry : layers.values()) {
                if (!Objects.equals(entry.ownerName(), ownerName)) {
                    continue;
                }
                HudLayer layer = entry.layer();
                if (layer.isDisposed() || layer.getId().startsWith("$")) {
                    continue;
                }
                out.add(layer);
            }
        }
        out.sort(Comparator.comparing(HudLayer::getZIndex)
                .thenComparing(HudLayer::getId));
        return List.copyOf(out);
    }

    /**
     * Removes the calling module's layer under {@code id} and disposes
     * every element handle it holds. Unknown ids are ignored.
     *
     * @param id public layer id (must not start with {@code $})
     * @zh 移除调用方模块下 {@code id} 的层并失效其持有的全部元素句柄。未知 id 忽略。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void removeLayer(String id) {
        requirePublicId(id);
        Entry entry;
        synchronized (lock) {
            entry = layers.remove(key(id, ownerProbe.get()));
        }
        if (entry != null) {
            entry.layer().dispose();
        }
    }

    /**
     * Returns the calling module's texture under {@code name}, creating
     * and registering it on first use (G-001 拍板⑥). Idempotent: repeated
     * calls with the same owner and name hand back the same live handle,
     * so a name is registered exactly once. A named {@link CustomImage}
     * canvas of the same name adopts into this entry (its dimensions
     * win); an entry created here starts as a blank placeholder of the
     * given size, uploaded best-effort — {@link Texture#update(Image)} is
     * the awaited upload contract. Unloading the module disposes its
     * textures and releases their GPU slots.
     *
     * @param name texture name (a lowercase identifier path; the public
     *             registry key, not starting with {@code $})
     * @param spec texture options: {@code width}/{@code height} of the
     *             blank placeholder (both default {@code 1})
     * @return the live texture handle
     * @zh 返回调用方模块下 {@code name} 对应的纹理，首次调用时创建并注册（G-001 拍板⑥）。幂等：同 owner 同名重复调用返回同一存活句柄，一个名字只登记一次。同名具名 {@link CustomImage} 画布会并入本登记（以其尺寸为准）；此处创建的登记以给定尺寸的空白占位起步、尽力上传——{@link Texture#update(Image)} 才是权威等待点。模块卸载会失效其纹理并释放 GPU 槽位。
     * @zh-param name 纹理名（小写标识符路径；公开注册键，不得以 {@code $} 开头）
     * @zh-param spec 纹理选项：空白占位的 {@code width}/{@code height}（均默认 {@code 1}）
     * @zh-return 存活的纹理句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Texture texture(String name,
            @ApiOptional @ApiType("{ width?: number, height?: number }") Map<String, Object> spec) {
        Texture.requireValidName(name);
        OwnerScope owner = ownerProbe.get();
        String key = key(name, owner);
        synchronized (lock) {
            TextureEntry existing = textures.get(key);
            if (existing != null && !existing.texture().isDisposed()) {
                return existing.texture();
            }
            int width = spec == null ? 1 : intOf(spec.get("width"), 1);
            int height = spec == null ? 1 : intOf(spec.get("height"), 1);
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "texture dimensions must be positive: " + width + "x" + height);
            }
            Texture fresh = newTexture(key, owner, name, width, height);
            try {
                render.putTexture(fresh.getId(), width, height, new int[width * height])
                        .exceptionally(t -> null);
            } catch (RuntimeException ignored) {
                // Best-effort: Texture.update() is the awaited upload.
            }
            return fresh;
        }
    }

    /**
     * Every live texture of the calling module, in registration order;
     * entries adopted from named {@link CustomImage} canvases included.
     * Empty when the caller owns no textures.
     *
     * @zh 调用方模块的全部存活纹理，按登记次序排列；含从具名 {@link CustomImage} 画布并入的登记。调用方没有纹理时为空。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<Texture> getTextures() {
        OwnerScope owner = ownerProbe.get();
        String ownerName = owner == null ? null : owner.name();
        List<Texture> out = new ArrayList<>();
        synchronized (lock) {
            for (TextureEntry entry : textures.values()) {
                if (!Objects.equals(entry.ownerName(), ownerName)) {
                    continue;
                }
                if (!entry.texture().isDisposed()) {
                    out.add(entry.texture());
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * Adds a persistent text entry on the HUD overlay (frozen 0.1.0
     * compatibility face): upserts a text element on the calling module's
     * implicit {@code $legacy} layer.
     *
     * @param id    stable text identifier (remove via {@link #removeText})
     * @param text  the text content
     * @param x     screen x (pixels from left)
     * @param y     screen y (pixels from top)
     * @param color ARGB color (e.g. {@code 0xFFFFFFFF} for white); taken as
     *              {@code long} so full-range JS literals like
     *              {@code 0xFFFFFFFF} pass the engine's argument conversion
     * @return a promise resolving once the element is published
     * @zh 在 HUD 覆盖层上添加一条持久文本（冻结的 0.1.0 兼容面）：在调用方模块的隐式 {@code $legacy} 层上 upsert 文本元素。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> addText(String id, String text, int x, int y, long color) {
        requirePublicId(id);
        OwnerScope owner = ownerProbe.get();
        int argb = (int) color;
        return CompletableFuture.runAsync(() -> {
            HudLayer legacy = getOrCreate(LEGACY_LAYER, 0, owner);
            legacy.text(id, text, Map.<String, Object>of(
                    "x", x, "y", y, "color", argb));
        }, mainExecutor);
    }

    /**
     * Removes a previously added text entry (frozen 0.1.0 compatibility
     * face). Unknown ids resolve without effect.
     *
     * @param id text identifier passed to {@link #addText}
     * @return a promise resolving once the element is dropped
     * @zh 移除一条 HUD 文本（冻结的 0.1.0 兼容面）。未知 id 静默完成。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> removeText(String id) {
        requirePublicId(id);
        OwnerScope owner = ownerProbe.get();
        return CompletableFuture.runAsync(() -> {
            Entry entry;
            synchronized (lock) {
                entry = layers.get(key(LEGACY_LAYER, owner));
            }
            if (entry != null && !entry.layer().isDisposed()) {
                entry.layer().remove(id);
            }
        }, mainExecutor);
    }

    /** Creates (or returns) the caller's layer under {@code id}. */
    private HudLayer getOrCreate(String id, int z, OwnerScope owner) {
        String key = key(id, owner);
        synchronized (lock) {
            Entry existing = layers.get(key);
            if (existing != null && !existing.layer().isDisposed()) {
                return existing.layer();
            }
            HudLayer fresh = new HudLayer(id, key, z, render, mainExecutor, window);
            layers.put(key, new Entry(fresh, owner == null ? null : owner.name()));
            wireOwner(owner);
            return fresh;
        }
    }

    /** Registers a named {@link CustomImage} canvas into the texture registry. */
    private void registerCanvas(CustomImage image) {
        String name = image.getName();
        if (name == null || name.isEmpty()) {
            return;
        }
        OwnerScope owner = ownerProbe.get();
        String key = key(name, owner);
        synchronized (lock) {
            TextureEntry existing = textures.get(key);
            if (existing != null) {
                if (!existing.texture().isDisposed()) {
                    // Same GPU identifier: the canvas dimensions win.
                    existing.texture().adopt(image.getWidth(), image.getHeight());
                    return;
                }
                textures.remove(key);
            }
            newTexture(key, owner, name, image.getWidth(), image.getHeight());
        }
    }

    /** Puts a fresh texture entry under {@code key}; caller holds {@code lock}. */
    private Texture newTexture(String key, OwnerScope owner, String name,
            int width, int height) {
        Texture fresh = new Texture(name, width, height, render,
                () -> removeTextureEntry(key));
        textures.put(key, new TextureEntry(fresh, owner == null ? null : owner.name()));
        wireOwner(owner);
        return fresh;
    }

    /** Drops one texture entry (dispose path); unknown keys are ignored. */
    private void removeTextureEntry(String key) {
        synchronized (lock) {
            textures.remove(key);
        }
    }

    /** Wires the owner's unload hook once, releasing everything it owns. */
    private void wireOwner(OwnerScope owner) {
        if (owner != null && wiredOwners.add(owner)) {
            owner.onRelease(() -> {
                wiredOwners.remove(owner);
                releaseOwner(owner.name());
            });
        }
    }

    /** Disposes every layer and texture owned by {@code ownerName} (module unload). */
    private void releaseOwner(String ownerName) {
        List<HudLayer> victims = new ArrayList<>();
        List<Texture> textureVictims = new ArrayList<>();
        synchronized (lock) {
            var iterator = layers.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next().getValue();
                if (Objects.equals(entry.ownerName(), ownerName)) {
                    victims.add(entry.layer());
                    iterator.remove();
                }
            }
            var textureIterator = textures.entrySet().iterator();
            while (textureIterator.hasNext()) {
                TextureEntry entry = textureIterator.next().getValue();
                if (Objects.equals(entry.ownerName(), ownerName)) {
                    textureVictims.add(entry.texture());
                    textureIterator.remove();
                }
            }
        }
        for (HudLayer victim : victims) {
            victim.dispose();
        }
        for (Texture victim : textureVictims) {
            victim.dispose();
        }
    }

    /** The owner-qualified port key: {@code <owner>:<id>}, or bare id. */
    private static String key(String id, OwnerScope owner) {
        return owner != null ? owner.name() + ":" + id : id;
    }

    private static void requirePublicId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("layer id must not be empty");
        }
        if (id.startsWith("$")) {
            throw new IllegalArgumentException(
                    "layer ids must not start with '$' (reserved for internal layers)");
        }
    }

    private static int intOf(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("expected a number, got: " + value);
    }
}
