package ink.tuanzi.neomacros.fabric.mcp.tools;

import com.google.gson.JsonObject;
import ink.tuanzi.neomacros.fabric.mcp.McpTool;
import ink.tuanzi.neomacros.fabric.mcp.McpToolError;
import ink.tuanzi.neomacros.fabric.web.GameRunner;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.argInt;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.integerProperty;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.objectSchema;

/**
 * The game-state read tools: local player, held/container inventory, the
 * open screen and dialog, and a world probe with nearby entities. Every
 * read dispatches onto the game main thread through the game runner and
 * rejects with a clear message when the client is outside a loaded world.
 */
public final class StateTools {

    private static final int DEFAULT_RADIUS = 16;
    private static final int MAX_RADIUS = 128;
    private static final int DEFAULT_ENTITY_LIMIT = 50;

    private final NeomacrosAdapter adapter;
    private final GameRunner game;

    /** Creates the tools over the adapter ports and the main-thread runner. */
    public StateTools(NeomacrosAdapter adapter, GameRunner game) {
        this.adapter = adapter;
        this.game = game;
    }

    /** The four tools of this group, in registry order. */
    public List<McpTool> tools() {
        return List.of(getPlayer(), getInventory(), getScreen(), getWorld());
    }

    /** Runs a main-thread read after confirming a loaded world. */
    private <T> T readInWorld(Supplier<T> read) {
        return game.call(() -> {
            requireWorld();
            return read.get();
        });
    }

    private void requireWorld() {
        if (!adapter.lifecycle().inWorld().get()) {
            throw new McpToolError(
                    "the game client is not inside a loaded world; join a world first");
        }
    }

    private McpTool getPlayer() {
        JsonObject schema = objectSchema(new JsonObject());
        return new McpTool("get_player",
                "The local player snapshot: identity, position, look angles, health,"
                        + " hunger, air, saturation, movement flags, armor slots, and the"
                        + " active status effects.",
                "state", schema, args -> readInWorld(() -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("player", adapter.player().self().orElse(null));
                    out.put("armor", adapter.player().armorSlots());
                    out.put("effects", adapter.player().activeEffects());
                    return out;
                }));
    }

    private McpTool getInventory() {
        JsonObject schema = objectSchema(new JsonObject());
        return new McpTool("get_inventory",
                "The inventory contents: the held stack, every occupied slot of the"
                        + " player inventory, and the open container (title, kind, size,"
                        + " and its slots) when one is open.",
                "state", schema, args -> readInWorld(() -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("held", adapter.inventory().heldItem().get().orElse(null));
                    out.put("container", adapter.inventory().openedContainer().orElse(null));
                    out.put("slots", adapter.inventory().allItems().get());
                    return out;
                }));
    }

    private McpTool getScreen() {
        JsonObject schema = objectSchema(new JsonObject());
        return new McpTool("get_screen",
                "The currently open screen (class, kind label, title), the open dialog"
                        + " with its actions and inputs when one is showing, and the chat"
                        + " box draft text when the chat screen is open.",
                "state", schema, args -> game.call(() -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("screen", adapter.lifecycle().screenInfo().orElse(null));
                    out.put("dialog", adapter.dialog().currentDialog().orElse(null));
                    out.put("chatText", adapter.lifecycle().chatText().orElse(null));
                    return out;
                }));
    }

    private McpTool getWorld() {
        JsonObject properties = new JsonObject();
        properties.add("radius", integerProperty(
                "Entity search radius in blocks around the player", 1));
        properties.add("limit", integerProperty("Maximum entities returned", 1));
        JsonObject schema = objectSchema(properties);
        return new McpTool("get_world",
                "A world probe: dimension id, time of day, weather, and the entities"
                        + " within the radius around the player (type, name, position,"
                        + " health).",
                "state", schema, args -> readInWorld(() -> {
                    int radius = Math.min(argInt(args, "radius", DEFAULT_RADIUS), MAX_RADIUS);
                    int limit = Math.min(argInt(args, "limit", DEFAULT_ENTITY_LIMIT), 500);
                    Pos3d center = adapter.player().self()
                            .map(ink.tuanzi.neomacros.port.model.PlayerSnapshot::pos)
                            .orElse(new Pos3d(0, 0, 0));
                    List<ink.tuanzi.neomacros.port.model.EntitySnapshot> entities =
                            adapter.entity().inRadius(center, radius, snapshot -> true);
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("dimension", adapter.world().dimension());
                    out.put("time", adapter.world().time());
                    out.put("raining", adapter.world().isRaining());
                    out.put("thundering", adapter.world().isThundering());
                    out.put("entityCount", entities.size());
                    out.put("entities", entities.size() <= limit
                            ? entities : entities.subList(0, limit));
                    return out;
                }));
    }
}
