package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.types.Component;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.core.api.types.TextComponent;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.EntityHoverData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.TextSegmentData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-017B position/text/math face: lexicographic point ordering at the
 * numeric boundaries, the immutable DATA contract of the position and text
 * records, the text style readback, the show-entity hover capture, and the
 * generic click/style builder members.
 */
class PosTextMathGapTest {

    private static final UUID PIG = UUID.randomUUID();
    private static final UUID GONE = UUID.randomUUID();

    private CannedEntity entities;

    @BeforeEach
    void install() {
        entities = new CannedEntity();
        ScriptApiEnvironment.install(null, new SinglePortAdapter(entities));
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    // ---------------------------------------------------------------- math

    @Test
    void compareOrdersByXThenY() {
        assertTrue(new Pos2D(1, 9).compare(new Pos2D(2, -9)) < 0, "x decides first");
        assertTrue(new Pos2D(2, -9).compare(new Pos2D(1, 9)) > 0);
        assertTrue(new Pos2D(1, 2).compare(new Pos2D(1, 3)) < 0, "y breaks the x tie");
        assertEquals(0, new Pos2D(1, 2).compare(new Pos2D(1, 2)));
    }

    @Test
    void compareIsAntisymmetricAndReflexive() {
        Pos2D[] points = {
                new Pos2D(0, 0), new Pos2D(-1, 5), new Pos2D(1, -5),
                new Pos2D(0, -0.0), new Pos2D(3, 3), new Pos2D(-7, -7),
        };
        for (Pos2D a : points) {
            assertEquals(0, a.compare(a), "a value always compares equal to itself");
            for (Pos2D b : points) {
                int ab = Integer.signum(a.compare(b));
                int ba = Integer.signum(b.compare(a));
                assertEquals(-ab, ba, "compare must be antisymmetric for " + a + " / " + b);
            }
        }
        // Transitivity over the sample set.
        for (Pos2D a : points) {
            for (Pos2D b : points) {
                for (Pos2D c : points) {
                    if (a.compare(b) <= 0 && b.compare(c) <= 0) {
                        assertTrue(a.compare(c) <= 0,
                                "transitivity broken for " + a + " / " + b + " / " + c);
                    }
                }
            }
        }
    }

    @Test
    void compareAgreesWithEqualsAndNumericEdges() {
        assertEquals(0, new Pos2D(0.1 + 0.2, 1).compare(new Pos2D(0.1 + 0.2, 1)));
        // Double.compare orders -0.0 below 0.0 and NaN above every number.
        assertTrue(new Pos2D(-0.0, 0).compare(new Pos2D(0.0, 0)) < 0);
        assertTrue(new Pos2D(Double.NaN, 0).compare(new Pos2D(Double.MAX_VALUE, 0)) > 0);
        assertTrue(new Pos2D(1, Double.NaN).compare(new Pos2D(1, Double.MAX_VALUE)) > 0);
        assertEquals(0, new Pos2D(Double.NaN, Double.NaN)
                .compare(new Pos2D(Double.NaN, Double.NaN)), "NaN equals NaN here");
        // Equality and the ordering agree on the same bit pattern.
        Pos2D left = new Pos2D(2, 4);
        Pos2D right = new Pos2D(2, 4);
        assertTrue(left.equals(right));
        assertEquals(0, left.compare(right));
    }

    @Test
    void positionDataIsImmutable() throws Exception {
        for (Class<?> type : List.of(Pos2D.class, Pos3D.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        type.getSimpleName() + "." + field.getName() + " must be final");
            }
        }
        Pos3D base = new Pos3D(1, 2, 3);
        assertNotSame(base, base.add(new Pos3D(0, 0, 0)));
        assertNotSame(base, base.withX(9));
        assertEquals(1, base.getX(), "operations must not mutate the source");
        assertEquals(3, base.getZ());
        assertEquals(0, new Pos2D(0, 0).compare(Pos2D.ZERO));
    }

    // ------------------------------------------------------- port records

    @Test
    void portRecordsAreImmutableAndNullSafe() {
        EntityHoverData hover = new EntityHoverData(
                PIG.toString(), "minecraft:pig", "Pig");
        assertEquals("minecraft:pig", hover.type());
        assertThrows(NullPointerException.class,
                () -> new EntityHoverData(null, "minecraft:pig", "Pig"));
        assertThrows(NullPointerException.class,
                () -> new EntityHoverData(PIG.toString(), null, "Pig"));
        assertThrows(NullPointerException.class,
                () -> new EntityHoverData(PIG.toString(), "minecraft:pig", null));

        TextSegmentData legacy = new TextSegmentData("hi", "red", null, null,
                null, null, null, null, null, null, null);
        assertNull(legacy.hoverEntity(), "the pre-entity constructor keeps working");
        assertThrows(NullPointerException.class, () -> new TextSegmentData(
                null, null, null, null, null, null, null, null, null, null, null, null));
        TextSegmentData full = new TextSegmentData("hi", null, null, null,
                null, null, null, null, null, null, null, hover);
        assertEquals(hover, full.hoverEntity());
    }

    // -------------------------------------------------------------- text

    @Test
    void styleReadsBackTheOwnValues() {
        TextComponent node = new Component("tip")
                .color("red")
                .clickCommand("/home")
                .hover("hover me")
                .build();
        assertEquals("red", node.getColor());
        assertEquals("/home", node.getClickValue());
        assertEquals("show_text", node.getHoverAction());
        assertInstanceOf(TextComponent.class, node.getHoverValue());
        assertEquals("hover me", ((TextComponent) node.getHoverValue()).getPlain());

        TextComponent plain = new Component("plain").build();
        assertNull(plain.getColor());
        assertNull(plain.getClickValue());
        assertNull(plain.getHoverAction());
        assertNull(plain.getHoverValue());
    }

    @Test
    void styleReadsStayOnTheOwnNodeWhileChildrenInherit() {
        TextComponent built = new Component("root")
                .color("red")
                .hover("tip")
                .append(new Component("child"))
                .build();
        TextComponent child = built.getChildren().get(0);
        assertNull(child.getColor(), "the child owns no color");
        assertNull(child.getHoverAction(), "the child owns no hover");
        TextSegmentData flatChild = built.flatten().get(1);
        assertEquals("red", flatChild.color(), "inheritance still resolves at flatten time");
        assertEquals("tip", flatChild.hoverText());
    }

    @Test
    void hoverActionCoversTheThreeShapes() {
        TextComponent byItem = new Component("x").hoverItem("diamond").build();
        assertEquals("show_item", byItem.getHoverAction());
        ItemStack stack = assertInstanceOf(ItemStack.class, byItem.getHoverValue());
        assertEquals("minecraft:diamond", stack.getId());

        TextComponent byEntity = new Component("x")
                .hoverEntity(new EntityApi.Entity(PIG))
                .build();
        assertEquals("show_entity", byEntity.getHoverAction());
        EntityApi.Entity hovered = assertInstanceOf(EntityApi.Entity.class,
                byEntity.getHoverValue());
        assertEquals(PIG.toString(), hovered.getUuid());
        assertEquals("minecraft:pig", hovered.getType());
    }

    @Test
    void hoverKindsReplaceEachOther() {
        TextComponent built = new Component("x")
                .hover("text")
                .hoverEntity(new EntityApi.Entity(PIG))
                .hoverItem("diamond")
                .build();
        TextSegmentData segment = built.flatten().get(0);
        assertNull(segment.hoverText(), "the latest hover kind wins");
        assertNull(segment.hoverEntity());
        assertEquals("minecraft:diamond", segment.hoverItem().id());
        assertEquals("show_item", built.getHoverAction());
    }

    @Test
    void hoverEntityNeedsAPresentEntity() {
        assertThrows(IllegalArgumentException.class,
                () -> new Component("x").hoverEntity(null));
        assertThrows(IllegalArgumentException.class,
                () -> new Component("x").hoverEntity(new EntityApi.Entity(GONE)),
                "an entity the world no longer knows cannot be captured");
        TextComponent built = new Component("x").hoverEntity(new EntityApi.Entity(PIG)).build();
        EntityHoverData captured = built.flatten().get(0).hoverEntity();
        assertEquals(PIG.toString(), captured.uuid());
        assertEquals("minecraft:pig", captured.type());
        assertEquals("Pig", captured.name());
    }

    @Test
    void entityHoverRidesTheTreeAndTheJsonFace() {
        TextComponent built = new Component("root")
                .hoverEntity(new EntityApi.Entity(PIG))
                .append(new Component("child"))
                .build();
        assertNull(built.getChildren().get(0).getHoverAction(),
                "the child owns no hover; inheritance resolves at flatten time");
        assertEquals(PIG.toString(), built.flatten().get(1).hoverEntity().uuid());

        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) ink.tuanzi.neomacros.core.util.JsonLite
                .parse(built.toJSON());
        @SuppressWarnings("unchecked")
        Map<String, Object> hover = (Map<String, Object>) view.get("hoverEvent");
        assertEquals("show_entity", hover.get("action"));
        @SuppressWarnings("unchecked")
        Map<String, Object> contents = (Map<String, Object>) hover.get("contents");
        assertEquals("minecraft:pig", contents.get("type"));
        assertEquals(PIG.toString(), contents.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> name = (Map<String, Object>) contents.get("name");
        assertEquals("Pig", name.get("text"));
    }

    @Test
    void clickAcceptsTheVanillaVocabularyAndValidates() {
        assertEquals("open_url",
                new Component("x").click("Open_URL", "https://example.com").build()
                        .flatten().get(0).clickAction());
        assertEquals("3", new Component("x").click("change_page", "3").build()
                .flatten().get(0).clickValue());
        assertEquals("/home",
                new Component("x").clickCommand("/home").build().flatten().get(0).clickValue());

        Component draft = new Component("x").clickUrl("https://example.com");
        assertThrows(IllegalArgumentException.class, () -> draft.click("custom", "x"));
        assertThrows(IllegalArgumentException.class, () -> draft.click("show_dialog", "x"));
        assertThrows(IllegalArgumentException.class, () -> draft.click("change_page", "last"));
        assertThrows(IllegalArgumentException.class, () -> draft.click("run_command", null));
        assertThrows(IllegalArgumentException.class, () -> draft.click("", "/home"));
        TextSegmentData kept = draft.build().flatten().get(0);
        assertEquals("open_url", kept.clickAction(),
                "a rejected call leaves the draft untouched");
        assertEquals("https://example.com", kept.clickValue());
    }

    @Test
    void styleReplacesTheWholeStyleButKeepsContentAndChildren() {
        TextComponent source = new Component("donor")
                .color("red")
                .bold(true)
                .hover("tip")
                .clickUrl("https://example.com")
                .build();
        TextComponent built = new Component("target")
                .color("blue")
                .italic(true)
                .append(new Component("kept"))
                .style(source)
                .build();

        TextSegmentData root = built.flatten().get(0);
        assertEquals("red", root.color(), "the donor color replaces the earlier blue");
        assertEquals(Boolean.TRUE, root.bold());
        assertNull(root.italic(), "an unset decoration in the donor clears the earlier one");
        assertEquals("tip", root.hoverText());
        assertEquals("open_url", root.clickAction());
        assertEquals("target", built.getContent(), "content is not part of the style");
        assertEquals(1, built.getChildren().size(), "children are not part of the style");
        assertEquals("kept", built.getChildren().get(0).getContent());

        assertThrows(IllegalArgumentException.class,
                () -> new Component("x").style(null));
    }

    // ------------------------------------------------------- canned ports

    /** Entity port serving one pig snapshot. */
    static final class CannedEntity implements EntityPort {
        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return PIG.equals(uuid)
                    ? Optional.of(new EntitySnapshot(uuid, "minecraft:pig", "Pig",
                            new Pos3d(0, 64, 0), 5.5))
                    : Optional.empty();
        }

        @Override
        public List<EntitySnapshot> inRadius(Pos3d center, double range,
                java.util.function.Predicate<EntitySnapshot> filter) {
            return List.of();
        }

        @Override
        public Map<String, Object> entityState(UUID uuid) {
            return Map.of();
        }
    }

    /** Adapter that only serves the entity port; the rest is unused here. */
    record SinglePortAdapter(EntityPort entity) implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlayerPort player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ink.tuanzi.neomacros.port.DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }
    }
}
