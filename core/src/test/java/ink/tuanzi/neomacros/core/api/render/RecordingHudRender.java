package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.model.DrawContext2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Recording {@link RenderPort} for HUD layer tests: the port-call log
 * ({@code add}/{@code setz}/{@code remove}), the registered painters, and
 * frame captures rendered in {@code (z, id)} order.
 */
public final class RecordingHudRender implements RenderPort {

    /** Layer id → painter. */
    private final Map<String, BiConsumer<Integer, DrawContext2d>> painters =
            new ConcurrentHashMap<>();
    private final Map<String, Integer> zs = new ConcurrentHashMap<>();
    private final List<String> events = new CopyOnWriteArrayList<>();

    @Override
    public void addLayer(String id, int z, BiConsumer<Integer, DrawContext2d> painter) {
        painters.put(id, painter);
        zs.put(id, z);
        events.add("add:" + id + ":" + z);
    }

    @Override
    public void removeLayer(String id) {
        if (painters.remove(id) != null) {
            zs.remove(id);
            events.add("remove:" + id);
        }
    }

    @Override
    public void setLayerZ(String id, int z) {
        if (painters.containsKey(id)) {
            zs.put(id, z);
            events.add("setz:" + id + ":" + z);
        }
    }

    @Override
    public Supplier<List<String>> layerIds() {
        return () -> painters.keySet().stream()
                .sorted(java.util.Comparator
                        .comparingInt((String id) -> zs.getOrDefault(id, 0))
                        .thenComparing(java.util.Comparator.naturalOrder()))
                .toList();
    }

    /** The ordered port-call log: {@code add:id:z}, {@code setz:id:z}, {@code remove:id}. */
    public List<String> events() {
        return List.copyOf(events);
    }

    /** Fires one frame across every registered layer in render order. */
    public List<String> frame(int tick) {
        List<String> lines = new ArrayList<>();
        DrawContext2d ctx = new Lines(lines);
        for (String id : layerIds().get()) {
            BiConsumer<Integer, DrawContext2d> painter = painters.get(id);
            if (painter != null) {
                painter.accept(tick, ctx);
            }
        }
        return lines;
    }

    /** Collects draw calls as one readable line per primitive. */
    private static final class Lines implements DrawContext2d {
        private final List<String> sink;

        Lines(List<String> sink) {
            this.sink = sink;
        }

        @Override
        public void text(int x, int y, String text) {
            text(x, y, text, 0xFFFFFFFF);
        }

        @Override
        public void text(int x, int y, String text, int color) {
            sink.add("text:" + x + "," + y + "," + text + "," + hex(color));
        }

        @Override
        public void rect(int x, int y, int width, int height, int color) {
            sink.add("rect:" + x + "," + y + "," + width + "," + height + "," + hex(color));
        }

        @Override
        public void outline(int x, int y, int width, int height, int color) {
            sink.add("outline:" + x + "," + y + "," + width + "," + height + ","
                    + hex(color));
        }

        @Override
        public void sprite(int x, int y, int width, int height, String textureId) {
            sink.add("sprite:" + x + "," + y + "," + width + "," + height + "," + textureId);
        }

        private static String hex(int color) {
            return String.format("%08x", color);
        }
    }
}
