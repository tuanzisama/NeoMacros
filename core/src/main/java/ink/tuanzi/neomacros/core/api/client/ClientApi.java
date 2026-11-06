package ink.tuanzi.neomacros.core.api.client;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ModData;
import ink.tuanzi.neomacros.port.model.OptionsData;
import ink.tuanzi.neomacros.port.model.ServerInfoData;
import ink.tuanzi.neomacros.port.model.WindowData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.client} facade: process-level facts about the running game
 * client, window and input surfaces, the connection lifecycle, and read-only
 * registry and option slices.
 *
 * @zh {@code neo.client} 门面：运行中游戏客户端的进程级事实、窗口与输入面、连接生命周期，以及只读的注册表与设置切片。
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
    public List<Map<String, Object>> getMods() {
        return adapter().mods().stream().map(ClientApi::modView).toList();
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
    public Map<String, Object> getMod(String id) {
        return adapter().mods().stream()
                .filter(mod -> mod.id().equals(id))
                .map(ClientApi::modView).findFirst().orElse(null);
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
    public Map<String, Object> getWindow() {
        return adapter().window().map(ClientApi::windowView).orElse(null);
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
    public Map<String, Object> getMouse() {
        var pos = adapter().mouse();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("x", pos.x());
        view.put("y", pos.y());
        return view;
    }

    /**
     * The connected server entry as {@code {name, address}}, or {@code null}
     * in singleplayer.
     *
     * @zh 当前服务器条目 {@code {name, address}}；单人世界为 null。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public Map<String, Object> getServerInfo() {
        return adapter().serverInfo().map(ClientApi::serverInfoView).orElse(null);
    }

    /**
     * A small read-only slice of the game options as
     * {@code {fov, gamma, guiScale, renderDistance}}.
     *
     * @zh 游戏设置只读切片 {@code {fov, gamma, guiScale, renderDistance}}。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public Map<String, Object> getOptions() {
        return adapter().options().map(ClientApi::optionsView).orElse(null);
    }

    /**
     * The identifiers of one registry kind ({@code block}, {@code item},
     * {@code entity_type}, {@code sound_event}, {@code particle_type},
     * {@code mob_effect}); unknown kinds throw.
     *
     * @zh 返回一类注册表的全部标识（{@code block}/{@code item}/{@code entity_type}/{@code sound_event}/{@code particle_type}/{@code mob_effect}）；未知类别抛错。
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

    /** Renders one mod as the script-facing shape. */
    private static Map<String, Object> modView(ModData mod) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", mod.id());
        view.put("name", mod.name());
        view.put("version", mod.version());
        view.put("authors", mod.authors());
        return view;
    }

    /** Renders the window metrics as the script-facing shape. */
    private static Map<String, Object> windowView(WindowData data) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("width", data.width());
        view.put("height", data.height());
        view.put("scaledWidth", data.scaledWidth());
        view.put("scaledHeight", data.scaledHeight());
        return view;
    }

    /** Renders the server entry as the script-facing shape. */
    private static Map<String, Object> serverInfoView(ServerInfoData data) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", data.name());
        view.put("address", data.address());
        return view;
    }

    /** Renders the options slice as the script-facing shape. */
    private static Map<String, Object> optionsView(OptionsData data) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("fov", data.fov());
        view.put("gamma", data.gamma());
        view.put("guiScale", data.guiScale());
        view.put("renderDistance", data.renderDistance());
        return view;
    }

    /** The client port behind the facade (single adapter access point). */
    private static ink.tuanzi.neomacros.port.ClientPort adapter() {
        return ScriptApiEnvironment.adapter().client();
    }
}
