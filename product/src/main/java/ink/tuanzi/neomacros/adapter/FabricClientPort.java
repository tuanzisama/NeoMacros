package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.ClientPort;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

/**
 * Client context port over the running game client process.
 */
public final class FabricClientPort implements ClientPort {

    @Override
    public int fps() {
        return MainThread.run(Minecraft.getInstance()::getFps);
    }

    @Override
    public String gameVersion() {
        return MainThread.run(() -> SharedConstants.getCurrentVersion().getName());
    }

    @Override
    public Object minecraftInstance() {
        return Minecraft.getInstance();
    }
}
