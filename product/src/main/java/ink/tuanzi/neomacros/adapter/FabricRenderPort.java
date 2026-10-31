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
}
