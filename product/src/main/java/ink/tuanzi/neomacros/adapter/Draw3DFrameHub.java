package ink.tuanzi.neomacros.adapter;

/**
 * Bridges the level renderer gizmo mixin to the active 3D draw port. Vanilla
 * collects gizmos into a fresh per-frame collector, so the port re-adds its
 * persistent gizmo from inside every collection scope instead of registering
 * it once at client init, where no collector exists yet.
 */
public final class Draw3DFrameHub {

    private static volatile FabricDraw3DPort active;

    private Draw3DFrameHub() {
    }

    public static void register(FabricDraw3DPort port) {
        active = port;
    }

    public static void addFrameGizmo() {
        FabricDraw3DPort port = active;
        if (port != null) {
            port.addFrameGizmo();
        }
    }
}
