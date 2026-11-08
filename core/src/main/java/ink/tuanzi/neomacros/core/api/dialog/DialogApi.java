package ink.tuanzi.neomacros.core.api.dialog;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.DialogPort;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.dialog} module: the vanilla dialog system (server dialogs
 * on 1.21.6+, plus client-side dialogs scripts can open). Reads go through
 * neutral snapshots; actions and script dialogs require the dialog feature
 * on the running node.
 *
 * @zh {@code neo.dialog}：原版对话框系统（1.21.6+ 服务端对话框，及脚本可自行打开的客户端对话框）。读取经中立快照；动作与脚本弹窗要求当前节点具备对话框特性。
 */
@ApiModule(name = "dialog", category = "界面")
@ApiSince("0.3.0")
@ApiLoader("*")
@ApiMC(">=1.21.6")
public final class DialogApi {

    /**
     * The currently open dialog handle, or undefined when no dialog screen
     * is open.
     *
     * @zh 当前打开的对话框句柄；无对话框屏幕时为 undefined。
     */
    @ApiSince("0.3.0")
    @ApiLoader("*")
    @ApiMC(">=1.21.6")
    @ApiReadOnly
    public DialogHandle getOpened() {
        return ScriptApiEnvironment.adapter().dialog().currentDialog()
                .map(DialogApi::handleOf).orElse(null);
    }

    /**
     * Opens a client-side dialog from a plain config map and resolves with
     * the chosen action id once {@code clickAction} runs; Esc or a manual
     * button click resolves with {@code null} when the dialog closes.
     * Config keys: {@code type} ({@code notice}, {@code confirmation},
     * {@code multi_action}), {@code title}, {@code body} (string or string
     * array), {@code canCloseWithEscape}, {@code actions} (array of
     * {@code {label}}).
     *
     * @zh 从普通配置对象打开一个客户端对话框，脚本调用 {@code clickAction} 后以所选动作 id 落定；按 Esc 或手动点击按钮关闭时以 null 落定。配置键：{@code type}（{@code notice}/{@code confirmation}/{@code multi_action}）、{@code title}、{@code body}（字符串或字符串数组）、{@code canCloseWithEscape}、{@code actions}（{@code {label}} 数组）。
     */
    @ApiSince("0.3.0")
    @ApiLoader("*")
    @ApiMC(">=1.21.6")
    public CompletableFuture<String> show(Map<String, Object> config) {
        return ScriptApiEnvironment.adapter().dialog().show(config);
    }

    /** Builds the handle for one dialog snapshot (screen module bridge). */
    public static DialogHandle handleOf(DialogPort.DialogInfo info) {
        return new DialogHandle(info.kind(), info.title(), info.canCloseWithEscape(),
                List.copyOf(info.body()), List.copyOf(info.actions()),
                List.copyOf(info.inputs()));
    }

    private static DialogPort adapter() {
        return ScriptApiEnvironment.adapter().dialog();
    }

    /**
     * Live dialog handle: reads resolve on every access, actions dispatch
     * on the main thread.
     *
     * @zh 对话框句柄：读取实时解析，动作在主线程派发。
     */
    @ApiClass(name = "DialogHandle", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.server.dialog.Dialog")
    public static final class DialogHandle {

        private final String kind;
        private final String title;
        private final boolean canCloseWithEscape;
        private final List<String> body;
        private final List<DialogPort.DialogActionInfo> actions;
        private final List<DialogPort.DialogInputInfo> inputs;

        DialogHandle(String kind, String title, boolean canCloseWithEscape,
                     List<String> body, List<DialogPort.DialogActionInfo> actions,
                     List<DialogPort.DialogInputInfo> inputs) {
            this.kind = kind;
            this.title = title;
            this.canCloseWithEscape = canCloseWithEscape;
            this.body = body;
            this.actions = actions;
            this.inputs = inputs;
        }

        /** The dialog kind ({@code notice}, {@code confirmation}, {@code multi_action}, ...).
         * @zh 对话框类别（{@code notice}、{@code confirmation}、{@code multi_action} 等）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public String getKind() {
            return kind;
        }

        /** The dialog title.
         * @zh 对话框标题。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public String getTitle() {
            return title;
        }

        /** Whether the dialog can be closed with Esc.
         * @zh 是否可按 Esc 关闭。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public boolean getCanCloseWithEscape() {
            return canCloseWithEscape;
        }

        /** The dialog body texts, in order.
         * @zh 对话框正文文本（按序）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public List<String> getBody() {
            return body;
        }

        /** The action buttons: {@code {index, label, id}} in flattened order.
         * @zh 动作按钮：按展平顺序的 {@code {index, label, id}}。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public List<Map<String, Object>> getActions() {
            return actions.stream()
                    .map(action -> Map.<String, Object>of(
                            "index", action.index(),
                            "label", action.label(),
                            "id", action.id()))
                    .toList();
        }

        /** The declared input controls: {@code {key, type, value}}.
         * @zh 声明的输入控件：{@code {key, type, value}}。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public List<Map<String, Object>> getInputs() {
            return inputs.stream()
                    .map(input -> Map.<String, Object>of(
                            "key", input.key(),
                            "type", input.type(),
                            "value", input.value() == null ? "" : input.value()))
                    .toList();
        }

        /**
         * Clicks one action button by its flattened index (script-shown
         * dialogs resolve their show() future with the action id).
         *
         * @zh 按展平序号点击动作按钮（脚本弹窗会以该动作 id 落定 show() 的 future）。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        public CompletableFuture<Void> clickAction(int index) {
            return adapter().clickAction(index);
        }

        /** Closes the dialog (Esc path; script dialogs resolve show() with null).
         * @zh 关闭对话框（Esc 路径；脚本弹窗以 null 落定 show()）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        public CompletableFuture<Void> close() {
            return adapter().close();
        }
    }
}
