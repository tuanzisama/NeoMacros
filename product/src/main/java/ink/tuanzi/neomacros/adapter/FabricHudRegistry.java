package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.core.api.HudApi;
import ink.tuanzi.neomacros.port.RenderPort;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * HUD text registry over the render port: each text entry is a dedicated
 * render layer that paints one string. Registration completes on the game
 * main thread (the Fabric HUD element registry must be touched there).
 */
public final class FabricHudRegistry implements HudApi.HudRegistry {

    private static final String LAYER_PREFIX = "text_";

    private final RenderPort render;
    private final Executor mainExecutor;

    /** Creates the registry over the render port and a main-thread executor. */
    public FabricHudRegistry(RenderPort render, Executor mainExecutor) {
        this.render = render;
        this.mainExecutor = mainExecutor;
    }

    @Override
    public CompletableFuture<Void> addText(String id, String text, int x, int y, int color) {
        return CompletableFuture.runAsync(() -> {
            String layer = LAYER_PREFIX + id;
            render.removeLayer(layer);
            render.addLayer(layer, 0, (tick, ctx) -> ctx.text(x, y, text, color));
        }, mainExecutor);
    }

    @Override
    public CompletableFuture<Void> removeText(String id) {
        return CompletableFuture.runAsync(() ->
                render.removeLayer(LAYER_PREFIX + id), mainExecutor);
    }

    @Override
    public List<String> textIds() {
        return render.layerIds().get().stream()
                .filter(id -> id.startsWith(LAYER_PREFIX))
                .map(id -> id.substring(LAYER_PREFIX.length()))
                .toList();
    }
}
