package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.UnsupportedPortFeature;
import ink.tuanzi.neomacros.port.WorldPort;

/**
 * The 26.3 node's adapter: lifecycle, chat, inventory, and input are
 * implemented against Fabric API and the vanilla client; the remaining ports
 * are implemented by later increments and raise {@link UnsupportedPortFeature}
 * until then.
 */
public final class NeomacrosAdapterImpl implements NeomacrosAdapter {

    private final FabricLifecyclePort lifecycle = new FabricLifecyclePort();
    private final FabricChatPort chat = new FabricChatPort();
    private final FabricInputPort input = new FabricInputPort();
    private final FabricInventoryPort inventory = new FabricInventoryPort();

    @Override
    public LifecyclePort lifecycle() {
        return lifecycle;
    }

    @Override
    public ChatPort chat() {
        return chat;
    }

    @Override
    public InputPort input() {
        return input;
    }

    @Override
    public InventoryPort inventory() {
        return inventory;
    }

    @Override
    public WorldPort world() {
        throw new UnsupportedPortFeature("world");
    }

    @Override
    public EntityPort entity() {
        throw new UnsupportedPortFeature("entity");
    }

    @Override
    public PlayerPort player() {
        throw new UnsupportedPortFeature("player");
    }
}
