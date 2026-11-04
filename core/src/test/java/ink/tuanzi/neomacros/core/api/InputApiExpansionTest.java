package ink.tuanzi.neomacros.core.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The input expansion: injection (press/release/hold/set) and the unified
 * subscription shape for registrations.
 */
class InputApiExpansionTest {

    /** Recording bridge: captures every call in order. */
    static final class RecordingBridge implements InputApi.InputBridge {
        final List<String> calls = new ArrayList<>();

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
}
