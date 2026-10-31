package ink.tuanzi.neomacros.e2e.harness;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.UnsupportedPortFeature;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.ChatComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The E2E stub adapter: records chat output, holds lifecycle sinks that tests
 * fire explicitly, and exposes injection methods as the scripted counterpart
 * of the game's event sources. Ports without canned behavior in this harness
 * raise {@link UnsupportedPortFeature}.
 */
public final class StubAdapter implements NeomacrosAdapter {

    private final StubLifecycle lifecycle = new StubLifecycle();
    private final StubChat chat = new StubChat();

    public StubLifecycle lifecyclePort() {
        return lifecycle;
    }

    public StubChat chatPort() {
        return chat;
    }

    @Override
    public LifecyclePort lifecycle() {
        return lifecycle;
    }

    @Override
    public ChatPort chat() {
        return chat;
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
    public ink.tuanzi.neomacros.port.InputPort input() {
        throw new UnsupportedPortFeature("input");
    }

    @Override
    public ink.tuanzi.neomacros.port.InventoryPort inventory() {
        throw new UnsupportedPortFeature("inventory");
    }

    @Override
    public RenderPort render() {
        throw new UnsupportedPortFeature("render");
    }

    @Override
    public PlayerPort player() {
        throw new UnsupportedPortFeature("player");
    }

    /** Lifecycle sink fan-out with explicit test-side firing. */
    public static final class StubLifecycle implements LifecyclePort {
        final List<Consumer<Long>> tickSinks = new CopyOnWriteArrayList<>();
        final List<Runnable> startSinks = new CopyOnWriteArrayList<>();
        final List<Runnable> quitSinks = new CopyOnWriteArrayList<>();
        final List<Runnable> joinSinks = new CopyOnWriteArrayList<>();
        final List<Runnable> leaveSinks = new CopyOnWriteArrayList<>();
        final AtomicBooleanHolder inWorld = new AtomicBooleanHolder();

        /** Fires the tick sink with the given tick count. */
        public void fireTick(long tick) {
            for (Consumer<Long> sink : tickSinks) {
                sink.accept(tick);
            }
        }

        /** Fires the game-start sink. */
        public void fireGameStart() {
            for (Runnable sink : startSinks) {
                sink.run();
            }
        }

        /** Fires the game-quit sink. */
        public void fireGameQuit() {
            for (Runnable sink : quitSinks) {
                sink.run();
            }
        }

        /** Fires the world-join sink. */
        public void fireWorldJoin() {
            for (Runnable sink : joinSinks) {
                sink.run();
            }
        }

        /** Fires the world-leave sink. */
        public void fireWorldLeave() {
            for (Runnable sink : leaveSinks) {
                sink.run();
            }
        }

        /** Sets the in-world answer. */
        public void setInWorld(boolean value) {
            inWorld.value = value;
        }

        @Override
        public void onTick(Consumer<Long> sink) {
            tickSinks.add(sink);
        }

        @Override
        public void onGameStart(Runnable sink) {
            startSinks.add(sink);
        }

        @Override
        public void onGameQuit(Runnable sink) {
            quitSinks.add(sink);
        }

        @Override
        public void onWorldJoin(Runnable sink) {
            joinSinks.add(sink);
        }

        @Override
        public void onWorldLeave(Runnable sink) {
            leaveSinks.add(sink);
        }

        @Override
        public Supplier<Boolean> inWorld() {
            return () -> inWorld.value;
        }
    }

    /** Chat output recording plus a fireable receive sink. */
    public static final class StubChat implements ChatPort {
        final List<String> output = new ArrayList<>();
        final List<Consumer<ChatComponent>> receiveSinks = new CopyOnWriteArrayList<>();

        /** Delivers one displayed message to the receive sinks. */
        public void fireReceive(String text) {
            ChatComponent message = ChatComponent.plain(text);
            for (Consumer<ChatComponent> sink : receiveSinks) {
                sink.accept(message);
            }
        }

        /** Recorded output lines in order (prefixed by channel). */
        public List<String> output() {
            return List.copyOf(output);
        }

        @Override
        public void say(String message) {
            output.add("say:" + message);
        }

        @Override
        public void log(String message) {
            output.add("log:" + message);
        }

        @Override
        public void actionbar(String message) {
            output.add("bar:" + message);
        }

        @Override
        public void onChatReceive(Consumer<ChatComponent> sink) {
            receiveSinks.add(sink);
        }
    }

    private static final class AtomicBooleanHolder {
        boolean value;
    }
}
