package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.port.model.BoundingBoxData;
import ink.tuanzi.neomacros.port.model.EnchantmentData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep snapshot of event payloads into neutral Java data (records become
 * maps, neutral models become their script types, scalars pass through).
 * Snapshots are safe to hand to guest code under any host-access policy and
 * immune to later mutation.
 */
public final class Payloads {

    private Payloads() {
    }

    /** Recursively converts {@code value} into neutral plain data. */
    public static Object snapshot(Object value) {
        if (value instanceof java.util.UUID uuid) {
            // Neutral surface: identifiers cross as strings, not host objects.
            return uuid.toString();
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Pos3d point) {
            // Script types keep their class identity across the boundary
            // (the guest receives real Pos3D instances, JSON-safe via toJSON).
            return new Pos3D(point.x(), point.y(), point.z());
        }
        if (value instanceof Rot2d rotation) {
            return new Pos2D(rotation.yawDeg(), rotation.pitchDeg());
        }
        if (value instanceof ItemStackData stack) {
            return ItemStack.of(stack);
        }
        if (value instanceof ink.tuanzi.neomacros.port.model.ChatComponent component) {
            // Received chat crosses as the TextComponent face: the neutral
            // tree maps directly, JSON.stringify renders the vanilla tree.
            return ink.tuanzi.neomacros.core.api.types.TextComponent.from(component);
        }
        if (value instanceof BoundingBoxData box) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("min", snapshot(box.min()));
            view.put("max", snapshot(box.max()));
            return view;
        }
        if (value instanceof EnchantmentData enchantment) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", enchantment.id());
            view.put("level", enchantment.level());
            return view;
        }
        if (value.getClass().isRecord()) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (java.lang.reflect.RecordComponent component :
                    value.getClass().getRecordComponents()) {
                try {
                    component.getAccessor().setAccessible(true);
                    copy.put(component.getName(),
                            snapshot(component.getAccessor().invoke(value)));
                } catch (ReflectiveOperationException ignored) {
                    copy.put(component.getName(), null);
                }
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), snapshot(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(snapshot(element));
            }
            return copy;
        }
        return value;
    }
}
