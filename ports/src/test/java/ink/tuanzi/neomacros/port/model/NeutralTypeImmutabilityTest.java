package ink.tuanzi.neomacros.port.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the immutability contract of the neutral value types: list
 * components are defensively copied and unmodifiable, required components
 * reject null, and equality is pure value semantics.
 */
class NeutralTypeImmutabilityTest {

    @Test
    void chatComponentCopiesChildrenDefensively() {
        List<ChatComponent> children = new ArrayList<>();
        children.add(ChatComponent.plain("child"));
        ChatComponent component = new ChatComponent("text", "red", true, null, null, null, children);

        children.add(ChatComponent.plain("late"));
        assertEquals(1, component.children().size(), "late mutation must not leak in");

        assertThrows(UnsupportedOperationException.class,
                () -> component.children().add(ChatComponent.plain("x")),
                "stored children list must be unmodifiable");
    }

    @Test
    void chatComponentRejectsNullText() {
        assertThrows(NullPointerException.class,
                () -> new ChatComponent(null, null, null, null, null, null, List.of()));
    }

    @Test
    void plainFactoryProducesInheritedStyling() {
        ChatComponent plain = ChatComponent.plain("hello");
        assertEquals("hello", plain.text());
        assertEquals(List.of(), plain.children());
        assertNull(plain.color(), "plain text inherits color");
    }

    @Test
    void entitySnapshotRejectsNullRequiredComponents() {
        UUID uuid = UUID.randomUUID();
        Pos3d pos = new Pos3d(0, 64, 0);
        assertThrows(NullPointerException.class,
                () -> new EntitySnapshot(null, "minecraft:pig", "Pig", pos, 10.0));
        assertThrows(NullPointerException.class,
                () -> new EntitySnapshot(uuid, null, "Pig", pos, 10.0));
        assertThrows(NullPointerException.class,
                () -> new EntitySnapshot(uuid, "minecraft:pig", "Pig", null, 10.0));
    }

    @Test
    void playerSnapshotRejectsNullRequiredComponents() {
        UUID uuid = UUID.randomUUID();
        Pos3d pos = new Pos3d(0, 64, 0);
        Rot2d look = new Rot2d(0, 0);
        assertThrows(NullPointerException.class,
                () -> new PlayerSnapshot(null, "steve", pos, look, 20, 20, 300));
        assertThrows(NullPointerException.class,
                () -> new PlayerSnapshot(uuid, null, pos, look, 20, 20, 300));
        assertThrows(NullPointerException.class,
                () -> new PlayerSnapshot(uuid, "steve", null, look, 20, 20, 300));
        assertThrows(NullPointerException.class,
                () -> new PlayerSnapshot(uuid, "steve", pos, null, 20, 20, 300));
    }

    @Test
    void snapshotsUseValueEquality() {
        UUID uuid = UUID.randomUUID();
        Pos3d pos = new Pos3d(1.5, 64.0, -3.0);
        EntitySnapshot a = new EntitySnapshot(uuid, "minecraft:pig", "Pig", pos, 10.0);
        EntitySnapshot b = new EntitySnapshot(uuid, "minecraft:pig", "Pig", new Pos3d(1.5, 64.0, -3.0), 10.0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new EntitySnapshot(uuid, "minecraft:pig", "Pig", pos, 9.0));
    }

    @Test
    void modelTypesAreRecords() {
        for (Class<?> type : List.of(Pos3d.class, Rot2d.class, EntitySnapshot.class,
                PlayerSnapshot.class, ItemStackData.class, BlockData.class, ChatComponent.class)) {
            assertTrue(type.isRecord(), type.getSimpleName() + " must be a record");
        }
    }
}
