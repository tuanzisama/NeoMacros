package ink.tuanzi.neomacros.adapter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Input sources backed by vanilla KeyMapping (tick-polled press edges) and a
 * wheel sink fed from the scroll mixin. Keybinds register into the vanilla
 * options so they surface in the controls menu. Script-facade keybinds use
 * generated handle ids; manifest-driven script bindings use the script name
 * as their id and rebind in place ({@link KeyMapping#setKey}) because fabric
 * api offers no keymapping removal.
 */
public final class FabricInputPort implements ink.tuanzi.neomacros.port.InputPort {

    private record Keybind(KeyMapping mapping, CopyOnWriteArrayList<Consumer<Integer>> sinks) {
    }

    private final Map<String, Keybind> keybinds = new java.util.LinkedHashMap<>();
    private final List<DoubleConsumer> scrollSinks = new CopyOnWriteArrayList<>();
    private final AtomicLong handleSequence = new AtomicLong();

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
    public Object registerKeybind(String keyName, Consumer<Integer> sink) {
        String handle = "keybind-" + handleSequence.incrementAndGet();
        register(handle, "neomacros.keybind." + keyName, keyName, sink);
        return handle;
    }

    @Override
    public void registerKeyPress(String keyName, Runnable sink) {
        registerKeybind(keyName, count -> sink.run());
    }

    /**
     * Registers one manifest-driven script binding keyed by script name;
     * rebinding the same script keeps its KeyMapping and switches the bound
     * key in place. Empty key names are rejected (an unbind is represented
     * by not registering).
     */
    @Override
    public void registerScriptBinding(String scriptName, String keyName, Consumer<Integer> sink) {
        if (keyName.isEmpty()) {
            return;
        }
        String id = "script:" + scriptName;
        Keybind existing = keybinds.get(id);
        if (existing != null) {
            existing.sinks().clear();
            existing.sinks().add(sink);
            existing.mapping().setKey(InputConstants.getKey(keyName));
            return;
        }
        register(id, "neomacros.script." + scriptName, keyName, sink);
    }

    /**
     * Removes one script's manifest-driven binding, if present. The mapping
     * stays listed (unbound) in the controls menu since fabric api offers no
     * removal; its sinks detach so nothing fires.
     */
    @Override
    public void unregisterScriptBinding(String scriptName) {
        detach("script:" + scriptName);
    }

    /** Registers one keybind under a stable id. */
    private void register(String id, String translationKey, String keyName, Consumer<Integer> sink) {
        var key = InputConstants.getKey(keyName);
        //? if <1.21.11 {
        /*
        var mapping = new KeyMapping(translationKey,
                InputConstants.Type.KEYBOARD, key.getValue(), "neomacros");
        */
        //?}
        //? if >=1.21.11 {
        var category = net.minecraft.client.KeyMapping.Category.register(net.minecraft.resources.Identifier.withDefaultNamespace("neomacros"));
        var mapping = newMapping(translationKey, key.getValue(), category);
        //?}
        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
        keybinds.put(id, new Keybind(mapping, new CopyOnWriteArrayList<>(List.of(sink))));
    }

    /** Detaches one keybind by id: sinks cleared, mapping left unbound. */
    private void detach(String id) {
        Keybind keybind = keybinds.get(id);
        if (keybind != null) {
            keybind.sinks().clear();
            keybind.mapping().setKey(InputConstants.UNKNOWN);
        }
    }

    //? if >=1.21.11 {
    /** Builds one key mapping; the input type constant renames in 26.3. */
    private static KeyMapping newMapping(String name, int key,
            net.minecraft.client.KeyMapping.Category category) {
        return new KeyMapping(name, InputConstants.Type.KEYBOARD, key, category);
    }
    //?}

    @Override
    public void unregister(Object handle) {
        if (handle instanceof String id) {
            detach(id);
        }
    }

    @Override
    public void onScroll(Consumer<Double> sink) {
        scrollSinks.add(sink::accept);
    }
}
