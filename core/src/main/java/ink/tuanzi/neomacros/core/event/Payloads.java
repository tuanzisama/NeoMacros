package ink.tuanzi.neomacros.core.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep snapshot of event payloads into plain Java data (records become maps,
 * polyglot views flatten, scalars pass through). Snapshots are safe to hand
 * to guest code under any host-access policy and immune to later mutation.
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
