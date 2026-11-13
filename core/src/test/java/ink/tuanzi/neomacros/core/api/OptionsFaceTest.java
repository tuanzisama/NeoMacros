package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.client.ClientApi;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.model.OptionsData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The options root and video faces over a writable canned port: reads render
 * the option vocabulary, writes round-trip through read-back verification,
 * and a write the client does not store rejects.
 */
class OptionsFaceTest {

    private WritableClient client;
    private ClientApi api;

    @BeforeEach
    void install() {
        client = new WritableClient();
        ScriptApiEnvironment.install(null,
                new ClientGapExpansionsTest.SinglePortAdapter(client));
        api = new ClientApi();
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void readsRenderTheOptionVocabulary() {
        ClientApi.Options options = api.getOptions();
        assertEquals(90, options.getFov());
        assertEquals("en_us", options.getLanguage());
        assertEquals(Boolean.FALSE, options.getDifficultyLocked());
        assertEquals(List.of("vanilla"), options.getResourcePacks());
        assertEquals(List.of("vanilla"), options.getEnabledResourcePacks());
        assertEquals(0, options.getCameraMode());
        assertEquals(1920, options.getWidth());
        assertEquals(1080, options.getHeight());

        ClientApi.VideoOptions video = options.getVideo();
        assertEquals(12, video.getRenderDistance());
        assertEquals("fancy", video.getGraphicsMode());
        assertEquals("nearby", video.getPrioritizeChunkUpdates());
        assertEquals(Boolean.TRUE, video.getAmbientOcclusion());
        assertEquals(120, video.getFramerateLimit());
        assertEquals(Boolean.TRUE, video.getEnableVsync());
        assertEquals(Boolean.TRUE, video.getBobView());
        assertEquals("crosshair", video.getAttackIndicator());
        assertEquals(1.0, video.getGamma());
        assertEquals(1.0, video.getBrightness());
        assertEquals(Boolean.FALSE, video.getFullscreen());
        assertEquals("all", video.getParticles());
        assertNull(video.getFullscreenResolution());
        assertEquals(options.getFov(), video.getParent().getFov());

        // The five deferred groups exist and point back at the root.
        assertEquals(options.getFov(), options.getSkin().getParent().getFov());
        assertEquals(options.getFov(), options.getMusic().getParent().getFov());
        assertEquals(options.getFov(), options.getControl().getParent().getFov());
        assertEquals(options.getFov(), options.getChat().getParent().getFov());
        assertEquals(options.getFov(), options.getAccessibility().getParent().getFov());
    }

    @Test
    void writesRoundTripThroughReadBack() throws Exception {
        ClientApi.Options options = api.getOptions();

        options.setFov(70).get(1, TimeUnit.SECONDS);
        assertEquals(70, options.getFov());

        options.getVideo().setRenderDistance(8).get(1, TimeUnit.SECONDS);
        assertEquals(8, options.getVideo().getRenderDistance());

        options.getVideo().setGraphicsMode("fast").get(1, TimeUnit.SECONDS);
        assertEquals("fast", options.getVideo().getGraphicsMode());

        options.getVideo().setEnableVsync(false).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.FALSE, options.getVideo().getEnableVsync());

        options.getVideo().setBrightness(0.75).get(1, TimeUnit.SECONDS);
        assertEquals(0.75, options.getVideo().getGamma());

        options.setLanguage("en_gb").get(1, TimeUnit.SECONDS);
        assertEquals("en_gb", options.getLanguage());

        options.setCameraMode(1).get(1, TimeUnit.SECONDS);
        assertEquals(1, options.getCameraMode());

        options.setSmoothCamera(true).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, options.getSmoothCamera());

        options.setSize(1280, 720).get(1, TimeUnit.SECONDS);
        assertEquals(1280, options.getWidth());
        assertEquals(720, options.getHeight());

        options.setWidth(1024).get(1, TimeUnit.SECONDS);
        assertEquals(1024, options.getWidth());
        assertEquals(720, options.getHeight());

        options.setEnabledResourcePacks(List.of("vanilla", "my-pack"))
                .get(1, TimeUnit.SECONDS);
        assertEquals(List.of("vanilla", "my-pack"), options.getEnabledResourcePacks());

        options.removeServerResourcePack(true).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, client.data().serverPacksDisabled());
        options.removeServerResourcePack(false).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.FALSE, client.data().serverPacksDisabled());

        options.saveOptions().get(1, TimeUnit.SECONDS);
        options.sendSyncedOptions();
        assertTrue(client.actions.contains("save"));
        assertTrue(client.actions.contains("sync"));
    }

    @Test
    void lockDifficultySendsTheRequestWithoutLocalReadBack() throws Exception {
        api.getOptions().lockDifficulty().get(1, TimeUnit.SECONDS);
        assertEquals(List.of("lock:true"), client.actions);
        api.getOptions().unlockDifficulty().get(1, TimeUnit.SECONDS);
        assertEquals(List.of("lock:true", "lock:false"), client.actions);
    }

    @Test
    void writeRejectsWhenTheClientStoresAnotherValue() {
        client.ignore.add("framerateLimit");
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> api.getOptions().getVideo()
                        .setFramerateLimit(999).get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(error.getCause()).contains("was not applied"),
                "message was: " + error.getCause());
        assertEquals(120, api.getOptions().getVideo().getFramerateLimit());
    }

    @Test
    void writeRejectsOnUnknownOptionNames() {
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> client.setOption("nope", 1).get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(error.getCause()).contains("unknown option"));
    }

    @Test
    void writeRejectsWhenTheSelectionDoesNotMatch() {
        // The canned port re-adds the required pack, so the stored set differs.
        client.forceRequiredPack = true;
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> api.getOptions().setEnabledResourcePacks(List.of("my-pack"))
                        .get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(error.getCause()).contains("was not applied"),
                "message was: " + error.getCause());
    }

    @Test
    void absentOptionsReadUndefinedAndWritesReject() {
        client.present = false;
        ClientApi.Options options = api.getOptions();
        assertNull(options.getFov());
        assertNull(options.getLanguage());
        assertNull(options.getDifficultyLocked());
        assertTrue(options.getResourcePacks().isEmpty());
        assertNull(options.getVideo().getRenderDistance());
        assertThrows(ExecutionException.class,
                () -> options.setFov(70).get(1, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class,
                () -> options.saveOptions().get(1, TimeUnit.SECONDS));
        assertFalse(client.actions.contains("save"));
    }

    /** Writable canned client port mirroring the adapter's write contract. */
    static final class WritableClient implements ClientPort {

        private OptionsData data = ClientGapExpansionsTest.cannedOptions();
        boolean present = true;
        boolean forceRequiredPack;
        final java.util.Set<String> ignore = new java.util.HashSet<>();
        final java.util.List<String> actions = new java.util.ArrayList<>();

        OptionsData data() {
            return data;
        }

        @Override
        public int fps() {
            return 60;
        }

        @Override
        public String gameVersion() {
            return "26.3-stub";
        }

        @Override
        public Object minecraftInstance() {
            return null;
        }

        @Override
        public Optional<OptionsData> options() {
            return present ? Optional.of(data) : Optional.empty();
        }

        @Override
        public CompletableFuture<Void> setOption(String name, Object value) {
            if (!present) {
                return failed("game options are unavailable");
            }
            if (ignore.contains(name)) {
                actions.add("option:" + name);
                return CompletableFuture.completedFuture(null);
            }
            try {
                data = data.withOption(name, value);
            } catch (IllegalArgumentException e) {
                return failed(e.getMessage());
            }
            actions.add("option:" + name);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> saveOptions() {
            if (!present) {
                return failed("game options are unavailable");
            }
            actions.add("save");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setEnabledResourcePacks(List<String> enabled) {
            List<String> stored = new java.util.ArrayList<>(enabled);
            if (forceRequiredPack && !stored.contains("vanilla")) {
                stored.add("vanilla");
            }
            data = data.withOption("enabledResourcePacks", stored);
            actions.add("packs:" + stored);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setServerResourcePacksDisabled(boolean disabled) {
            data = data.withOption("serverPacksDisabled", disabled);
            actions.add("serverPacks:" + disabled);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setLanguage(String languageCode) {
            if (languageCode == null || !languageCode.startsWith("en_")) {
                return failed("unknown language: " + languageCode);
            }
            data = data.withOption("language", languageCode);
            actions.add("language:" + languageCode);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setDifficultyLocked(boolean locked) {
            actions.add("lock:" + locked);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setCameraMode(int mode) {
            if (mode < 0 || mode > 2) {
                return failed("camera mode out of range: " + mode);
            }
            data = data.withOption("cameraMode", mode);
            actions.add("camera:" + mode);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setSmoothCamera(boolean enabled) {
            data = data.withOption("smoothCamera", enabled);
            actions.add("smooth:" + enabled);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setWindowSize(int width, int height) {
            if (width <= 0 || height <= 0) {
                return failed("window size must be positive");
            }
            data = data.withOption("width", width).withOption("height", height);
            actions.add("size:" + width + "x" + height);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void sendSyncedOptions() {
            actions.add("sync");
        }

        private static CompletableFuture<Void> failed(String message) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(message));
        }
    }
}
