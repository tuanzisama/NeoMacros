package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.DialogPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EffectSnapshot;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-037 sign/panorama faces over a canned player port: the four-line
 * {@code writeSign} keeps {@code null} lines and answers the editor-open
 * state, the single-line {@code writeSignLine} validates its index even
 * without an editor, and {@code takePanorama} resolves with the capture
 * message while its failure semantics stay explicit — a capture failure
 * rejects the promise, an invalid folder throws before the port runs.
 */
class SignPanoramaFaceTest {

    private CannedPlayer port;
    private PlayerApi api;

    @BeforeEach
    void install() {
        port = new CannedPlayer();
        ScriptApiEnvironment.install(null, new PlayerOnlyAdapter(port));
        api = new PlayerApi();
    }

    @AfterEach
    void uninstall() throws Exception {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void fourLineWriteKeepsNullLinesAndAnswersTheEditorState() {
        port.signEditorOpen = false;
        assertFalse(api.writeSign("a", null, "c", null),
                "a closed editor answers false");
        assertArrayEquals(new String[] {null, null, null, null}, port.signLines,
                "a closed editor keeps its buffer");
        assertEquals(List.of("a/-/c/-"), port.writes,
                "the call still reaches the port with its line slots");

        port.signEditorOpen = true;
        assertTrue(api.writeSign("a", null, "c", null), "an open editor answers true");
        assertArrayEquals(new String[] {"a", null, "c", null}, port.signLines,
                "null lines are left unchanged");
        assertEquals(List.of("a/-/c/-", "a/-/c/-"), port.writes);

        // The second write merges into the live buffer line by line.
        assertTrue(api.writeSign(null, "b", null, null));
        assertArrayEquals(new String[] {"a", "b", "c", null}, port.signLines,
                "later writes only replace the non-null lines");
        assertEquals(List.of("a/-/c/-", "a/-/c/-", "-/b/-/-"), port.writes);
    }

    @Test
    void singleLineWriteValidatesTheIndexBeforeAskingAboutTheEditor() {
        port.signEditorOpen = false;
        assertThrows(IndexOutOfBoundsException.class, () -> api.writeSignLine(-1, "x"),
                "the index is checked even with no editor open");
        assertThrows(IndexOutOfBoundsException.class, () -> api.writeSignLine(4, "x"));
        assertThrows(NullPointerException.class, () -> api.writeSignLine(0, null),
                "the single-line form does not accept null");
        assertFalse(api.writeSignLine(2, "two"), "no editor answers false");
        assertArrayEquals(new String[] {null, null, null, null}, port.signLines,
                "a rejected index never reaches the buffer");
        assertEquals(List.of("-/-/two/-"), port.writes);

        port.signEditorOpen = true;
        assertTrue(api.writeSignLine(2, "two"));
        assertArrayEquals(new String[] {null, null, "two", null}, port.signLines,
                "the indexed line is written, the others stay untouched");
        assertEquals(List.of("-/-/two/-", "-/-/two/-"), port.writes);
    }

    @Test
    void takePanoramaResolvesWithTheCaptureMessage() throws Exception {
        port.panoramaMessage = "Saved panorama";
        assertEquals("Saved panorama",
                api.takePanorama("shots").get(1, TimeUnit.SECONDS));
        assertEquals(List.of("shots"), port.captures,
                "the folder reaches the port unchanged");
    }

    @Test
    void takePanoramaRejectsWhenTheCaptureCannotRun() {
        port.panoramaFailure =
                new IllegalStateException("panorama capture requires an in-world player");
        var failed = api.takePanorama("shots");
        assertTrue(failed.isCompletedExceptionally(),
                "a capture failure rejects the promise instead of throwing");
        var thrown = assertThrows(ExecutionException.class,
                () -> failed.get(1, TimeUnit.SECONDS));
        assertEquals("panorama capture requires an in-world player",
                thrown.getCause().getMessage());

        // The folder guard runs before the port, so it fails synchronously
        // and never reaches the capture. The absolute path comes from the
        // platform so the guard is exercised on every host.
        String absolute = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "g037").toString();
        assertThrows(IllegalArgumentException.class, () -> api.takePanorama(null));
        assertThrows(IllegalArgumentException.class, () -> api.takePanorama("  "));
        assertThrows(IllegalArgumentException.class, () -> api.takePanorama(absolute));
        assertThrows(IllegalArgumentException.class, () -> api.takePanorama("../escape"));
        assertThrows(IllegalArgumentException.class,
                () -> api.takePanorama("shots/../../escape"));
        assertEquals(List.of("shots"), port.captures,
                "an invalid folder never reaches the port");
    }

    /** Canned player port: the G-037 write/capture faces plus canned state. */
    private static final class CannedPlayer implements PlayerPort {
        volatile PlayerSnapshot self;
        volatile boolean signEditorOpen;
        volatile String panoramaMessage;
        volatile RuntimeException panoramaFailure;
        final String[] signLines = new String[4];
        final List<String> writes = new CopyOnWriteArrayList<>();
        final List<String> captures = new CopyOnWriteArrayList<>();

        @Override
        public Optional<PlayerSnapshot> self() {
            return Optional.ofNullable(self);
        }

        @Override
        public List<EffectSnapshot> activeEffects() {
            return List.of();
        }

        @Override
        public void onAttackBlock(Function<BlockInteraction, Boolean> sink) {
        }

        @Override
        public void onAttackEntity(Function<EntitySnapshot, Boolean> sink) {
        }

        @Override
        public void onInteractBlock(Function<BlockInteraction, Boolean> sink) {
        }

        @Override
        public void onInteractEntity(Function<EntitySnapshot, Boolean> sink) {
        }

        @Override
        public CompletableFuture<Void> attack(EntitySnapshot target) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> useEntity(EntitySnapshot target, String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> useBlock(BlockInteraction hit, String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> useItem(String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> attackBlock(BlockInteraction hit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> stopBreaking() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> swingHand(String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> dropHeld(boolean all) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean writeSign(List<String> lines) {
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < signLines.length; i++) {
                String line = lines != null && i < lines.size() ? lines.get(i) : null;
                if (signEditorOpen && line != null) {
                    signLines[i] = line;
                }
                if (i > 0) {
                    detail.append('/');
                }
                detail.append(line == null ? "-" : line);
            }
            writes.add(detail.toString());
            return signEditorOpen;
        }

        @Override
        public CompletableFuture<String> takePanorama(String folder) {
            captures.add(folder);
            if (panoramaFailure != null) {
                return CompletableFuture.failedFuture(panoramaFailure);
            }
            return CompletableFuture.completedFuture(
                    panoramaMessage != null ? panoramaMessage : "panorama:" + folder);
        }
    }

    /** Minimal entity port answering nothing (unused by this face). */
    private static final class CannedEntities implements EntityPort {
        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public List<EntitySnapshot> inRadius(ink.tuanzi.neomacros.port.model.Pos3d center,
                double range, java.util.function.Predicate<EntitySnapshot> filter) {
            return List.of();
        }
    }

    /** Adapter exposing only the canned player port; the rest stay unused. */
    private static final class PlayerOnlyAdapter implements NeomacrosAdapter {
        private final CannedPlayer player;

        PlayerOnlyAdapter(CannedPlayer player) {
            this.player = player;
        }

        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityPort entity() {
            return new CannedEntities();
        }

        @Override
        public PlayerPort player() {
            return player;
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
