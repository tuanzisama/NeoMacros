package ink.tuanzi.neomacros.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * ModMenu bridge: points the config button at the Cloth settings screen.
 * Fabric instantiates the modmenu entrypoint only when ModMenu is installed,
 * so the ModMenu types stay unloaded in plain deployments.
 */
public final class NeoMacrosModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance()
                .isModLoaded("cloth-config2")) {
            return NeoMacrosClothConfig::create;
        }
        return parent -> null;
    }
}
