package ink.tuanzi.neomacros.core.api.screen;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.dialog.DialogApi;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * The {@code neo.screen} module: the currently open screen as a typed
 * {@code Screen} handle family ({@code ChatScreen}, {@code InventoryScreen},
 * {@code DialogScreen}, ...; every screen without a dedicated class falls
 * back to the base {@code Screen}). The script-built GUI surface stays a
 * later-phase design.
 *
 * @zh {@code neo.screen}：当前打开屏幕的类型化句柄族（{@code ChatScreen}、{@code InventoryScreen}、{@code DialogScreen} 等；无专属类的屏幕回落基类 {@code Screen}）。脚本自建 GUI 属后续阶段设计。
 */
@ApiModule(name = "screen", category = "界面")
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class ScreenApi {

    /**
     * The currently open screen as a {@code Screen} handle, or undefined
     * when no screen is open. The handle resolves through the screen kind
     * (chat, title, container screens, dialogs, ...); the container itself
     * is reachable through {@code player.inventory} or the handle's own
     * {@code container} property.
     *
     * @zh 当前打开的屏幕句柄；无屏幕时为 undefined。句柄按屏幕类别解析（聊天、菜单、容器屏、对话框等）；容器本体可经 {@code player.inventory} 或句柄自身的 {@code container} 属性获取。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public Screen getOpened() {
        var info = ScriptApiEnvironment.adapter().lifecycle().screenInfo().orElse(null);
        return info == null ? null : openByKind(info.kind(), info.screenClass(), info.title());
    }

    /** Resolves the handle class for one screen kind. */
    private static Screen openByKind(String kind, String mcClass, String title) {
        return switch (kind) {
            case "inventory" -> new InventoryScreen(mcClass, title);
            case "creative" -> new CreativeScreen(mcClass, title);
            case "container", "chest", "furnace", "crafting", "anvil", "enchanting",
                    "grindstone", "stonecutter", "smithing", "cartography", "loom",
                    "lectern", "dispenser", "hopper", "crafter", "beacon", "brewing",
                    "villager", "horse", "mount", "shulkerbox" ->
                    new ContainerScreen(mcClass, title);
            case "chat" -> new ChatScreen(mcClass, title);
            case "title" -> new TitleScreen(mcClass, title);
            case "pause" -> new PauseScreen(mcClass, title);
            case "death" -> new DeathScreen(mcClass, title);
            case "wingame" -> new WinScreen(mcClass, title);
            case "progress", "connecting" -> new ProgressScreen(mcClass, title);
            case "disconnected" -> new DisconnectedScreen(mcClass, title);
            case "confirm" -> new ConfirmScreen(mcClass, title);
            case "dialog" -> new DialogScreen(mcClass, title);
            case "sign" -> new SignScreen(mcClass, title);
            case "bookedit" -> new BookEditScreen(mcClass, title);
            case "bookview" -> new BookViewScreen(mcClass, title);
            case "commandblock" -> new CommandBlockScreen(mcClass, title);
            case "structureblock" -> new StructureBlockScreen(mcClass, title);
            case "jigsaw" -> new JigsawScreen(mcClass, title);
            default -> new Screen(mcClass, kind, title);
        };
    }

    /**
     * Base handle for any open screen: the MC class name, the kind label,
     * the title, and Esc-style close.
     *
     * @zh 任意打开屏幕的基础句柄：MC 类名、类别标签、标题与 Esc 式关闭。
     */
    @ApiClass(name = "Screen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.Screen")
    public static class Screen {

        final String mcClass;
        final String kind;
        final String title;

        Screen(String mcClass, String kind, String title) {
            this.mcClass = mcClass;
            this.kind = kind;
            this.title = title;
        }

        /** The Minecraft screen class name (e.g. {@code TitleScreen}).
         * @zh Minecraft 屏幕类名（如 {@code TitleScreen}）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getName() {
            return mcClass;
        }

        /** The screen kind label (e.g. {@code title}, {@code chest}).
         * @zh 屏幕类别标签（如 {@code title}、{@code chest}）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getKind() {
            return kind;
        }

        /** The screen title.
         * @zh 屏幕标题。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getTitle() {
            return title;
        }

        /** Closes the screen (the same path as pressing Esc).
         * @zh 关闭屏幕（与按 Esc 同路）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public void close() {
            ScriptApiEnvironment.adapter().lifecycle().closeScreen();
        }
    }

    /** The player inventory screen (the E-key backpack).
     * @zh 玩家背包界面（E 键打开的物品栏）。 */
    @ApiClass(name = "InventoryScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.InventoryScreen")
    public static final class InventoryScreen extends Screen {

        InventoryScreen(String mcClass, String title) {
            super(mcClass, "inventory", title);
        }

        /** The player inventory handle shown by this screen.
         * @zh 此界面展示的玩家背包句柄。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public PlayerApi.Inventory getContainer() {
            return PlayerApi.containerHandle("Inventory", "player", 0);
        }
    }

    /** Any container screen (chest, furnace, villager, mount, ...).
     * @zh 任意容器界面（箱子、熔炉、村民、坐骑等）。 */
    @ApiClass(name = "ContainerScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen")
    public static final class ContainerScreen extends Screen {

        ContainerScreen(String mcClass, String title) {
            super(mcClass, "container", title);
        }

        /** The open container handle, or undefined outside a world.
         * @zh 打开的容器句柄；不在世界内为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public PlayerApi.Inventory getContainer() {
            return ScriptApiEnvironment.adapter().inventory().openedContainer()
                    .map(info -> PlayerApi.containerHandle(info.title(), info.kind(), info.size()))
                    .orElse(null);
        }
    }

    /** The chat screen with the text currently being typed.
     * @zh 聊天界面，可读当前正在输入的文本。 */
    @ApiClass(name = "ChatScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.ChatScreen")
    public static final class ChatScreen extends Screen {

        ChatScreen(String mcClass, String title) {
            super(mcClass, "chat", title);
        }

        /** The text currently typed into the chat input, or undefined.
         * @zh 聊天输入框中的文本；无输入时为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getText() {
            return ScriptApiEnvironment.adapter().lifecycle().chatText().orElse(null);
        }
    }

    /** The sign editor with the front-face text.
     * @zh 告示牌编辑界面，可读正面文本。 */
    @ApiClass(name = "SignScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen")
    public static final class SignScreen extends Screen {

        SignScreen(String mcClass, String title) {
            super(mcClass, "sign", title);
        }

        /** The front-face sign text, line-joined.
         * @zh 告示牌正面文本（按行拼接）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getText() {
            return ScriptApiEnvironment.adapter().lifecycle().signText().orElse(null);
        }
    }

    /** The book editor with the page text under edit.
     * @zh 成书编辑界面，可读正在编辑的页文本。 */
    @ApiClass(name = "BookEditScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.BookEditScreen")
    public static final class BookEditScreen extends Screen {

        BookEditScreen(String mcClass, String title) {
            super(mcClass, "bookedit", title);
        }

        /** The text of the page under edit, or undefined.
         * @zh 正在编辑的页文本；无则 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getText() {
            return ScriptApiEnvironment.adapter().lifecycle().bookEditPage().orElse(null);
        }
    }

    /** The dialog screen (server dialogs and script-shown dialogs).
     * @zh 对话框界面（服务端对话框与脚本弹窗）。 */
    @ApiClass(name = "DialogScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.dialog.DialogScreen")
    public static final class DialogScreen extends Screen {

        DialogScreen(String mcClass, String title) {
            super(mcClass, "dialog", title);
        }

        /** The open dialog handle, or undefined when none.
         * @zh 打开的对话框句柄；无则 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public DialogApi.DialogHandle getDialog() {
            return ScriptApiEnvironment.adapter().dialog().currentDialog()
                    .map(DialogApi::handleOf).orElse(null);
        }
    }

    /** The title screen.
     * @zh 主菜单界面。 */
    @ApiClass(name = "TitleScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.TitleScreen")
    public static final class TitleScreen extends Screen {

        TitleScreen(String mcClass, String title) {
            super(mcClass, "title", title);
        }
    }

    /** The pause screen.
     * @zh 暂停菜单界面。 */
    @ApiClass(name = "PauseScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.PauseScreen")
    public static final class PauseScreen extends Screen {

        PauseScreen(String mcClass, String title) {
            super(mcClass, "pause", title);
        }
    }

    /** The death screen.
     * @zh 死亡界面。 */
    @ApiClass(name = "DeathScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.DeathScreen")
    public static final class DeathScreen extends Screen {

        DeathScreen(String mcClass, String title) {
            super(mcClass, "death", title);
        }
    }

    /** The creative inventory screen.
     * @zh 创造模式物品栏界面。 */
    @ApiClass(name = "CreativeScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen")
    public static final class CreativeScreen extends Screen {

        CreativeScreen(String mcClass, String title) {
            super(mcClass, "creative", title);
        }
    }

    /** A loading or progress screen.
     * @zh 加载或进度界面。 */
    @ApiClass(name = "ProgressScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.ProgressScreen")
    public static final class ProgressScreen extends Screen {

        ProgressScreen(String mcClass, String title) {
            super(mcClass, "progress", title);
        }
    }

    /** The disconnect notice screen.
     * @zh 断开连接提示界面。 */
    @ApiClass(name = "DisconnectedScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.DisconnectedScreen")
    public static final class DisconnectedScreen extends Screen {

        DisconnectedScreen(String mcClass, String title) {
            super(mcClass, "disconnected", title);
        }
    }

    /** The end-poem (credits) screen.
     * @zh 终局诗（制作名单）界面。 */
    @ApiClass(name = "WinScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.WinScreen")
    public static final class WinScreen extends Screen {

        WinScreen(String mcClass, String title) {
            super(mcClass, "wingame", title);
        }
    }

    /** A yes/no confirmation screen.
     * @zh 是/否确认界面。 */
    @ApiClass(name = "ConfirmScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.ConfirmScreen")
    public static final class ConfirmScreen extends Screen {

        ConfirmScreen(String mcClass, String title) {
            super(mcClass, "confirm", title);
        }
    }

    /** The book view screen.
     * @zh 成书阅读界面。 */
    @ApiClass(name = "BookViewScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.BookViewScreen")
    public static final class BookViewScreen extends Screen {

        BookViewScreen(String mcClass, String title) {
            super(mcClass, "bookview", title);
        }
    }

    /** A command block editor screen.
     * @zh 命令方块编辑界面。 */
    @ApiClass(name = "CommandBlockScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen")
    public static final class CommandBlockScreen extends Screen {

        CommandBlockScreen(String mcClass, String title) {
            super(mcClass, "commandblock", title);
        }
    }

    /** The structure block editor screen.
     * @zh 结构方块编辑界面。 */
    @ApiClass(name = "StructureBlockScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen")
    public static final class StructureBlockScreen extends Screen {

        StructureBlockScreen(String mcClass, String title) {
            super(mcClass, "structureblock", title);
        }
    }

    /** The jigsaw block editor screen.
     * @zh 拼图方块编辑界面。 */
    @ApiClass(name = "JigsawScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen")
    public static final class JigsawScreen extends Screen {

        JigsawScreen(String mcClass, String title) {
            super(mcClass, "jigsaw", title);
        }
    }
}
