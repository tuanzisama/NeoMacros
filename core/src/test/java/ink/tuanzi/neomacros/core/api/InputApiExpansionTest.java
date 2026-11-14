package ink.tuanzi.neomacros.core.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The input expansion: injection (press/release/hold/set) and the unified
 * subscription shape for registrations.
 */
class InputApiExpansionTest {

    /** Recording bridge: captures every call in order. */
    static final class RecordingBridge implements InputApi.InputBridge {
        final List<String> calls = new ArrayList<>();
        /** Bind names currently held (drives bindDown / pressedKeys). */
        final java.util.Set<String> held = new java.util.LinkedHashSet<>();

        @Override
        public Object registerKeybind(String keyName, java.util.function.Consumer<Integer> sink) {
            calls.add("register:" + keyName);
            return "kb-handle";
        }

        @Override
        public Object registerKeyPress(String keyName, Runnable sink) {
            calls.add("keypress:" + keyName);
            return "kp-handle";
        }

        @Override
        public void onScroll(java.util.function.DoubleConsumer sink) {
        }

        @Override
        public void unregister(Object handle) {
            calls.add("unregister:" + handle);
        }

        @Override
        public void injectKey(String keyName, boolean down) {
            calls.add("key:" + keyName + "=" + down);
        }

        @Override
        public void injectBind(String bindName, boolean down) {
            calls.add("bind:" + bindName + "=" + down);
            if (down) {
                held.add(bindName);
            } else {
                held.remove(bindName);
            }
        }

        @Override
        public boolean bindDown(String bindName) {
            return held.contains(bindName);
        }

        @Override
        public java.util.List<ink.tuanzi.neomacros.port.model.KeybindData> keybinds() {
            return java.util.List.of(
                    new ink.tuanzi.neomacros.port.model.KeybindData("key.forward", "W"));
        }

        @Override
        public void setBind(String bindName, String keyName) {
            calls.add("rebind:" + bindName + "=" + keyName);
        }
    }

    @Test
    void injectionPressReleaseHoldAndSet() throws Exception {
        var delayer = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
        try {
            RecordingBridge bridge = new RecordingBridge();
            InputApi api = new InputApi(bridge, delayer);

            api.press("key.keyboard.r");
            api.release("key.keyboard.r");
            api.set("key.use", true);
            api.set("key.use", false);
            api.hold("key.keyboard.h", 40).get(2, TimeUnit.SECONDS);

            assertEquals(List.of(
                    "key:key.keyboard.r=true",
                    "key:key.keyboard.r=false",
                    "bind:key.use=true",
                    "bind:key.use=false",
                    "key:key.keyboard.h=true",
                    "key:key.keyboard.h=false"), bridge.calls,
                    "injection order: " + bridge.calls);
        } finally {
            delayer.shutdownNow();
        }
    }

    @Test
    void registrationsReturnSubscriptionsThatCancel() {
        RecordingBridge bridge = new RecordingBridge();
        InputApi api = new InputApi(bridge);

        InputApi.Subscription keybind =
                (InputApi.Subscription) api.registerKeybind("key.keyboard.k", () -> {
                });
        keybind.cancel();
        assertEquals(List.of("register:key.keyboard.k", "unregister:kb-handle"),
                bridge.calls, "subscription cancel unregisters the bridge handle");

        InputApi.Subscription press =
                (InputApi.Subscription) api.registerKeyPress("key.keyboard.p", () -> {
                });
        assertTrue(press instanceof InputApi.Subscription);
        press.cancel();
        assertEquals("unregister:kp-handle",
                bridge.calls.get(bridge.calls.size() - 1));
    }

    @Test
    void legacyHandlesStillRouteToUnregister() {
        RecordingBridge bridge = new RecordingBridge();
        InputApi api = new InputApi(bridge);
        api.unregister("opaque-handle");
        assertEquals(List.of("unregister:opaque-handle"), bridge.calls,
                "opaque handles keep flowing through to the bridge");
    }

    /**
     * G-028B: the PlayerInput movement/action fields read back through
     * {@code movementState} and hold through {@code movement}; a held bind
     * surfaces in {@code pressedKeys} (the KeyTracker read face).
     */
    @Test
    void movementHoldDrivesMovementStateAndPressedKeys() {
        RecordingBridge bridge = new RecordingBridge();
        InputApi api = new InputApi(bridge);

        InputApi.Subscription sub = api.movement(java.util.Map.of(
                "forward", true, "jump", true, "sneak", true, "sprint", true));

        java.util.Map<String, Boolean> state = api.getMovementState();
        assertEquals(7, state.size(),
                "movement state covers forward/backward/left/right/jump/sneak/sprint");
        assertEquals(Boolean.TRUE, state.get("forward"), "movementForward > 0 reads back");
        assertEquals(Boolean.FALSE, state.get("backward"), "no backward hold");
        assertEquals(Boolean.TRUE, state.get("jump"), "jumping reads back");
        assertEquals(Boolean.TRUE, state.get("sneak"), "sneaking reads back");
        assertEquals(Boolean.TRUE, state.get("sprint"), "sprinting reads back");
        assertEquals(List.of("key.forward"), api.getPressedKeys(),
                "a held bind surfaces in pressedKeys");

        sub.cancel();
        assertEquals(Boolean.FALSE, api.getMovementState().get("forward"),
                "cancelling the subscription releases the hold");
        assertTrue(api.getPressedKeys().isEmpty(),
                "released binds drop out of pressedKeys");
    }

    @Test
    void movementRejectsUnknownKeys() {
        RecordingBridge bridge = new RecordingBridge();
        InputApi api = new InputApi(bridge);
        assertThrows(IllegalArgumentException.class,
                () -> api.movement(java.util.Map.of("up", true)),
                "unknown movement keys are rejected");
    }
}
