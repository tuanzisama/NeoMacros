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
 * The options root, video, chat, control, accessibility, music and skin faces
 * over a writable canned port: reads render the option vocabulary, writes
 * round-trip through read-back verification, and a write the client does not
 * store rejects.
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
    void groupReadsRenderTheOptionVocabulary() {
        ClientApi.Options options = api.getOptions();

        ClientApi.ChatOptions chat = options.getChat();
        assertEquals("full", chat.getChatVisibility());
        assertEquals(Boolean.TRUE, chat.getChatColors());
        assertEquals(Boolean.TRUE, chat.getChatLinks());
        assertEquals(Boolean.TRUE, chat.getChatLinksPrompt());
        assertEquals(1.0, chat.getChatOpacity());
        assertEquals(0.5, chat.getTextBackgroundOpacity());
        assertEquals(1.0, chat.getChatScale());
        assertEquals(0.0, chat.getChatLineSpacing());
        assertEquals(0.0, chat.getChatDelay());
        assertEquals(1.0, chat.getChatWidth());
        assertEquals(1.0, chat.getChatHeightFocused());
        assertEquals(0.4436, chat.getChatHeightUnfocused());
        assertEquals("off", chat.getNarrator());
        assertEquals(Boolean.TRUE, chat.getAutoSuggestions());
        assertEquals(Boolean.FALSE, chat.getHideMatchedNames());
        assertEquals(Boolean.FALSE, chat.getReducedDebugInfo());
        assertEquals(options.getFov(), chat.getParent().getFov());

        ClientApi.ControlOptions control = options.getControl();
        assertEquals(0.5, control.getSensitivity());
        assertEquals(Boolean.FALSE, control.getInvertMouseY());
        assertEquals(1.0, control.getMouseWheelSensitivity());
        assertEquals(Boolean.FALSE, control.getDiscreteMouseScroll());
        assertEquals(Boolean.FALSE, control.getTouchscreen());
        assertEquals(Boolean.TRUE, control.getRawMouseInput());
        assertEquals(Boolean.FALSE, control.getAutoJump());
        assertEquals(Boolean.FALSE, control.getToggleCrouch());
        assertEquals(Boolean.FALSE, control.getToggleSprint());
        assertEquals(List.of("key.forward", "key.back", "key.left", "key.right"),
                control.getKeys());
        assertEquals(List.of("key.categories.movement"), control.getCategories());
        assertEquals("W", control.getKeyBindsByCategory()
                .get("key.categories.movement").get("Forward"));
        assertEquals("S",
                control.getKeyBindsByCategory("key.categories.movement").get("Back"));
        assertNull(control.getKeyBindsByCategory("key.categories.inventory"));

        ClientApi.AccessibilityOptions accessibility = options.getAccessibility();
        assertEquals("off", accessibility.getNarrator());
        assertEquals(Boolean.FALSE, accessibility.getShowSubtitles());
        assertEquals(0.5, accessibility.getTextBackgroundOpacity());
        assertEquals(Boolean.FALSE, accessibility.getBackgroundForChatOnly());
        assertEquals(1.0, accessibility.getChatOpacity());
        assertEquals(0.0, accessibility.getChatLineSpacing());
        assertEquals(0.0, accessibility.getChatDelay());
        assertEquals(Boolean.FALSE, accessibility.getAutoJump());
        assertEquals(Boolean.FALSE, accessibility.getToggleCrouch());
        assertEquals(Boolean.FALSE, accessibility.getToggleSprint());
        assertEquals(0.5, accessibility.getDistortionEffect());
        assertEquals(1.0, accessibility.getFovEffect());
        assertEquals(Boolean.FALSE, accessibility.getDarkMojangStudiosBackground());
        assertEquals(Boolean.FALSE, accessibility.getHideLightningFlash());
        assertEquals(options.getFov(), accessibility.getParent().getFov());
    }

    @Test
    void groupWritesRoundTripThroughReadBack() throws Exception {
        ClientApi.Options options = api.getOptions();

        options.getChat().setChatOpacity(0.5).get(1, TimeUnit.SECONDS);
        assertEquals(0.5, options.getChat().getChatOpacity());

        options.getChat().setChatVisibility("hidden").get(1, TimeUnit.SECONDS);
        assertEquals("hidden", options.getChat().getChatVisibility());

        options.getChat().setNarrator("all").get(1, TimeUnit.SECONDS);
        assertEquals("all", options.getChat().getNarrator());

        options.getControl().setSensitivity(0.75).get(1, TimeUnit.SECONDS);
        assertEquals(0.75, options.getControl().getSensitivity());

        options.getControl().setAutoJump(true).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, options.getControl().getAutoJump());

        options.getControl().setInvertMouseY(true).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, options.getControl().getInvertMouseY());

        options.getAccessibility().setShowSubtitles(true).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, options.getAccessibility().getShowSubtitles());

        options.getAccessibility().setTextBackgroundOpacity(0.25).get(1, TimeUnit.SECONDS);
        assertEquals(0.25, options.getAccessibility().getTextBackgroundOpacity());

        options.getAccessibility().setDistortionEffect(0.25).get(1, TimeUnit.SECONDS);
        assertEquals(0.25, options.getAccessibility().getDistortionEffect());

        options.getAccessibility().setDarkMojangStudiosBackground(true)
                .get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE,
                options.getAccessibility().getDarkMojangStudiosBackground());
    }

    @Test
    void groupWriteRejectsWhenTheClientStoresAnotherValue() {
        client.ignore.add("chatOpacity");
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> api.getOptions().getChat()
                        .setChatOpacity(0.25).get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(error.getCause()).contains("was not applied"),
                "message was: " + error.getCause());
        assertEquals(1.0, api.getOptions().getChat().getChatOpacity());
    }

    @Test
    void musicAndSkinReadsRenderTheOptionVocabulary() {
        ClientApi.Options options = api.getOptions();

        ClientApi.MusicOptions music = options.getMusic();
        assertEquals(1.0, music.getMasterVolume());
        assertEquals(0.5, music.getMusicVolume());
        assertEquals(0.75, music.getRecordVolume());
        assertEquals(1.0, music.getWeatherVolume());
        assertEquals(1.0, music.getBlockVolume());
        assertEquals(1.0, music.getHostileVolume());
        assertEquals(1.0, music.getNeutralVolume());
        assertEquals(1.0, music.getPlayerVolume());
        assertEquals(1.0, music.getAmbientVolume());
        assertEquals(1.0, music.getVoiceVolume());
        assertEquals(0.25, music.getUiVolume());
        assertEquals("speakers", music.getSoundDevice());
        assertEquals(List.of("", "speakers", "headphones"), music.getAudioDevices());
        assertEquals(Boolean.FALSE, music.getShowSubtitles());
        java.util.Map<String, Double> volumes = music.getVolumes();
        assertEquals(11, volumes.size());
        assertEquals(0.5, volumes.get("music"));
        assertEquals(0.75, volumes.get("record"));
        assertEquals(0.25, volumes.get("ui"));
        assertEquals(options.getFov(), music.getParent().getFov());

        ClientApi.SkinOptions skin = options.getSkin();
        assertEquals(Boolean.TRUE, skin.getCape());
        assertEquals(Boolean.TRUE, skin.getJacket());
        assertEquals(Boolean.TRUE, skin.getLeftSleeve());
        assertEquals(Boolean.TRUE, skin.getRightSleeve());
        assertEquals(Boolean.TRUE, skin.getLeftPantsLeg());
        assertEquals(Boolean.TRUE, skin.getRightPantsLeg());
        assertEquals(Boolean.TRUE, skin.getHat());
        assertEquals("right", skin.getMainHand());
        assertEquals(Boolean.TRUE, skin.getRightHanded());
        assertEquals(Boolean.FALSE, skin.getLeftHanded());
        assertEquals(options.getFov(), skin.getParent().getFov());
    }

    @Test
    void musicAndSkinWritesRoundTripThroughReadBack() throws Exception {
        ClientApi.Options options = api.getOptions();
        ClientApi.MusicOptions music = options.getMusic();
        ClientApi.SkinOptions skin = options.getSkin();

        music.setMasterVolume(0.75).get(1, TimeUnit.SECONDS);
        assertEquals(0.75, music.getMasterVolume());
        assertEquals(0.75, music.getVolumes().get("master"));

        music.setRecordVolume(0.25).get(1, TimeUnit.SECONDS);
        assertEquals(0.25, music.getRecordVolume());

        music.setUiVolume(0.5).get(1, TimeUnit.SECONDS);
        assertEquals(0.5, music.getUiVolume());

        music.setSoundDevice("headphones").get(1, TimeUnit.SECONDS);
        assertEquals("headphones", music.getSoundDevice());

        music.setShowSubtitles(true).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, music.getShowSubtitles());

        skin.setCape(false).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.FALSE, skin.getCape());

        skin.setLeftPantsLeg(false).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.FALSE, skin.getLeftPantsLeg());

        skin.setMainHand("left").get(1, TimeUnit.SECONDS);
        assertEquals("left", skin.getMainHand());
        assertEquals(Boolean.TRUE, skin.getLeftHanded());
        assertEquals(Boolean.FALSE, skin.getRightHanded());
    }

    @Test
    void musicAndSkinRefusedWritesRejectWithoutStoring() {
        ClientApi.MusicOptions music = api.getOptions().getMusic();
        ClientApi.SkinOptions skin = api.getOptions().getSkin();

        ExecutionException range = assertThrows(ExecutionException.class,
                () -> music.setMasterVolume(1.5).get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(range.getCause()).contains("out of range"),
                "message was: " + range.getCause());
        assertEquals(1.0, music.getMasterVolume());

        ExecutionException device = assertThrows(ExecutionException.class,
                () -> music.setSoundDevice("bluetooth").get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(device.getCause()).contains("unknown audio device"),
                "message was: " + device.getCause());
        assertEquals("speakers", music.getSoundDevice());

        ExecutionException hand = assertThrows(ExecutionException.class,
                () -> skin.setMainHand("middle").get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(hand.getCause()).contains("rejects value"),
                "message was: " + hand.getCause());
        assertEquals("right", skin.getMainHand());
    }

    @Test
    void musicAndSkinWriteRejectsWhenTheClientStoresAnotherValue() {
        client.ignore.add("blockVolume");
        client.ignore.add("hat");
        ExecutionException volume = assertThrows(ExecutionException.class,
                () -> api.getOptions().getMusic()
                        .setBlockVolume(0.25).get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(volume.getCause()).contains("was not applied"),
                "message was: " + volume.getCause());
        assertEquals(1.0, api.getOptions().getMusic().getBlockVolume());

        ExecutionException part = assertThrows(ExecutionException.class,
                () -> api.getOptions().getSkin()
                        .setHat(false).get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(part.getCause()).contains("was not applied"),
                "message was: " + part.getCause());
        assertEquals(Boolean.TRUE, api.getOptions().getSkin().getHat());
    }

    @Test
    void removedOptionWritesSettleOnlyWhenTheStoredValueMatches() throws Exception {
        ClientApi.Options options = api.getOptions();
        // A node without the setting keeps its stored value: a matching write
        // settles and a differing write rejects through read-back.
        client.ignore.add("touchscreen");
        client.ignore.add("rawMouseInput");

        options.getControl().setTouchscreen(false).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.FALSE, options.getControl().getTouchscreen());
        ExecutionException rejected = assertThrows(ExecutionException.class,
                () -> options.getControl().setTouchscreen(true).get(1, TimeUnit.SECONDS));
        assertTrue(String.valueOf(rejected.getCause()).contains("was not applied"),
                "message was: " + rejected.getCause());

        options.getControl().setRawMouseInput(true).get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, options.getControl().getRawMouseInput());
        assertThrows(ExecutionException.class,
                () -> options.getControl().setRawMouseInput(false).get(1, TimeUnit.SECONDS));
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
        assertNull(options.getChat().getChatOpacity());
        assertNull(options.getChat().getChatVisibility());
        assertNull(options.getControl().getSensitivity());
        assertNull(options.getAccessibility().getShowSubtitles());
        assertNull(options.getMusic().getMasterVolume());
        assertNull(options.getMusic().getSoundDevice());
        assertNull(options.getMusic().getShowSubtitles());
        assertTrue(options.getMusic().getAudioDevices().isEmpty());
        assertTrue(options.getMusic().getVolumes().isEmpty());
        assertNull(options.getSkin().getCape());
        assertNull(options.getSkin().getMainHand());
        assertNull(options.getSkin().getRightHanded());
        assertTrue(options.getControl().getKeys().isEmpty());
        assertTrue(options.getControl().getCategories().isEmpty());
        assertTrue(options.getControl().getKeyBindsByCategory().isEmpty());
        assertThrows(ExecutionException.class,
                () -> options.setFov(70).get(1, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class,
                () -> options.getChat().setChatOpacity(0.5).get(1, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class,
                () -> options.getMusic().setMasterVolume(0.5).get(1, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class,
                () -> options.getSkin().setCape(false).get(1, TimeUnit.SECONDS));
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
        public java.util.Optional<ink.tuanzi.neomacros.port.model.KeybindCatalogData> keybindCatalog() {
            return present
                    ? Optional.of(ClientGapExpansionsTest.cannedKeybinds())
                    : java.util.Optional.empty();
        }

        @Override
        public List<String> audioDevices() {
            return present ? List.of("", "speakers", "headphones") : List.of();
        }


        @Override
        public CompletableFuture<Void> setOption(String name, Object value) {
            if (!present) {
                return failed("game options are unavailable");
            }
            String rejection = invalidWrite(name, value);
            if (rejection != null) {
                return failed(rejection);
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

        /** Mirrors the adapter's write validation so refused writes behave alike. */
        private String invalidWrite(String name, Object value) {
            if (name != null && name.endsWith("Volume")) {
                if (!(value instanceof Number number)) {
                    return "option '" + name + "' requires a number";
                }
                double level = number.doubleValue();
                if (!(level >= 0.0 && level <= 1.0)) {
                    return "option '" + name + "' out of range: " + level;
                }
            }
            if ("soundDevice".equals(name) && value instanceof String device
                    && !device.isEmpty() && !audioDevices().contains(device)) {
                return "unknown audio device: " + device;
            }
            if ("mainHand".equals(name) && value instanceof String hand
                    && !"left".equalsIgnoreCase(hand) && !"right".equalsIgnoreCase(hand)) {
                return "option '" + name + "' rejects value " + hand;
            }
            return null;
        }

        private static CompletableFuture<Void> failed(String message) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(message));
        }
    }
}
