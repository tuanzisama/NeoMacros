package ink.tuanzi.neomacros.core.api.client;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.KeybindCatalogData;
import ink.tuanzi.neomacros.port.model.ModData;
import ink.tuanzi.neomacros.port.model.OptionsData;
import ink.tuanzi.neomacros.port.model.ServerInfoData;
import ink.tuanzi.neomacros.port.model.WindowData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.client} module: process-level facts about the running game
 * client, window and input surfaces, the connection lifecycle, the registry
 * queries, and the game-options handle with its write actions.
 *
 * @zh {@code neo.client}：运行中游戏客户端的进程级事实、窗口与输入面、连接生命周期、注册表查询，以及带写操作的游戏设置句柄。
 */
@ApiModule(name = "client", category = "系统")
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class ClientApi {

    /**
     * The current frames-per-second measurement of the client.
     *
     * @zh 客户端当前帧率。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public int getFps() {
        return adapter().fps();
    }

    /**
     * The game version string the client runs on.
     *
     * @zh 客户端运行的游戏版本串。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public String getGameVersion() {
        return adapter().gameVersion();
    }

    /**
     * The raw host client instance as a {@code JavaObject} proxy. Requires
     * the {@code java} permission; equivalent to
     * {@code java.type('net.minecraft.client.Minecraft').getInstance()}.
     *
     * @zh 原始宿主客户端实例（{@code JavaObject} 代理）。需 {@code java} 权限；等价于 {@code java.type('net.minecraft.client.Minecraft').getInstance()}。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiPermission("java")
    @ApiType("JavaObject")
    public Object getMinecraft() {
        return adapter().minecraftInstance();
    }

    /**
     * Connects to the server at the address, leaving any current world.
     * Resolves once the connection is initiated; failures reject.
     *
     * @zh 连接到目标地址的服务器（先离开当前世界）；发起连接后即 resolve，失败 reject。
     * @zh-param address 服务器地址（host 或 host:port）
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CompletableFuture<Void> connect(String address) {
        return adapter().connect(address);
    }

    /**
     * Disconnects from the current server. Resolves once initiated; rejects
     * in singleplayer.
     *
     * @zh 断开与服务器的连接；发起后即 resolve，单人世界下 reject。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CompletableFuture<Void> disconnect() {
        return adapter().disconnect();
    }

    /**
     * Loads the local save with the given name. Resolves once the load flow
     * starts; unknown saves reject.
     *
     * @zh 加载指定名称的本地存档；加载流程开始后即 resolve，存档不存在 reject。
     * @zh-param name 存档名称
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CompletableFuture<Void> loadWorld(String name) {
        return adapter().loadWorld(name);
    }

    /**
     * Closes the game gracefully.
     *
     * @zh 优雅地关闭游戏。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void shutdown() {
        adapter().shutdown();
    }

    /**
     * Closes the game immediately, skipping save and shutdown hooks.
     *
     * @zh 立即关闭游戏，跳过保存与关闭钩子。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void forceShutdown() {
        adapter().forceShutdown();
    }

    /**
     * The server-reported latency for the local player in milliseconds, or
     * {@code null} without a connection.
     *
     * @zh 服务器报告的本地玩家延迟（毫秒）；未连接时为 null。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public Integer getPing() {
        return adapter().ping().orElse(null);
    }

    /**
     * The installed mods as {@code [{id, name, version, authors}]} in loader
     * order.
     *
     * @zh 已安装模组数组 {@code [{id, name, version, authors}]}，按加载器顺序。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public List<ModData> getMods() {
        return adapter().mods();
    }

    /**
     * Whether a mod with the identifier is loaded.
     *
     * @zh 判断指定 id 的模组是否已加载。
     * @zh-param id 模组标识
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean isModLoaded(String id) {
        return adapter().isModLoaded(id);
    }

    /**
     * The metadata of one loaded mod as {@code {id, name, version, authors}},
     * or {@code null} when not loaded.
     *
     * @zh 指定 id 模组的元数据 {@code {id, name, version, authors}}；未加载时为 null。
     * @zh-param id 模组标识
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ModData getMod(String id) {
        return adapter().mods().stream()
                .filter(mod -> mod.id().equals(id))
                .findFirst().orElse(null);
    }

    /**
     * Whether the game runs in a development environment.
     *
     * @zh 游戏是否运行在开发环境。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean isDevEnv() {
        return adapter().devEnvironment();
    }

    /** Grabs the mouse cursor back into the game window.
     *
     * @zh 把鼠标收回游戏窗口（重新捕获）。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void grabMouse() {
        adapter().grabMouse();
    }

    /**
     * Releases the mouse cursor to the operating system.
     *
     * @zh 把鼠标释放给操作系统。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void releaseMouse() {
        adapter().releaseMouse();
    }

    /**
     * The current system clipboard text.
     *
     * @zh 当前系统剪贴板文本。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public String getClipboard() {
        return adapter().clipboard();
    }

    /**
     * Writes text to the system clipboard.
     *
     * @zh 写入系统剪贴板。
     * @zh-param value 剪贴板文本
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void setClipboard(String value) {
        adapter().setClipboard(value);
    }

    /**
     * The window metrics as {@code {width, height, scaledWidth, scaledHeight}}.
     *
     * @zh 窗口度量 {@code {width, height, scaledWidth, scaledHeight}}。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public WindowData getWindow() {
        return adapter().window().orElse(null);
    }

    /**
     * The cursor position in window pixels as {@code {x, y}}.
     *
     * @zh 鼠标位置（窗口像素）{@code {x, y}}。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public MousePos getMouse() {
        var pos = adapter().mouse();
        return new MousePos(pos.x(), pos.y());
    }

    /**
     * The connected server entry as {@code {name, address, ping}}, or
     * {@code null} in singleplayer.
     *
     * @zh 当前服务器条目 {@code {name, address, ping}}；单人世界为 null。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public ServerInfoData getServerInfo() {
        return adapter().serverInfo().orElse(null);
    }

    /**
     * The live game-options root handle: video, skin, music, control, chat
     * and accessibility groups plus the resource-pack, language, difficulty,
     * camera and window controls. Every read re-queries the client port;
     * writes resolve once the stored value has been read back.
     *
     * @zh 活的游戏设置根句柄：视频/皮肤/音乐/控制/聊天/无障碍分组，以及资源包、语言、难度、镜头与窗口控制。每次读取都重新查询端口；写入在回读确认存储值后落定。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Options getOptions() {
        return new Options();
    }

    /**
     * The live game-options root handle. The read face keeps the historical
     * {@code fov}, {@code gamma}, {@code guiScale} and {@code renderDistance}
     * slice at the root; the full surface lives on {@link Options} and
     * {@link Options#getVideo()}.
     *
     * @zh 活的游戏设置根句柄；读面在根上保留历史的 {@code fov}/{@code gamma}/{@code guiScale}/{@code renderDistance} 切片，完整面在 {@link Options} 与 {@link Options#getVideo()}。
     */
    @ApiClass(name = "Options", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.Options")
    public static final class Options implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final VideoOptions video = new VideoOptions(this);
        private final SkinOptions skin = new SkinOptions(this);
        private final MusicOptions music = new MusicOptions(this);
        private final ControlOptions control = new ControlOptions(this);
        private final ChatOptions chat = new ChatOptions(this);
        private final AccessibilityOptions accessibility = new AccessibilityOptions(this);

        /** The graphics and video settings group.
         * @zh 图形与视频设置分组。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public VideoOptions getVideo() {
            return video;
        }

        /** The skin model-part and main-hand settings group.
         * @zh 皮肤部件与主手设置分组。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public SkinOptions getSkin() {
            return skin;
        }

        /** The sound volume and audio device settings group.
         * @zh 音量与音频设备设置分组。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public MusicOptions getMusic() {
            return music;
        }

        /** The input and key-binding settings group.
         * @zh 输入与键位设置分组。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ControlOptions getControl() {
            return control;
        }

        /** The chat display and behavior settings group.
         * @zh 聊天显示与行为设置分组。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ChatOptions getChat() {
            return chat;
        }

        /** The accessibility settings group.
         * @zh 无障碍设置分组。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public AccessibilityOptions getAccessibility() {
            return accessibility;
        }

        /** Persists the current settings to disk.
         * @zh 把当前设置写入磁盘。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> saveOptions() {
            return adapter().saveOptions();
        }

        /** The available resource-pack identifiers.
         * @zh 可用资源包标识列表。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<String> getResourcePacks() {
            OptionsData data = optionsData();
            return data == null ? List.of() : data.resourcePacks();
        }

        /** The enabled resource-pack identifiers, including required packs.
         * @zh 已启用的资源包标识（含必选包）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<String> getEnabledResourcePacks() {
            OptionsData data = optionsData();
            return data == null ? List.of() : data.enabledResourcePacks();
        }

        /**
         * Selects the resource packs to enable and reloads resources when the
         * selection changes. The promise rejects when the stored selection
         * does not match the request (unknown identifiers or required packs
         * the list omits).
         *
         * @zh 选择要启用的资源包并在选择变化时重载资源；存储选择与请求不一致（未知标识或遗漏必选包）时 reject。
         * @zh-param enabled 要启用的资源包标识列表
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setEnabledResourcePacks(List<String> enabled) {
            if (enabled == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("resource pack list required"));
            }
            return adapter().setEnabledResourcePacks(enabled)
                    .thenCompose(ignored -> verifyValue("enabledResourcePacks", enabled));
        }

        /**
         * Rejects or allows server-provided resource packs and reloads
         * resources when the policy changes.
         *
         * @zh 禁用或允许服务器提供的资源包；策略变化时重载资源。
         * @zh-param state true 为拒绝服务器资源包，false 为允许
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> removeServerResourcePack(boolean state) {
            return adapter().setServerResourcePacksDisabled(state)
                    .thenCompose(ignored -> verifyValue("serverPacksDisabled", state));
        }

        /** The selected language code.
         * @zh 当前选择的语言代码。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getLanguage() {
            OptionsData data = optionsData();
            return data == null ? null : data.language();
        }

        /**
         * Selects the language when its code is known and reloads resources;
         * unknown codes reject.
         *
         * @zh 在语言代码已知时切换语言并重载资源；未知代码 reject。
         * @zh-param languageCode 语言代码（如 {@code zh_cn}）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setLanguage(String languageCode) {
            if (languageCode == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("language code required"));
            }
            return adapter().setLanguage(languageCode)
                    .thenCompose(ignored -> verifyValue("language", languageCode));
        }

        /** Whether the world difficulty is locked; undefined without a world.
         * @zh 世界难度是否被锁定；不在世界中为 undefined。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getDifficultyLocked() {
            OptionsData data = optionsData();
            return data == null ? null : data.difficultyLocked();
        }

        /**
         * Asks the server to lock the world difficulty. The promise settles
         * once the request is sent; the server decides whether it applies.
         *
         * @zh 请求服务器锁定世界难度；请求发出后即落定，是否生效由服务器决定。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> lockDifficulty() {
            return adapter().setDifficultyLocked(true);
        }

        /**
         * Asks the server to unlock the world difficulty. The promise settles
         * once the request is sent; the server decides whether it applies.
         *
         * @zh 请求服务器解除世界难度锁定；请求发出后即落定，是否生效由服务器决定。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> unlockDifficulty() {
            return adapter().setDifficultyLocked(false);
        }

        /** The field of view in degrees; undefined when options are unavailable.
         * @zh 视野角度（度）；设置不可用时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getFov() {
            OptionsData data = optionsData();
            return data == null ? null : data.fov();
        }

        /** The brightness (gamma) level; an alias of {@code video.gamma}.
         * @zh 亮度（gamma）值；{@code video.gamma} 的别名。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getGamma() {
            return video.getGamma();
        }

        /** The configured GUI scale factor; an alias of {@code video.guiScale}.
         * @zh 配置的界面缩放倍率；{@code video.guiScale} 的别名。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getGuiScale() {
            return video.getGuiScale();
        }

        /** The render distance in chunks; an alias of {@code video.renderDistance}.
         * @zh 渲染距离（区块）；{@code video.renderDistance} 的别名。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getRenderDistance() {
            return video.getRenderDistance();
        }

        /**
         * Sets the field of view; rejects when the client stores a different
         * value (out-of-range input).
         *
         * @zh 设置视野角度；客户端存储值与请求不一致（越界输入）时 reject。
         * @zh-param fov 视野角度（度）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setFov(int fov) {
            return writeOption("fov", fov);
        }

        /** The camera mode: 0 first person, 1 third person behind, 2 third person front.
         * @zh 镜头模式：0 第一人称、1 第三人称背后、2 第三人称正面。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getCameraMode() {
            OptionsData data = optionsData();
            return data == null ? null : data.cameraMode();
        }

        /**
         * Sets the camera mode; rejects when the mode is out of range or the
         * stored value differs.
         *
         * @zh 设置镜头模式；模式越界或存储值不一致时 reject。
         * @zh-param mode 0 第一人称、1 第三人称背后、2 第三人称正面
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setCameraMode(int mode) {
            return adapter().setCameraMode(mode)
                    .thenCompose(ignored -> verifyValue("cameraMode", mode));
        }

        /** Whether the smooth-camera toggle is enabled.
         * @zh 平滑镜头开关是否开启。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getSmoothCamera() {
            OptionsData data = optionsData();
            return data == null ? null : data.smoothCamera();
        }

        /**
         * Enables or disables the smooth-camera toggle.
         *
         * @zh 开启或关闭平滑镜头。
         * @zh-param enabled true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setSmoothCamera(boolean enabled) {
            return adapter().setSmoothCamera(enabled)
                    .thenCompose(ignored -> verifyValue("smoothCamera", enabled));
        }

        /** The window width in screen pixels.
         * @zh 窗口宽度（屏幕像素）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getWidth() {
            OptionsData data = optionsData();
            return data == null ? null : data.width();
        }

        /** The window height in screen pixels.
         * @zh 窗口高度（屏幕像素）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getHeight() {
            OptionsData data = optionsData();
            return data == null ? null : data.height();
        }

        /**
         * Resizes the window width, keeping the current height; rejects while
         * fullscreen.
         *
         * @zh 调整窗口宽度（高度不变）；全屏状态下 reject。
         * @zh-param w 宽度（像素）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setWidth(int w) {
            return resizeTo(w, null, "width");
        }

        /**
         * Resizes the window height, keeping the current width; rejects while
         * fullscreen.
         *
         * @zh 调整窗口高度（宽度不变）；全屏状态下 reject。
         * @zh-param h 高度（像素）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setHeight(int h) {
            return resizeTo(null, h, "height");
        }

        /**
         * Resizes the window; rejects while fullscreen or for non-positive
         * sizes.
         *
         * @zh 调整窗口尺寸；全屏状态或非正尺寸时 reject。
         * @zh-param w 宽度（像素）
         * @zh-param h 高度（像素）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setSize(int w, int h) {
            return adapter().setWindowSize(w, h)
                    .thenCompose(ignored -> verifyValue("width", w))
                    .thenCompose(ignored -> verifyValue("height", h));
        }

        /**
         * Broadcasts the current synchronized options to the server; call it
         * after writes that other players should observe.
         *
         * @zh 把当前同步设置广播给服务器；写入需要让他人观察到的选项后调用。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public void sendSyncedOptions() {
            adapter().sendSyncedOptions();
        }

        /** Resizes one window axis while keeping the other, then verifies it. */
        private static CompletableFuture<Void> resizeTo(Integer width, Integer height,
                                                        String component) {
            OptionsData data = optionsData();
            if (data == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("game options are unavailable"));
            }
            int targetWidth = width != null ? width : data.width();
            int targetHeight = height != null ? height : data.height();
            return adapter().setWindowSize(targetWidth, targetHeight)
                    .thenCompose(ignored -> verifyValue(component,
                            component.equals("width") ? targetWidth : targetHeight));
        }
    }

    /**
     * Video and graphics settings view over the option face. Reads re-query
     * the client on every access; writes resolve after read-back.
     *
     * @zh 视频与图形设置视图；每次读取都重新查询客户端，写入在回读后落定。
     */
    @ApiClass(name = "VideoOptions", kind = ApiClassKind.HANDLE)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class VideoOptions implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final Options parent;

        VideoOptions(Options parent) {
            this.parent = parent;
        }

        /** The options root that owns this group.
         * @zh 拥有该分组的设置根句柄。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Options getParent() {
            return parent;
        }

        /** The configured fullscreen video mode string; undefined when none is set.
         * @zh 已配置的全屏分辨率串；未配置时为 undefined。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getFullscreenResolution() {
            OptionsData data = optionsData();
            return data == null ? null : data.fullscreenResolution();
        }

        /** The biome blend radius in blocks.
         * @zh 生物群系混合半径（方块）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getBiomeBlendRadius() {
            OptionsData data = optionsData();
            return data == null ? null : data.biomeBlendRadius();
        }

        /**
         * Sets the biome blend radius; rejects when the client stores a
         * different value.
         *
         * @zh 设置生物群系混合半径；客户端存储值不一致时 reject。
         * @zh-param radius 半径（方块）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setBiomeBlendRadius(int radius) {
            return writeOption("biomeBlendRadius", radius);
        }

        /** The graphics preset: {@code fast}, {@code fancy} or {@code fabulous} (and {@code custom} on newer versions).
         * @zh 图形预设：{@code fast}/{@code fancy}/{@code fabulous}（新版本另有 {@code custom}）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getGraphicsMode() {
            OptionsData data = optionsData();
            return data == null ? null : data.graphicsMode();
        }

        /**
         * Sets the graphics preset by name, case-insensitively; rejects on
         * unknown names or when the stored value differs.
         *
         * @zh 按名称（忽略大小写）设置图形预设；名称未知或存储值不一致时 reject。
         * @zh-param mode fast / fancy / fabulous（新版本另有 custom）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setGraphicsMode(String mode) {
            return writeOption("graphicsMode", mode);
        }

        /** The chunk builder mode: {@code none}, {@code nearby} or {@code player_affected}.
         * @zh 区块构建模式：{@code none}/{@code nearby}/{@code player_affected}。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getPrioritizeChunkUpdates() {
            OptionsData data = optionsData();
            return data == null ? null : data.prioritizeChunkUpdates();
        }

        /**
         * Sets the chunk builder mode, case-insensitively; rejects on unknown
         * names or when the stored value differs.
         *
         * @zh 按名称（忽略大小写）设置区块构建模式；名称未知或存储值不一致时 reject。
         * @zh-param mode none / nearby / player_affected
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setPrioritizeChunkUpdates(String mode) {
            return writeOption("prioritizeChunkUpdates", mode);
        }

        /** Whether ambient occlusion (smooth lighting) is enabled.
         * @zh 环境光遮蔽（平滑光照）是否开启。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getAmbientOcclusion() {
            OptionsData data = optionsData();
            return data == null ? null : data.ambientOcclusion();
        }

        /**
         * Enables or disables ambient occlusion.
         *
         * @zh 开启或关闭环境光遮蔽。
         * @zh-param mode true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setAmbientOcclusion(boolean mode) {
            return writeOption("ambientOcclusion", mode);
        }

        /** The render distance in chunks.
         * @zh 渲染距离（区块）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getRenderDistance() {
            OptionsData data = optionsData();
            return data == null ? null : data.renderDistance();
        }

        /**
         * Sets the render distance; rejects when the client stores a
         * different value (out-of-range input).
         *
         * @zh 设置渲染距离；客户端存储值不一致（越界输入）时 reject。
         * @zh-param radius 距离（区块）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setRenderDistance(int radius) {
            return writeOption("renderDistance", radius);
        }

        /** The simulation distance in chunks.
         * @zh 模拟距离（区块）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getSimulationDistance() {
            OptionsData data = optionsData();
            return data == null ? null : data.simulationDistance();
        }

        /**
         * Sets the simulation distance; rejects when the client stores a
         * different value.
         *
         * @zh 设置模拟距离；客户端存储值不一致时 reject。
         * @zh-param radius 距离（区块）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setSimulationDistance(int radius) {
            return writeOption("simulationDistance", radius);
        }

        /** The upper frame-rate limit.
         * @zh 帧率上限。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getFramerateLimit() {
            OptionsData data = optionsData();
            return data == null ? null : data.framerateLimit();
        }

        /**
         * Sets the upper frame-rate limit; rejects when the client stores a
         * different value.
         *
         * @zh 设置帧率上限；客户端存储值不一致时 reject。
         * @zh-param maxFps 帧率上限
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setFramerateLimit(int maxFps) {
            return writeOption("framerateLimit", maxFps);
        }

        /** Whether vertical sync is enabled.
         * @zh 垂直同步是否开启。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getEnableVsync() {
            OptionsData data = optionsData();
            return data == null ? null : data.enableVsync();
        }

        /**
         * Enables or disables vertical sync.
         *
         * @zh 开启或关闭垂直同步。
         * @zh-param val true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setEnableVsync(boolean val) {
            return writeOption("enableVsync", val);
        }

        /** Whether view bobbing is enabled.
         * @zh 视角晃动是否开启。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getBobView() {
            OptionsData data = optionsData();
            return data == null ? null : data.bobView();
        }

        /**
         * Enables or disables view bobbing.
         *
         * @zh 开启或关闭视角晃动。
         * @zh-param val true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setBobView(boolean val) {
            return writeOption("bobView", val);
        }

        /** The configured GUI scale factor; the game may pick another effective scale.
         * @zh 配置的界面缩放倍率；游戏可能采用不同的实际倍率。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getGuiScale() {
            OptionsData data = optionsData();
            return data == null ? null : data.guiScale();
        }

        /**
         * Sets the GUI scale factor and resizes the interface; rejects when
         * the client stores a different value.
         *
         * @zh 设置界面缩放倍率并重排界面；客户端存储值不一致时 reject。
         * @zh-param scale 缩放倍率
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setGuiScale(int scale) {
            return writeOption("guiScale", scale);
        }

        /** The attack indicator mode: {@code off}, {@code crosshair} or {@code hotbar}.
         * @zh 攻击指示器模式：{@code off}/{@code crosshair}/{@code hotbar}。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getAttackIndicator() {
            OptionsData data = optionsData();
            return data == null ? null : data.attackIndicator();
        }

        /**
         * Sets the attack indicator mode, case-insensitively; rejects on
         * unknown names or when the stored value differs.
         *
         * @zh 按名称（忽略大小写）设置攻击指示器；名称未知或存储值不一致时 reject。
         * @zh-param type off / crosshair / hotbar
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setAttackIndicator(String type) {
            return writeOption("attackIndicator", type);
        }

        /** The brightness (gamma) level.
         * @zh 亮度（gamma）值。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getGamma() {
            OptionsData data = optionsData();
            return data == null ? null : data.gamma();
        }

        /**
         * Sets the brightness (gamma) level; rejects when the client stores a
         * different value.
         *
         * @zh 设置亮度（gamma）值；客户端存储值不一致时 reject。
         * @zh-param gamma 亮度值
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setGamma(double gamma) {
            return writeOption("gamma", gamma);
        }

        /** The brightness level; an alias of {@link #getGamma()}.
         * @zh 亮度值；{@link #getGamma()} 的别名。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getBrightness() {
            return getGamma();
        }

        /**
         * Sets the brightness level; an alias of {@link #setGamma(double)}.
         *
         * @zh 设置亮度值；{@link #setGamma(double)} 的别名。
         * @zh-param gamma 亮度值
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setBrightness(double gamma) {
            return setGamma(gamma);
        }

        /** The cloud rendering mode: {@code off}, {@code fast} or {@code fancy}.
         * @zh 云层渲染模式：{@code off}/{@code fast}/{@code fancy}。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getCloudStatus() {
            OptionsData data = optionsData();
            return data == null ? null : data.cloudStatus();
        }

        /**
         * Sets the cloud rendering mode, case-insensitively; rejects on
         * unknown names or when the stored value differs.
         *
         * @zh 按名称（忽略大小写）设置云层模式；名称未知或存储值不一致时 reject。
         * @zh-param mode off / fast / fancy
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setCloudStatus(String mode) {
            return writeOption("cloudStatus", mode);
        }

        /** Whether the game runs in fullscreen.
         * @zh 游戏是否以全屏运行。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getFullscreen() {
            OptionsData data = optionsData();
            return data == null ? null : data.fullscreen();
        }

        /**
         * Enters or leaves fullscreen mode.
         *
         * @zh 切换全屏模式。
         * @zh-param fullscreen true 为进入全屏
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setFullscreen(boolean fullscreen) {
            return writeOption("fullscreen", fullscreen);
        }

        /** The particle rendering mode: {@code minimal}, {@code decreased} or {@code all}.
         * @zh 粒子渲染模式：{@code minimal}/{@code decreased}/{@code all}。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getParticles() {
            OptionsData data = optionsData();
            return data == null ? null : data.particles();
        }

        /**
         * Sets the particle rendering mode, case-insensitively; rejects on
         * unknown names or when the stored value differs.
         *
         * @zh 按名称（忽略大小写）设置粒子模式；名称未知或存储值不一致时 reject。
         * @zh-param mode minimal / decreased / all
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setParticles(String mode) {
            return writeOption("particles", mode);
        }

        /** The mip-map level count.
         * @zh Mipmap 层级数。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getMipmapLevels() {
            OptionsData data = optionsData();
            return data == null ? null : data.mipmapLevels();
        }

        /**
         * Sets the mip-map level count; rejects when the client stores a
         * different value.
         *
         * @zh 设置 Mipmap 层级数；客户端存储值不一致时 reject。
         * @zh-param val 层级数
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setMipmapLevels(int val) {
            return writeOption("mipmapLevels", val);
        }

        /** Whether entity shadows are enabled.
         * @zh 实体阴影是否开启。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getEntityShadows() {
            OptionsData data = optionsData();
            return data == null ? null : data.entityShadows();
        }

        /**
         * Enables or disables entity shadows.
         *
         * @zh 开启或关闭实体阴影。
         * @zh-param val true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setEntityShadows(boolean val) {
            return writeOption("entityShadows", val);
        }

        /** The screen distortion effect scale.
         * @zh 屏幕扭曲效果强度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getScreenEffectScale() {
            OptionsData data = optionsData();
            return data == null ? null : data.screenEffectScale();
        }

        /**
         * Sets the screen distortion effect scale; rejects when the client
         * stores a different value.
         *
         * @zh 设置屏幕扭曲效果强度；客户端存储值不一致时 reject。
         * @zh-param val 强度值
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setScreenEffectScale(double val) {
            return writeOption("screenEffectScale", val);
        }

        /** The entity distance scaling factor.
         * @zh 实体距离缩放系数。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getEntityDistanceScaling() {
            OptionsData data = optionsData();
            return data == null ? null : data.entityDistanceScaling();
        }

        /**
         * Sets the entity distance scaling factor; rejects when the client
         * stores a different value.
         *
         * @zh 设置实体距离缩放系数；客户端存储值不一致时 reject。
         * @zh-param val 缩放系数
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setEntityDistanceScaling(double val) {
            return writeOption("entityDistanceScaling", val);
        }

        /** The field-of-view effect scale.
         * @zh 视野晃动效果强度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getFovEffectScale() {
            OptionsData data = optionsData();
            return data == null ? null : data.fovEffectScale();
        }

        /**
         * Sets the field-of-view effect scale; rejects when the client stores
         * a different value.
         *
         * @zh 设置视野晃动效果强度；客户端存储值不一致时 reject。
         * @zh-param val 强度值
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setFovEffectScale(double val) {
            return writeOption("fovEffectScale", val);
        }

        /** Whether the autosave indicator is shown.
         * @zh 是否显示自动保存提示。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getShowAutosaveIndicator() {
            OptionsData data = optionsData();
            return data == null ? null : data.showAutosaveIndicator();
        }

        /**
         * Shows or hides the autosave indicator.
         *
         * @zh 显示或隐藏自动保存提示。
         * @zh-param val true 为显示
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setShowAutosaveIndicator(boolean val) {
            return writeOption("showAutosaveIndicator", val);
        }
    }

    /**
     * Skin model-part visibility and main-hand settings group.
     *
     * @zh 皮肤部件可见性与主手设置分组。
     */
    @ApiClass(name = "SkinOptions", kind = ApiClassKind.HANDLE)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class SkinOptions implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final Options parent;

        SkinOptions(Options parent) {
            this.parent = parent;
        }

        /** The options root that owns this group.
         * @zh 拥有该分组的设置根句柄。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Options getParent() {
            return parent;
        }
    }

    /**
     * Sound volume and audio device settings group.
     *
     * @zh 音量与音频设备设置分组。
     */
    @ApiClass(name = "MusicOptions", kind = ApiClassKind.HANDLE)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class MusicOptions implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final Options parent;

        MusicOptions(Options parent) {
            this.parent = parent;
        }

        /** The options root that owns this group.
         * @zh 拥有该分组的设置根句柄。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Options getParent() {
            return parent;
        }
    }

    /**
     * Input and key-binding settings group.
     *
     * @zh 输入与键位设置分组。
     */
    @ApiClass(name = "ControlOptions", kind = ApiClassKind.HANDLE)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class ControlOptions implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final Options parent;

        ControlOptions(Options parent) {
            this.parent = parent;
        }

        /** The options root that owns this group.
         * @zh 拥有该分组的设置根句柄。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Options getParent() {
            return parent;
        }

        /** The mouse sensitivity.
         * @zh 鼠标灵敏度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getSensitivity() {
            OptionsData data = optionsData();
            return data == null ? null : data.sensitivity();
        }

        /**
         * Sets the mouse sensitivity; rejects when the stored value differs
         * (out-of-range input).
         *
         * @zh 设置鼠标灵敏度；存储值与请求不一致（越界输入）时 reject。
         * @zh-param value 灵敏度
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setSensitivity(double value) {
            return writeOption("sensitivity", value);
        }

        /** Whether the vertical mouse axis is inverted.
         * @zh 鼠标纵轴是否反转。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getInvertMouseY() {
            OptionsData data = optionsData();
            return data == null ? null : data.invertMouseY();
        }

        /**
         * Enables or disables vertical mouse inversion.
         *
         * @zh 开启或关闭鼠标纵轴反转。
         * @zh-param value true 为反转
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setInvertMouseY(boolean value) {
            return writeOption("invertMouseY", value);
        }

        /** The mouse wheel sensitivity.
         * @zh 鼠标滚轮灵敏度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getMouseWheelSensitivity() {
            OptionsData data = optionsData();
            return data == null ? null : data.mouseWheelSensitivity();
        }

        /**
         * Sets the mouse wheel sensitivity; rejects when the stored value
         * differs.
         *
         * @zh 设置鼠标滚轮灵敏度；存储值与请求不一致时 reject。
         * @zh-param value 滚轮灵敏度
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setMouseWheelSensitivity(double value) {
            return writeOption("mouseWheelSensitivity", value);
        }

        /** Whether the mouse wheel scrolls in discrete steps.
         * @zh 鼠标滚轮是否按离散步进滚动。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getDiscreteMouseScroll() {
            OptionsData data = optionsData();
            return data == null ? null : data.discreteMouseScroll();
        }

        /**
         * Enables or disables discrete mouse scrolling.
         *
         * @zh 开启或关闭滚轮离散步进。
         * @zh-param value true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setDiscreteMouseScroll(boolean value) {
            return writeOption("discreteMouseScroll", value);
        }

        /**
         * Whether touchscreen mode is on; always {@code false} from Minecraft
         * 26.2, where the setting was removed.
         *
         * @zh 触屏模式是否开启；Minecraft 26.2 起该设置已移除，恒为 false。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getTouchscreen() {
            OptionsData data = optionsData();
            return data == null ? null : data.touchscreen();
        }

        /**
         * Enables or disables touchscreen mode. The setting was removed in
         * Minecraft 26.2 with no replacement: the write has no effect there
         * and resolves only when the requested value already matches the
         * stored one.
         *
         * @zh 开启或关闭触屏模式。该设置在 Minecraft 26.2 被移除且无替代：在那些版本上写入无效，仅当请求值与存储值一致时 resolve。
         * @zh-param value true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setTouchscreen(boolean value) {
            return writeOption("touchscreen", value);
        }

        /**
         * Whether raw mouse input is on; always {@code true} from Minecraft
         * 26.3, where the setting was removed in favour of the SDL relative
         * mouse mode.
         *
         * @zh 原始鼠标输入是否开启；Minecraft 26.3 起该设置已移除（SDL 相对鼠标模式默认即原始输入），恒为 true。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getRawMouseInput() {
            OptionsData data = optionsData();
            return data == null ? null : data.rawMouseInput();
        }

        /**
         * Enables or disables raw mouse input. The setting was removed in
         * Minecraft 26.3: the write has no effect there and resolves only
         * when the requested value already matches the stored one.
         *
         * @zh 开启或关闭原始鼠标输入。该设置在 Minecraft 26.3 被移除：在那个版本上写入无效，仅当请求值与存储值一致时 resolve。
         * @zh-param value true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setRawMouseInput(boolean value) {
            return writeOption("rawMouseInput", value);
        }

        /** Whether auto-jump is enabled.
         * @zh 是否开启自动跳跃。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getAutoJump() {
            OptionsData data = optionsData();
            return data == null ? null : data.autoJump();
        }

        /**
         * Enables or disables auto-jump.
         *
         * @zh 开启或关闭自动跳跃。
         * @zh-param value true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setAutoJump(boolean value) {
            return writeOption("autoJump", value);
        }

        /** Whether sneak uses toggle behaviour.
         * @zh 潜行是否为切换式。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getToggleCrouch() {
            OptionsData data = optionsData();
            return data == null ? null : data.toggleCrouch();
        }

        /**
         * Switches sneak between hold and toggle behaviour.
         *
         * @zh 切换潜行的按住/切换行为。
         * @zh-param value true 为切换式
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setToggleCrouch(boolean value) {
            return writeOption("toggleCrouch", value);
        }

        /** Whether sprint uses toggle behaviour.
         * @zh 疾跑是否为切换式。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getToggleSprint() {
            OptionsData data = optionsData();
            return data == null ? null : data.toggleSprint();
        }

        /**
         * Switches sprint between hold and toggle behaviour.
         *
         * @zh 切换疾跑的按住/切换行为。
         * @zh-param value true 为切换式
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setToggleSprint(boolean value) {
            return writeOption("toggleSprint", value);
        }

        /** The key-mapping names in registration order.
         * @zh 键位绑定名列表，按注册顺序。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<String> getKeys() {
            KeybindCatalogData catalog = keybindCatalog();
            return catalog == null ? List.of() : catalog.names();
        }

        /** The distinct key-mapping category keys in first-seen order.
         * @zh 键位分类键列表（去重，按首次出现顺序）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<String> getCategories() {
            KeybindCatalogData catalog = keybindCatalog();
            return catalog == null ? List.of() : catalog.categories();
        }

        /** The key binds grouped by category: category key to {@code {bind name, key label}}.
         * @zh 按分类分组的键位表：分类键 → {@code {绑定名, 按键标签}}。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public java.util.Map<String, java.util.Map<String, String>> getKeyBindsByCategory() {
            KeybindCatalogData catalog = keybindCatalog();
            return catalog == null ? java.util.Map.of() : catalog.bindsByCategory();
        }

        /**
         * The key binds of one category as {@code {bind name, key label}},
         * or {@code undefined} when the category is unknown.
         *
         * @zh 指定分类的键位表 {@code {绑定名, 按键标签}}；分类未知时为 undefined。
         * @zh-param category 分类键（见 {@code categories}）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public java.util.Map<String, String> getKeyBindsByCategory(String category) {
            if (category == null) {
                return null;
            }
            return getKeyBindsByCategory().get(category);
        }
    }

    /**
     * Chat display and behavior settings group.
     *
     * @zh 聊天显示与行为设置分组。
     */
    @ApiClass(name = "ChatOptions", kind = ApiClassKind.HANDLE)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class ChatOptions implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final Options parent;

        ChatOptions(Options parent) {
            this.parent = parent;
        }

        /** The options root that owns this group.
         * @zh 拥有该分组的设置根句柄。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Options getParent() {
            return parent;
        }

        /** The chat visibility: {@code full}, {@code system} or {@code hidden}.
         * @zh 聊天可见性：{@code full}/{@code system}/{@code hidden}。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getChatVisibility() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatVisibility();
        }

        /**
         * Sets the chat visibility; unknown modes reject.
         *
         * @zh 设置聊天可见性；未知模式 reject。
         * @zh-param mode {@code full}、{@code system} 或 {@code hidden}（大小写不敏感）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatVisibility(String mode) {
            if (mode == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("chat visibility required"));
            }
            return writeOption("chatVisibility", mode);
        }

        /** Whether chat colors are shown.
         * @zh 是否显示聊天颜色。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getChatColors() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatColors();
        }

        /**
         * Shows or hides chat colors.
         *
         * @zh 显示或隐藏聊天颜色。
         * @zh-param value true 为显示
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatColors(boolean value) {
            return writeOption("chatColors", value);
        }

        /** Whether web links in chat can be opened.
         * @zh 聊天中的网页链接是否可点击打开。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getChatLinks() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatLinks();
        }

        /**
         * Enables or disables opening web links from chat.
         *
         * @zh 开启或关闭聊天网页链接点击。
         * @zh-param value true 为允许打开
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatLinks(boolean value) {
            return writeOption("chatLinks", value);
        }

        /** Whether opening a chat web link asks for confirmation first.
         * @zh 打开聊天链接前是否先弹出确认。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getChatLinksPrompt() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatLinksPrompt();
        }

        /**
         * Enables or disables the confirmation prompt before a chat link opens.
         *
         * @zh 开启或关闭聊天链接的打开确认。
         * @zh-param value true 为需要确认
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatLinksPrompt(boolean value) {
            return writeOption("chatLinksPrompt", value);
        }

        /** The chat background opacity.
         * @zh 聊天背景不透明度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatOpacity() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatOpacity();
        }

        /**
         * Sets the chat background opacity; rejects when the stored value
         * differs (out-of-range input).
         *
         * @zh 设置聊天背景不透明度；存储值与请求不一致（越界输入）时 reject。
         * @zh-param value 不透明度（0 到 1）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatOpacity(double value) {
            return writeOption("chatOpacity", value);
        }

        /** The text background opacity.
         * @zh 文字背景不透明度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getTextBackgroundOpacity() {
            OptionsData data = optionsData();
            return data == null ? null : data.textBackgroundOpacity();
        }

        /**
         * Sets the text background opacity; rejects when the stored value
         * differs.
         *
         * @zh 设置文字背景不透明度；存储值与请求不一致时 reject。
         * @zh-param value 不透明度（0 到 1）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setTextBackgroundOpacity(double value) {
            return writeOption("textBackgroundOpacity", value);
        }

        /** The chat text scale.
         * @zh 聊天文字缩放。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatScale() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatScale();
        }

        /**
         * Sets the chat text scale; rejects when the stored value differs.
         *
         * @zh 设置聊天文字缩放；存储值与请求不一致时 reject。
         * @zh-param value 文字缩放
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatScale(double value) {
            return writeOption("chatScale", value);
        }

        /** The spacing between chat lines.
         * @zh 聊天行距。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatLineSpacing() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatLineSpacing();
        }

        /**
         * Sets the spacing between chat lines; rejects when the stored value
         * differs.
         *
         * @zh 设置聊天行距；存储值与请求不一致时 reject。
         * @zh-param value 行距
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatLineSpacing(double value) {
            return writeOption("chatLineSpacing", value);
        }

        /** The delay before a chat message appears, in seconds.
         * @zh 聊天消息显示延迟（秒）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatDelay() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatDelay();
        }

        /**
         * Sets the chat message delay in seconds; rejects when the stored
         * value differs.
         *
         * @zh 设置聊天消息延迟（秒）；存储值与请求不一致时 reject。
         * @zh-param value 延迟秒数
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatDelay(double value) {
            return writeOption("chatDelay", value);
        }

        /** The chat width scale.
         * @zh 聊天宽度缩放。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatWidth() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatWidth();
        }

        /**
         * Sets the chat width scale; rejects when the stored value differs.
         *
         * @zh 设置聊天宽度缩放；存储值与请求不一致时 reject。
         * @zh-param value 宽度缩放
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatWidth(double value) {
            return writeOption("chatWidth", value);
        }

        /** The chat height scale while chat is focused.
         * @zh 聊天获得焦点时的高度缩放。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatHeightFocused() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatHeightFocused();
        }

        /**
         * Sets the chat height scale while chat is focused; rejects when the
         * stored value differs.
         *
         * @zh 设置聊天获得焦点时的高度缩放；存储值与请求不一致时 reject。
         * @zh-param value 高度缩放
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatHeightFocused(double value) {
            return writeOption("chatHeightFocused", value);
        }

        /** The chat height scale while chat is unfocused.
         * @zh 聊天失去焦点时的高度缩放。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatHeightUnfocused() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatHeightUnfocused();
        }

        /**
         * Sets the chat height scale while chat is unfocused; rejects when
         * the stored value differs.
         *
         * @zh 设置聊天失去焦点时的高度缩放；存储值与请求不一致时 reject。
         * @zh-param value 高度缩放
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatHeightUnfocused(double value) {
            return writeOption("chatHeightUnfocused", value);
        }

        /** The narrator mode: {@code off}, {@code chat}, {@code system} or {@code all}.
         * @zh 朗读模式：{@code off}/{@code chat}/{@code system}/{@code all}。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getNarrator() {
            OptionsData data = optionsData();
            return data == null ? null : data.narrator();
        }

        /**
         * Sets the narrator mode; unknown modes reject.
         *
         * @zh 设置朗读模式；未知模式 reject。
         * @zh-param mode {@code off}、{@code chat}、{@code system} 或 {@code all}（大小写不敏感）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setNarrator(String mode) {
            if (mode == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("narrator mode required"));
            }
            return writeOption("narrator", mode);
        }

        /** Whether command suggestions appear while typing in chat.
         * @zh 聊天输入时是否显示命令建议。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getAutoSuggestions() {
            OptionsData data = optionsData();
            return data == null ? null : data.autoSuggestions();
        }

        /**
         * Enables or disables command suggestions while typing.
         *
         * @zh 开启或关闭聊天命令建议。
         * @zh-param value true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setAutoSuggestions(boolean value) {
            return writeOption("autoSuggestions", value);
        }

        /** Whether messages from blocked players are hidden.
         * @zh 是否隐藏被屏蔽玩家的消息。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getHideMatchedNames() {
            OptionsData data = optionsData();
            return data == null ? null : data.hideMatchedNames();
        }

        /**
         * Enables or hides messages from blocked players.
         *
         * @zh 开启或关闭屏蔽玩家消息的隐藏。
         * @zh-param value true 为隐藏
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setHideMatchedNames(boolean value) {
            return writeOption("hideMatchedNames", value);
        }

        /** Whether the debug overlay is reduced.
         * @zh 是否精简调试信息。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getReducedDebugInfo() {
            OptionsData data = optionsData();
            return data == null ? null : data.reducedDebugInfo();
        }

        /**
         * Enables or disables reduced debug information.
         *
         * @zh 开启或关闭精简调试信息。
         * @zh-param value true 为精简
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setReducedDebugInfo(boolean value) {
            return writeOption("reducedDebugInfo", value);
        }
    }

    /**
     * Accessibility settings group.
     *
     * @zh 无障碍设置分组。
     */
    @ApiClass(name = "AccessibilityOptions", kind = ApiClassKind.HANDLE)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class AccessibilityOptions
            implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final Options parent;

        AccessibilityOptions(Options parent) {
            this.parent = parent;
        }

        /** The options root that owns this group.
         * @zh 拥有该分组的设置根句柄。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Options getParent() {
            return parent;
        }

        /** The narrator mode: {@code off}, {@code chat}, {@code system} or {@code all}.
         * @zh 朗读模式：{@code off}/{@code chat}/{@code system}/{@code all}。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getNarrator() {
            OptionsData data = optionsData();
            return data == null ? null : data.narrator();
        }

        /**
         * Sets the narrator mode; unknown modes reject.
         *
         * @zh 设置朗读模式；未知模式 reject。
         * @zh-param mode {@code off}、{@code chat}、{@code system} 或 {@code all}（大小写不敏感）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setNarrator(String mode) {
            if (mode == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("narrator mode required"));
            }
            return writeOption("narrator", mode);
        }

        /** Whether subtitle captions are shown.
         * @zh 是否显示字幕。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getShowSubtitles() {
            OptionsData data = optionsData();
            return data == null ? null : data.showSubtitles();
        }

        /**
         * Shows or hides subtitle captions.
         *
         * @zh 显示或隐藏字幕。
         * @zh-param value true 为显示
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setShowSubtitles(boolean value) {
            return writeOption("showSubtitles", value);
        }

        /** The text background opacity.
         * @zh 文字背景不透明度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getTextBackgroundOpacity() {
            OptionsData data = optionsData();
            return data == null ? null : data.textBackgroundOpacity();
        }

        /**
         * Sets the text background opacity; rejects when the stored value
         * differs.
         *
         * @zh 设置文字背景不透明度；存储值与请求不一致时 reject。
         * @zh-param value 不透明度（0 到 1）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setTextBackgroundOpacity(double value) {
            return writeOption("textBackgroundOpacity", value);
        }

        /** Whether text backgrounds render behind chat only.
         * @zh 文字背景是否只出现在聊天后方。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getBackgroundForChatOnly() {
            OptionsData data = optionsData();
            return data == null ? null : data.backgroundForChatOnly();
        }

        /**
         * Restricts text backgrounds to chat or lets them render everywhere.
         *
         * @zh 把文字背景限制在聊天，或允许其出现在所有文字后方。
         * @zh-param value true 为仅聊天
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setBackgroundForChatOnly(boolean value) {
            return writeOption("backgroundForChatOnly", value);
        }

        /** The chat background opacity.
         * @zh 聊天背景不透明度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatOpacity() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatOpacity();
        }

        /**
         * Sets the chat background opacity; rejects when the stored value
         * differs.
         *
         * @zh 设置聊天背景不透明度；存储值与请求不一致时 reject。
         * @zh-param value 不透明度（0 到 1）
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatOpacity(double value) {
            return writeOption("chatOpacity", value);
        }

        /** The spacing between chat lines.
         * @zh 聊天行距。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatLineSpacing() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatLineSpacing();
        }

        /**
         * Sets the spacing between chat lines; rejects when the stored value
         * differs.
         *
         * @zh 设置聊天行距；存储值与请求不一致时 reject。
         * @zh-param value 行距
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatLineSpacing(double value) {
            return writeOption("chatLineSpacing", value);
        }

        /** The delay before a chat message appears, in seconds.
         * @zh 聊天消息显示延迟（秒）。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getChatDelay() {
            OptionsData data = optionsData();
            return data == null ? null : data.chatDelay();
        }

        /**
         * Sets the chat message delay in seconds; rejects when the stored
         * value differs.
         *
         * @zh 设置聊天消息延迟（秒）；存储值与请求不一致时 reject。
         * @zh-param value 延迟秒数
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setChatDelay(double value) {
            return writeOption("chatDelay", value);
        }

        /** Whether auto-jump is enabled.
         * @zh 是否开启自动跳跃。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getAutoJump() {
            OptionsData data = optionsData();
            return data == null ? null : data.autoJump();
        }

        /**
         * Enables or disables auto-jump.
         *
         * @zh 开启或关闭自动跳跃。
         * @zh-param value true 为开启
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setAutoJump(boolean value) {
            return writeOption("autoJump", value);
        }

        /** Whether sneak uses toggle behaviour.
         * @zh 潜行是否为切换式。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getToggleCrouch() {
            OptionsData data = optionsData();
            return data == null ? null : data.toggleCrouch();
        }

        /**
         * Switches sneak between hold and toggle behaviour.
         *
         * @zh 切换潜行的按住/切换行为。
         * @zh-param value true 为切换式
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setToggleCrouch(boolean value) {
            return writeOption("toggleCrouch", value);
        }

        /** Whether sprint uses toggle behaviour.
         * @zh 疾跑是否为切换式。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getToggleSprint() {
            OptionsData data = optionsData();
            return data == null ? null : data.toggleSprint();
        }

        /**
         * Switches sprint between hold and toggle behaviour.
         *
         * @zh 切换疾跑的按住/切换行为。
         * @zh-param value true 为切换式
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setToggleSprint(boolean value) {
            return writeOption("toggleSprint", value);
        }

        /** The screen distortion effect scale.
         * @zh 屏幕扭曲效果强度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getDistortionEffect() {
            OptionsData data = optionsData();
            return data == null ? null : data.screenEffectScale();
        }

        /**
         * Sets the screen distortion effect scale; rejects when the stored
         * value differs.
         *
         * @zh 设置屏幕扭曲效果强度；存储值与请求不一致时 reject。
         * @zh-param value 效果强度
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setDistortionEffect(double value) {
            return writeOption("screenEffectScale", value);
        }

        /** The field-of-view effect scale.
         * @zh 视野效果强度。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getFovEffect() {
            OptionsData data = optionsData();
            return data == null ? null : data.fovEffectScale();
        }

        /**
         * Sets the field-of-view effect scale; rejects when the stored value
         * differs.
         *
         * @zh 设置视野效果强度；存储值与请求不一致时 reject。
         * @zh-param value 效果强度
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setFovEffect(double value) {
            return writeOption("fovEffectScale", value);
        }

        /** Whether the dark Mojang Studios background is used.
         * @zh 是否使用深色 Mojang Studios 背景。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getDarkMojangStudiosBackground() {
            OptionsData data = optionsData();
            return data == null ? null : data.darkMojangStudiosBackground();
        }

        /**
         * Enables or disables the dark Mojang Studios background.
         *
         * @zh 开启或关闭深色 Mojang Studios 背景。
         * @zh-param value true 为深色
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setDarkMojangStudiosBackground(boolean value) {
            return writeOption("darkMojangStudiosBackground", value);
        }

        /** Whether lightning flashes are hidden.
         * @zh 是否隐藏闪电闪光。 */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Boolean getHideLightningFlash() {
            OptionsData data = optionsData();
            return data == null ? null : data.hideLightningFlash();
        }
    }

    /**
     * One game option value by name ({@code fov}, {@code sensitivity},
     * {@code particles}, ...); the value is a Boolean, a number, or a
     * lowercase enum name. Unknown names return {@code null}.
     *
     * @zh 按名称读取单个游戏设置（{@code fov}/{@code sensitivity}/{@code particles} 等）；值为布尔、数字或小写枚举名；未知名称为 null。
     * @zh-param name 设置名
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiType("boolean | number | string")
    public Object option(String name) {
        return adapter().optionValue(name).orElse(null);
    }

    /**
     * The identifiers of one built-in registry kind, e.g. {@code block},
     * {@code item}, {@code enchantment}, {@code worldgen/biome}; any
     * registry id resolves, unknown kinds throw.
     *
     * @zh 返回一类内置注册表的全部标识，如 {@code block}/{@code item}/{@code enchantment}/{@code worldgen/biome}；任意注册表 id 均可解析；未知类别抛错。
     * @zh-kind 注册表类别
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public List<String> registryIds(String kind) {
        return adapter().registryIds(kind);
    }

    /**
     * Returns the full identifier when the registry kind contains the id
     * (missing namespace defaults to {@code minecraft}), or {@code null}.
     *
     * @zh 判断注册表中是否存在该 id（省略命名空间默认 {@code minecraft:}），存在返回全名，否则 null。
     * @zh-param kind 注册表类别
     * @zh-param id 标识
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String registryLookup(String kind, String id) {
        return adapter().registryLookup(kind, id).orElse(null);
    }

    /**
     * Captures the game framebuffer as an {@code Image}; the promise resolves
     * after the next frame read.
     *
     * @zh 抓取游戏画面为 {@code Image}；Promise 在下一帧读取后落定。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public java.util.concurrent.CompletableFuture<ink.tuanzi.neomacros.core.api.types.Image> grabScreenshot() {
        return adapter().screenshot().thenApply(data ->
                new ink.tuanzi.neomacros.core.api.types.Image(
                        data.width(), data.height(), data.bytes()));
    }

    /**
     * The cursor position in window pixels.
     *
     * @zh 鼠标位置（窗口像素）。
     */
    @ApiClass(name = "MousePos", kind = ApiClassKind.DATA)
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record MousePos(double x, double y) {
    }

    /** The client port behind the API (single adapter access point). */
    private static ink.tuanzi.neomacros.port.ClientPort adapter() {
        return ScriptApiEnvironment.adapter().client();
    }

    /** The current option face, or null when it is unavailable. */
    private static OptionsData optionsData() {
        var data = adapter().options();
        return data == null ? null : data.orElse(null);
    }

    /** The current key-mapping catalog, or null when it is unavailable. */
    private static KeybindCatalogData keybindCatalog() {
        var data = adapter().keybindCatalog();
        return data == null ? null : data.orElse(null);
    }

    /**
     * Writes one option-backed value and settles once the stored value reads
     * back as requested; a stored value that differs rejects.
     */
    private static CompletableFuture<Void> writeOption(String name, Object value) {
        return adapter().setOption(name, value)
                .thenCompose(ignored -> verifyValue(name, value));
    }

    /** Settles one write once the stored component matches the request. */
    private static CompletableFuture<Void> verifyValue(String component, Object expected) {
        OptionsData data = optionsData();
        if (data == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("game options are unavailable"));
        }
        Object stored = data.option(component);
        if (sameValue(expected, stored)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.failedFuture(new IllegalStateException(
                "option '" + component + "' was not applied: requested "
                        + expected + ", stored " + stored));
    }

    /** Compares a requested value with the value stored after a write. */
    private static boolean sameValue(Object expected, Object stored) {
        if (expected == null || stored == null) {
            return false;
        }
        if (expected instanceof Number wanted && stored instanceof Number actual) {
            return Double.compare(wanted.doubleValue(), actual.doubleValue()) == 0;
        }
        if (expected instanceof String wanted && stored instanceof String actual) {
            return wanted.equalsIgnoreCase(actual);
        }
        if (expected instanceof java.util.List<?> wanted
                && stored instanceof java.util.List<?> actual) {
            return new java.util.HashSet<>(wanted).equals(new java.util.HashSet<>(actual));
        }
        return expected.equals(stored);
    }
}
