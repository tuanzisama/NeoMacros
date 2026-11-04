package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders neutral snapshots as the plain object shape scripts see. Shared by
 * handle JSON forms and snapshot-returning facade methods so the field names
 * stay identical everywhere.
 */
public final class SnapshotViews {

    private SnapshotViews() {
    }

    /** Renders one entity snapshot, or null for a null input. */
    public static Map<String, Object> entity(EntitySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("uuid", snapshot.uuid().toString());
        view.put("type", snapshot.type());
        view.put("name", snapshot.name());
        view.put("pos", pos(snapshot.pos()));
        view.put("health", snapshot.health());
        return view;
    }

    /** Renders one player snapshot, or null for a null input. */
    public static Map<String, Object> player(PlayerSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("uuid", snapshot.uuid().toString());
        view.put("name", snapshot.name());
        view.put("pos", pos(snapshot.pos()));
        view.put("look", look(snapshot.look()));
        view.put("health", snapshot.health());
        view.put("hunger", snapshot.hunger());
        view.put("air", snapshot.air());
        return view;
    }

    /** Renders one position. */
    public static Map<String, Object> pos(Pos3d pos) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("x", pos.x());
        view.put("y", pos.y());
        view.put("z", pos.z());
        return view;
    }

    /** Renders one look direction. */
    public static Map<String, Object> look(Rot2d look) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("yaw", look.yawDeg());
        view.put("pitch", look.pitchDeg());
        return view;
    }
}
