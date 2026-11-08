package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.DialogPort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.dialog.DialogScreens;
import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.DialogListDialog;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.ServerLinksDialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Dialog reads and actions against the vanilla client dialog system. Reads
 * snapshot the open dialog screen; the close detection for script-shown
 * dialogs runs inside {@link #currentDialog()}, which the event tracker
 * polls once per tick, so a manually closed script dialog resolves its
 * show() future on the next tick.
 */
public final class FabricDialogPort implements DialogPort {

    //? if >=26.1 {
    /** Connection access for script-shown dialogs: script buttons carry no network action, so every hook stays inert. */
    private static final DialogConnectionAccess NOOP_CONNECTION = new DialogConnectionAccess() {
        @Override
        public void disconnect(Component reason) {
        }

        @Override
        public void runCommand(String command, Screen screen) {
        }

        @Override
        public void openDialog(net.minecraft.core.Holder<Dialog> dialog, Screen screen) {
        }

        @Override
        public void sendCustomAction(net.minecraft.resources.Identifier id, Optional<net.minecraft.nbt.Tag> payload) {
        }

        @Override
        public ServerLinks serverLinks() {
            return ServerLinks.EMPTY;
        }
    };
    //?} else {
    /*
    private static final DialogConnectionAccess NOOP_CONNECTION = new DialogConnectionAccess() {
        @Override
        public void disconnect(Component reason) {
        }

        @Override
        public void runCommand(String command, Screen screen) {
        }

        @Override
        public void openDialog(net.minecraft.core.Holder<Dialog> dialog, Screen screen) {
        }

        @Override
        public void sendCustomAction(net.minecraft.resources.Identifier id, Optional<net.minecraft.nbt.Tag> payload) {
        }

        @Override
        public ServerLinks serverLinks() {
            return ServerLinks.EMPTY;
        }
    };
    */
    //?}

    /** The screen a pending show() call opened (main thread only). */
    private Screen scriptScreen;
    private CompletableFuture<String> scriptFuture;
    private List<String> scriptActionIds = List.of();

    @Override
    public Optional<DialogInfo> currentDialog() {
        return MainThread.run(() -> {
            resolveScriptFutureIfClosed();
            Screen screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof DialogScreen<?> dialogScreen)) {
                return Optional.empty();
            }
            Dialog dialog = ((ink.tuanzi.neomacros.adapter.mixin.DialogScreenAccessor)dialogScreen).neomacros$dialog();
            return Optional.of(snapshot(dialog, screen == scriptScreen));
        });
    }

    @Override
    public CompletableFuture<Void> clickAction(int index) {
        return CompletableFuture.completedFuture(MainThread.run(() -> {
            clickActionNow(index);
            return null;
        }));
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(MainThread.run(() -> {
            Screen screen = FabricLifecyclePort.currentScreen();
            if (screen instanceof DialogScreen<?>) {
                resolveScriptFuture(null);
                Minecraft.getInstance().gui.setScreen(null);
            }
            return null;
        }));
    }

    @Override
    public CompletableFuture<String> show(Map<String, Object> config) {
        CompletableFuture<String> future = new CompletableFuture<>();
        MainThread.run(() -> {
            List<String> ids = new ArrayList<>();
            Dialog dialog = buildDialog(config, ids);
            DialogScreen<Dialog> screen = DialogScreens.createFromData(
                    dialog, FabricLifecyclePort.currentScreen(), NOOP_CONNECTION);
            scriptScreen = screen;
            scriptFuture = future;
            scriptActionIds = List.copyOf(ids);
            Minecraft.getInstance().gui.setScreen(screen);
        });
        return future;
    }

    /** Clicks one action on the open dialog, resolving a pending script dialog future. */
    private void clickActionNow(int index) {
        Screen screen = FabricLifecyclePort.currentScreen();
        if (!(screen instanceof DialogScreen<?> dialogScreen)) {
            throw new IllegalStateException("no dialog is open");
        }
        Dialog dialog = ((ink.tuanzi.neomacros.adapter.mixin.DialogScreenAccessor)dialogScreen).neomacros$dialog();
        if (screen == scriptScreen) {
            String id = index >= 0 && index < scriptActionIds.size()
                    ? scriptActionIds.get(index) : null;
            resolveScriptFuture(id);
            Minecraft.getInstance().gui.setScreen(null);
            return;
        }
        List<ActionButton> buttons = flattenedActions(dialog);
        if (index < 0 || index >= buttons.size()) {
            throw new IllegalStateException("dialog action index out of range: " + index);
        }
        ActionButton button = buttons.get(index);
        Optional<net.minecraft.network.chat.ClickEvent> click = Optional.empty();
        if (button.action().isPresent()) {
            try {
                click = button.action().get().createAction(Map.of());
            } catch (Throwable ignored) {
                click = Optional.empty();
            }
        }
        dialogScreen.runAction(click, dialog.common().afterAction());
    }

    /** Resolves the pending script dialog future when its screen is gone. */
    private void resolveScriptFutureIfClosed() {
        if (scriptFuture != null && !scriptFuture.isDone()
                && FabricLifecyclePort.currentScreen() != scriptScreen) {
            resolveScriptFuture(null);
        }
    }

    private void resolveScriptFuture(String id) {
        if (scriptFuture != null && !scriptFuture.isDone()) {
            scriptFuture.complete(id);
        }
        scriptFuture = null;
        scriptScreen = null;
        scriptActionIds = List.of();
    }

    /** Builds one dialog record from the plain show() config. */
    private Dialog buildDialog(Map<String, Object> config, List<String> actionIds) {
        String type = String.valueOf(config.getOrDefault("type", "notice"));
        Component title = Component.literal(str(config.get("title"), "Dialog"));
        boolean canCloseWithEscape = bool(config.get("canCloseWithEscape"), true);
        CommonDialogData common = new CommonDialogData(title, Optional.empty(), canCloseWithEscape,
                false, DialogAction.CLOSE, bodyOf(config.get("body")), List.of());

        List<Map<String, Object>> actionConfig = actionsOf(config.get("actions"));
        List<ActionButton> buttons = new ArrayList<>();
        int fallback = 0;
        for (Map<String, Object> entry : actionConfig) {
            String label = str(entry.get("label"), String.valueOf(fallback + 1));
            String id = entry.get("id") != null ? str(entry.get("id"), String.valueOf(fallback))
                    : String.valueOf(fallback);
            actionIds.add(id);
            buttons.add(new ActionButton(new CommonButtonData(Component.literal(label), 150),
                    Optional.empty()));
            fallback++;
        }

        return switch (type) {
            case "confirmation" -> {
                ActionButton yes = buttons.isEmpty()
                        ? new ActionButton(new CommonButtonData(Component.literal("Yes"), 150), Optional.empty())
                        : buttons.get(0);
                ActionButton no = buttons.size() > 1
                        ? buttons.get(1)
                        : new ActionButton(new CommonButtonData(Component.literal("No"), 150), Optional.empty());
                yield new ConfirmationDialog(common, yes, no);
            }
            case "multi_action" -> new MultiActionDialog(common, buttons,
                    Optional.empty(), intOf(config.get("columns"), 2));
            default -> new NoticeDialog(common,
                    buttons.isEmpty()
                            ? new ActionButton(new CommonButtonData(Component.literal("OK"), 150), Optional.empty())
                            : buttons.get(0));
        };
    }

    /** Builds the neutral snapshot of one open dialog. */
    private DialogInfo snapshot(Dialog dialog, boolean scriptShown) {
        CommonDialogData common = dialog.common();
        List<String> body = new ArrayList<>();
        for (DialogBody part : common.body()) {
            if (part instanceof PlainMessage plain) {
                body.add(plain.contents().getString());
            } else if (part instanceof ItemBody item) {
                item.description().ifPresent(description -> body.add(description.contents().getString()));
            }
        }
        List<DialogPort.DialogInputInfo> inputs = new ArrayList<>();
        for (Input input : common.inputs()) {
            inputs.add(new DialogPort.DialogInputInfo(input.key(),
                    controlType(input), controlValue(input)));
        }
        List<DialogPort.DialogActionInfo> actions = new ArrayList<>();
        List<ActionButton> buttons = flattenedActions(dialog);
        for (int i = 0; i < buttons.size(); i++) {
            actions.add(new DialogPort.DialogActionInfo(i,
                    buttons.get(i).button().label().getString(), String.valueOf(i)));
        }
        return new DialogInfo(dialogKind(dialog), common.title().getString(),
                common.canCloseWithEscape(), List.copyOf(body), List.copyOf(actions),
                List.copyOf(inputs), scriptShown);
    }

    /** Flattens the clickable buttons of one dialog in stable index order. */
    private static List<ActionButton> flattenedActions(Dialog dialog) {
        List<ActionButton> buttons = new ArrayList<>();
        if (dialog instanceof NoticeDialog notice) {
            buttons.add(notice.action());
        } else if (dialog instanceof ConfirmationDialog confirmation) {
            buttons.addAll(confirmation.mainActions());
        } else if (dialog instanceof MultiActionDialog multi) {
            buttons.addAll(multi.actions());
            multi.exitAction().ifPresent(buttons::add);
        } else if (dialog instanceof DialogListDialog list) {
            list.exitAction().ifPresent(buttons::add);
        } else if (dialog instanceof ServerLinksDialog links) {
            links.exitAction().ifPresent(buttons::add);
        }
        return buttons;
    }

    private static String dialogKind(Dialog dialog) {
        if (dialog instanceof NoticeDialog) {
            return "notice";
        }
        if (dialog instanceof ConfirmationDialog) {
            return "confirmation";
        }
        if (dialog instanceof MultiActionDialog) {
            return "multi_action";
        }
        if (dialog instanceof DialogListDialog) {
            return "list";
        }
        if (dialog instanceof ServerLinksDialog) {
            return "server_links";
        }
        return dialog.getClass().getSimpleName();
    }

    private static String controlType(Input input) {
        if (input.control() instanceof TextInput) {
            return "text";
        }
        if (input.control() instanceof BooleanInput) {
            return "boolean";
        }
        if (input.control() instanceof NumberRangeInput) {
            return "number";
        }
        if (input.control() instanceof SingleOptionInput) {
            return "option";
        }
        return input.control().getClass().getSimpleName();
    }

    /** The declared initial value of one control, or null when not extracted. */
    private static String controlValue(Input input) {
        if (input.control() instanceof TextInput text) {
            return text.initial();
        }
        if (input.control() instanceof BooleanInput flag) {
            return String.valueOf(flag.initial());
        }
        if (input.control() instanceof NumberRangeInput range) {
            return String.valueOf(range.rangeInfo().initialSliderValue());
        }
        if (input.control() instanceof SingleOptionInput option) {
            return option.initial().map(entry -> entry.id()).orElse(null);
        }
        return null;
    }

    private static List<DialogBody> bodyOf(Object config) {
        List<DialogBody> body = new ArrayList<>();
        if (config instanceof Iterable<?> texts) {
            for (Object item : texts) {
                body.add(new PlainMessage(Component.literal(str(item, "")),
                        PlainMessage.DEFAULT_WIDTH));
            }
        } else if (config != null) {
            body.add(new PlainMessage(Component.literal(str(config, "")),
                    PlainMessage.DEFAULT_WIDTH));
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> actionsOf(Object config) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (config instanceof Iterable<?> entries) {
            for (Object item : entries) {
                if (item instanceof Map<?, ?> entry) {
                    actions.add((Map<String, Object>) entry);
                }
            }
        }
        return actions;
    }

    private static String str(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    private static int intOf(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
