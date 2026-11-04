package ink.tuanzi.neomacros.core.api.client;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * The {@code neo.client} facade: process-level facts about the running game
 * client plus the controlled escape-layer entry to the raw client instance.
 *
 * @zh {@code neo.client} 门面：运行中游戏客户端的进程级事实，以及到原始客户端实例的受控穿透口。
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

    /** The client port behind the facade (single adapter access point). */
    private static ink.tuanzi.neomacros.port.ClientPort adapter() {
        return ScriptApiEnvironment.adapter().client();
    }
}
