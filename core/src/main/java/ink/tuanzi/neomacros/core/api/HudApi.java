package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.hud} facade: HUD overlay text rendered on top of the vanilla
 * HUD. Text entries persist across frames until removed or the module
 * unloads.
 */
@ApiModule(name = "hud", category = "界面")
public final class HudApi {

    private final HudRegistry registry;

    /** Creates the facade over the HUD registry (bootstrap-provided). */
    public HudApi(HudRegistry registry) {
        this.registry = registry;
    }

    /**
     * Adds a persistent text entry on the HUD overlay.
     *
     * @param id stable text identifier (remove via {@link #removeText})
     * @param text the text content
     * @param x screen x (pixels from left)
     * @param y screen y (pixels from top)
     * @param color ARGB color (e.g. {@code 0xFFFFFFFF} for white)
     * @zh 在 HUD 覆盖层上添加一条持久文本。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> addText(String id, String text, int x, int y, int color) {
        return registry.addText(id, text, x, y, color);
    }

    /**
     * Removes a previously added text entry.
     *
     * @zh 移除一条 HUD 文本。
     */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> removeText(String id) {
        return registry.removeText(id);
    }

    /**
     * Lists all registered text entry identifiers.
     *
     * @zh 列出所有已注册的 HUD 文本标识。
     */
    @ApiSince("0.1.0")
    public List<String> textIds() {
        return registry.textIds();
    }

    /** Internal registry surface implemented by the render subsystem. */
    public interface HudRegistry {
        CompletableFuture<Void> addText(String id, String text, int x, int y, int color);

        CompletableFuture<Void> removeText(String id);

        List<String> textIds();
    }
}
