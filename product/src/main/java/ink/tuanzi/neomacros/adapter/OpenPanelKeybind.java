package ink.tuanzi.neomacros.adapter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;

/**
 * The mod's own controls-menu keybind (default N) that opens the management
 * panel. Registered once at bootstrap; the press edge is tick-polled like
 * every script binding.
 */
public final class OpenPanelKeybind {

    private OpenPanelKeybind() {
    }

    /** Registers the keybind and delivers press edges to {@code onPress}. */
    public static void register(Runnable onPress) {
        var key = InputConstants.getKey("key.keyboard.n");
        //? if <1.21.11 {
        /*
        var mapping = new KeyMapping("key.neomacros.open_panel",
                InputConstants.Type.KEYBOARD, key.getValue(), "neomacros");
        */
        //?}
        //? if >=1.21.11 {
        var category = net.minecraft.client.KeyMapping.Category.register(net.minecraft.resources.Identifier.withDefaultNamespace("neomacros"));
        var mapping = new KeyMapping("key.neomacros.open_panel",
                InputConstants.Type.KEYBOARD, key.getValue(), category);
        //?}
        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (mapping.consumeClick()) {
                onPress.run();
            }
        });
    }
}
