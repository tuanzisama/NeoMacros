package ink.tuanzi.neomacros.port;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Dialog queries over the client dialog system (vanilla dialogs are
 * server-initiated screens; scripts can also open their own). All reads
 * answer with point-in-time neutral snapshots.
 */
public interface DialogPort {

    /** One flattened action button: label plus a stable index-based id. */
    record DialogActionInfo(int index, String label, String id) {
    }

    /** One declared input control: key, control type, and declared default (live widget values are not read). */
    record DialogInputInfo(String key, String type, String value) {
    }

    /** Neutral snapshot of the currently open dialog. */
    record DialogInfo(String kind, String title, boolean canCloseWithEscape,
                      List<String> body, List<DialogActionInfo> actions,
                      List<DialogInputInfo> inputs, boolean scriptShown) {
    }

    /** The currently open dialog, or empty when no dialog screen is open. */
    default Optional<DialogInfo> currentDialog() {
        return Optional.empty();
    }

    /** Clicks one action button by its flattened index (dialog kind decides availability). */
    default java.util.concurrent.CompletableFuture<Void> clickAction(int index) {
        throw new UnsupportedPortFeature("dialog actions");
    }

    /** Closes the currently open dialog (script-shown dialogs resolve their show() future with null). */
    default java.util.concurrent.CompletableFuture<Void> close() {
        throw new UnsupportedPortFeature("dialog actions");
    }

    /**
     * Opens a client-side dialog from a plain config map ({@code type},
     * {@code title}, {@code body}, {@code canCloseWithEscape},
     * {@code actions}) and resolves with the action id chosen through
     * {@code clickAction}; manual button clicks resolve with null when the
     * dialog closes.
     */
    default java.util.concurrent.CompletableFuture<String> show(java.util.Map<String, Object> config) {
        throw new UnsupportedPortFeature("dialog show");
    }
}
