package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.RenderPort;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * HUD overlay render backed by Fabric's HudElementRegistry (the 26.3 /
 * 1.21.6 HudElement pipeline). One resident element
 * ({@code neomacros:hud}) is registered for the whole port; every frame it
 * replays the layer painters in {@code (z, id)} order, so cross-layer
 * z-order is real and the registry never churns as layers come and go.
 * Text rendering uses the {@code GuiGraphicsExtractor.text} method
 * (rewritten by the node replacement table on pre-1.21.11 nodes).
 */
public final class FabricRenderPort implements RenderPort {

    /** The single resident HUD element; script layer ids never join it. */
    private static final Identifier HUD_ELEMENT =
            Identifier.tryParse("neomacros:hud");

    /** One registered layer: its sort key and frame painter. */
    private record LayerEntry(LayerKey key,
            BiConsumer<Integer, ink.tuanzi.neomacros.port.model.DrawContext2d> painter) {
    }

    /** Sort key: z ascending, then id ascending (stable render order). */
    private record LayerKey(int z, String id) {
    }

    private static final Comparator<LayerKey> RENDER_ORDER =
            Comparator.comparingInt(LayerKey::z).thenComparing(LayerKey::id);

    /** Layer id → entry (for z re-keying and painter lookup). */
    private final Map<String, LayerEntry> layers = new ConcurrentHashMap<>();
    /** (z, id) → layer id; the frame replay iterates this in render order. */
    private final ConcurrentSkipListMap<LayerKey, String> order =
            new ConcurrentSkipListMap<>(RENDER_ORDER);
    private final java.util.Set<String> textureIds =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Whether the resident element currently sits in the HUD registry. */
    private volatile boolean elementRegistered;

    @Override
    public void addLayer(String id, int z,
            BiConsumer<Integer, ink.tuanzi.neomacros.port.model.DrawContext2d> painter) {
        LayerEntry previous = layers.remove(id);
        if (previous != null) {
            order.remove(previous.key());
        }
        LayerEntry entry = new LayerEntry(new LayerKey(z, id), painter);
        layers.put(id, entry);
        order.put(entry.key(), id);
        if (HUD_ELEMENT != null && !elementRegistered) {
            elementRegistered = true;
            HudElementRegistry.addLast(HUD_ELEMENT, (extractor, deltaTracker) -> {
                // The extractor type stays inferred so the node replacement
                // table needs no //? branch for the callback parameter.
                var context = new ink.tuanzi.neomacros.port.model.DrawContext2d() {
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
                    public void sprite(int x, int y, int width, int height,
                            String textureId) {
                        var texture = Identifier.tryParse(textureId);
                        if (texture != null) {
                            extractor.blit(
                                    net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                                    texture, x, y, 0f, 0f, width, height, width, height);
                        }
                    }
                };
                for (String layerId : order.values()) {
                    LayerEntry current = layers.get(layerId);
                    if (current != null) {
                        current.painter().accept(0, context);
                    }
                }
            });
        }
    }

    @Override
    public void removeLayer(String id) {
        LayerEntry entry = layers.remove(id);
        if (entry != null) {
            order.remove(entry.key());
        }
        if (elementRegistered && layers.isEmpty() && HUD_ELEMENT != null) {
            elementRegistered = false;
            HudElementRegistry.removeElement(HUD_ELEMENT);
        }
    }

    @Override
    public void setLayerZ(String id, int z) {
        LayerEntry entry = layers.get(id);
        if (entry == null || entry.key().z() == z) {
            return;
        }
        order.remove(entry.key());
        LayerEntry rekeyed = new LayerEntry(new LayerKey(z, id), entry.painter());
        layers.put(id, rekeyed);
        order.put(rekeyed.key(), id);
    }

    @Override
    public Supplier<List<String>> layerIds() {
        return () -> List.copyOf(order.values());
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

    /**
     * Removes a texture on the game main thread: {@code TextureManager
     * .release(identifier)} drops the entry and safely closes the backing
     * texture (verified on all four milestone nodes; unknown identifiers
     * are a no-op), so a disposed registry entry frees its GPU slot and a
     * later {@link #putTexture} with the same id registers afresh.
     */
    @Override
    public java.util.concurrent.CompletableFuture<Void> removeTexture(String id) {
        java.util.concurrent.CompletableFuture<Void> future =
                new java.util.concurrent.CompletableFuture<>();
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            try {
                var identifier = Identifier.tryParse(id);
                if (identifier == null) {
                    throw new IllegalArgumentException("invalid texture identifier: " + id);
                }
                net.minecraft.client.Minecraft.getInstance().getTextureManager()
                        .release(identifier);
                textureIds.remove(id);
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}
