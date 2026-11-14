package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.RenderPort;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * HUD overlay render backed by Fabric's HudElementRegistry (26.3 HudElement
 * pipeline). Each layer attaches a draw callback; text rendering uses the
 * GuiGraphicsExtractor.text method.
 */
public final class FabricRenderPort implements RenderPort {

    private final Map<String, BiConsumer<Integer, ink.tuanzi.neomacros.port.model.DrawContext2d>> layers = new ConcurrentHashMap<>();
    private final java.util.Set<String> textureIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void addLayer(String id, int z, BiConsumer<Integer, ink.tuanzi.neomacros.port.model.DrawContext2d> painter) {
        var identifier = Identifier.withDefaultNamespace("hud_" + id);
        layers.put(id, painter);
        HudElementRegistry.addLast(identifier, (extractor, deltaTracker) -> {
            painter.accept(0, new ink.tuanzi.neomacros.port.model.DrawContext2d() {
                @Override
                public void text(int x, int y, String text) {
                    text(x, y, text, 0xFFFFFFFF);
                }

                @Override
                public void text(int x, int y, String text, int color) {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    extractor.text(mc.font, text, x, y, color);
                }

                @Override
                public void rect(int x, int y, int width, int height, int color) {
                    extractor.fill(x, y, x + width, y + height, color);
                }

                @Override
                public void outline(int x, int y, int width, int height, int color) {
                    extractor.outline(x, y, x + width, y + height, color);
                }

                @Override
                public void sprite(int x, int y, int width, int height, String textureId) {
                    var texture = Identifier.tryParse(textureId);
                    if (texture != null) {
                        extractor.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                                texture, x, y, 0f, 0f, width, height, width, height);
                    }
                }
            });
        });
    }

    @Override
    public void removeLayer(String id) {
        layers.remove(id);
        HudElementRegistry.removeElement(Identifier.withDefaultNamespace("hud_" + id));
    }

    @Override
    public Supplier<List<String>> layerIds() {
        return () -> List.copyOf(layers.keySet());
    }

    /**
     * Creates or replaces a texture from ARGB pixels on the game main
     * thread: the pixels land in a {@code NativeImage} ({@code setPixel}
     * takes ARGB on all four milestone nodes), a {@code DynamicTexture}
     * uploads it, and the texture manager registers it under the exact
     * identifier. Re-registering an identifier replaces the backing
     * texture (the texture manager closes the displaced one), so a canvas
     * re-upload never duplicates registrations.
     */
    @Override
    public java.util.concurrent.CompletableFuture<Void> putTexture(
            String id, int width, int height, int[] argb) {
        java.util.concurrent.CompletableFuture<Void> future =
                new java.util.concurrent.CompletableFuture<>();
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            try {
                var identifier = Identifier.tryParse(id);
                if (identifier == null) {
                    throw new IllegalArgumentException("invalid texture identifier: " + id);
                }
                if (width <= 0 || height <= 0) {
                    throw new IllegalArgumentException(
                            "texture dimensions must be positive: " + width + "x" + height);
                }
                if (argb == null || argb.length < width * height) {
                    throw new IllegalArgumentException(
                            "texture pixel buffer too small for " + width + "x" + height);
                }
                var image = new com.mojang.blaze3d.platform.NativeImage(width, height, false);
                for (int y = 0; y < height; y++) {
                    int row = y * width;
                    for (int x = 0; x < width; x++) {
                        image.setPixel(x, y, argb[row + x]);
                    }
                }
                var texture = new net.minecraft.client.renderer.texture.DynamicTexture(
                        () -> id, image);
                net.minecraft.client.Minecraft.getInstance().getTextureManager()
                        .register(identifier, texture);
                textureIds.add(id);
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public Supplier<List<String>> textureIds() {
        return () -> List.copyOf(textureIds);
    }
}
