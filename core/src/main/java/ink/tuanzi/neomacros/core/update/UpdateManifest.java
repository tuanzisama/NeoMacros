package ink.tuanzi.neomacros.core.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The update-check manifest contract (docs/releasing.md): a JSON object
 * whose {@code channels} map keys {@code release|dev|canary} to the newest
 * entry of that channel — its version string, CI build number, publish
 * date, notes URL and per-Minecraft-version download files. Parsing is
 * tolerant: unknown keys and missing channels are fine; structurally
 * broken JSON yields {@code null} so the checker can move to the next
 * source.
 */
public final class UpdateManifest {

    /** One downloadable artifact keyed by the Minecraft version it targets. */
    public record FileEntry(String mc, String url, String sha256) {
    }

    /** The newest entry of one channel. */
    public record ChannelInfo(String version, int build, String date, String notes, List<FileEntry> files) {
    }

    private static final List<String> CHANNEL_NAMES = List.of("release", "dev", "canary");

    private final Map<String, ChannelInfo> channels;

    private UpdateManifest(Map<String, ChannelInfo> channels) {
        this.channels = Map.copyOf(channels);
    }

    /** The newest entry of a channel ({@code release|dev|canary}), or null when absent. */
    public ChannelInfo channel(String name) {
        return channels.get(name);
    }

    /** Parses the manifest JSON; returns {@code null} when it is unusable. */
    public static UpdateManifest parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject channelsJson = root.has("channels") && root.get("channels").isJsonObject()
                    ? root.getAsJsonObject("channels")
                    : new JsonObject();
            Map<String, ChannelInfo> parsed = new LinkedHashMap<>();
            for (String name : CHANNEL_NAMES) {
                ChannelInfo info = parseChannel(channelsJson, name);
                if (info != null) {
                    parsed.put(name, info);
                }
            }
            return parsed.isEmpty() ? null : new UpdateManifest(parsed);
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    private static ChannelInfo parseChannel(JsonObject channels, String name) {
        if (!channels.has(name) || !channels.get(name).isJsonObject()) {
            return null;
        }
        JsonObject obj = channels.getAsJsonObject(name);
        String version = string(obj, "version");
        if (version == null || version.isBlank()) {
            return null;
        }
        int build = obj.has("build") && obj.get("build").isJsonPrimitive()
                ? obj.get("build").getAsInt()
                : 0;
        List<FileEntry> files = new java.util.ArrayList<>();
        if (obj.has("files") && obj.get("files").isJsonArray()) {
            for (JsonElement element : obj.getAsJsonArray("files")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject file = element.getAsJsonObject();
                String mc = string(file, "mc");
                String url = string(file, "url");
                if (mc == null || url == null) {
                    continue;
                }
                files.add(new FileEntry(mc, url, string(file, "sha256")));
            }
        }
        return new ChannelInfo(version, build, string(obj, "date"), string(obj, "notes"),
                List.copyOf(files));
    }

    private static String string(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}
