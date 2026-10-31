package ink.tuanzi.neomacros.adapter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Input sources backed by vanilla KeyMapping (tick-polled press edges) and a
 * wheel sink fed from the scroll mixin. Keybinds register into the vanilla
 * options so they surface in the controls menu.
 */
public final class FabricInputPort implements ink.tuanzi.neomacros.port.InputPort {

    private record Keybind(KeyMapping mapping, List<Consumer<Integer>> sinks) {
    }

    private final Map<String, Keybind> keybinds = new java.util.LinkedHashMap<>();
    private final List<DoubleConsumer> scrollSinks = new CopyOnWriteArrayList<>();

    /** Creates the port and starts the tick poll for press edges. */
    public FabricInputPort() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (Keybind keybind : keybinds.values()) {
                while (keybind.mapping().consumeClick()) {
                    for (Consumer<Integer> sink : keybind.sinks()) {
                        sink.accept(1);
                    }
                }
            }
        });
    }

    @Override
    public void registerKeybind(String keyName, Consumer<Integer> sink) {
        var key = InputConstants.getKey(keyName);
        var category = net.minecraft.client.KeyMapping.Category.register(
                net.minecraft.resources.Identifier.withDefaultNamespace("neomacros"));
        var mapping = new KeyMapping("neomacros.keybind." + keyName,
                InputConstants.Type.KEYBOARD, key.getValue(), category);
        KeyMappingHelper.registerKeyMapping(mapping);
        keybinds.put(keyName, new Keybind(mapping, new CopyOnWriteArrayList<>(List.of(sink))));
    }

    @Override
    public void registerKeyPress(String keyName, Runnable sink) {
        registerKeybind(keyName, count -> sink.run());
    }

    @Override
    public void onScroll(Consumer<Double> sink) {
        scrollSinks.add(sink::accept);
    }
}
