package ink.tuanzi.neomacros.core.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter from the GitHub releases API JSON — the fallback update source
 * when the self-hosted manifest is unreachable — to the channel semantics
 * of {@link UpdateManifest}: a {@code v*} tag names the release channel
 * (first non-prerelease hit in the newest-first list wins), while the
 * rolling {@code dev}/{@code canary} tags name the prerelease channels.
 * Per-channel version, build number and date are recovered from the asset
 * file names ({@code neomacros-<mc>-fabric-<version>.jar}).
 */
public final class GitHubReleases {

    private static final Pattern ASSET_PATTERN =
            Pattern.compile("^neomacros-(.+)-fabric-(.+)\\.jar$");
    private static final Pattern RELEASE_TAG = Pattern.compile("^v\\d.+");

    private GitHubReleases() {
    }

    /**
     * Parses a releases-list JSON array into channel → newest entry, the
     * same map shape {@link UpdateManifest} exposes. Returns {@code null}
     * when the payload is unusable.
     */
    public static Map<String, UpdateManifest.ChannelInfo> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonArray()) {
                return null;
            }
            JsonArray releases = element.getAsJsonArray();
            Map<String, UpdateManifest.ChannelInfo> parsed = new LinkedHashMap<>();
            for (JsonElement item : releases) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject release = item.getAsJsonObject();
                String tag = string(release, "tag_name");
                String htmlUrl = string(release, "html_url");
                if (tag == null) {
                    continue;
                }
                boolean prerelease = release.has("prerelease") && release.get("prerelease").isJsonPrimitive()
                        && release.get("prerelease").getAsBoolean();
                String channel = null;
                if ("dev".equals(tag) || "canary".equals(tag)) {
                    channel = tag;
                } else if (!prerelease && RELEASE_TAG.matcher(tag).matches()
                        && !parsed.containsKey("release")) {
                    channel = "release";
                }
                if (channel == null) {
                    continue;
                }
                UpdateManifest.ChannelInfo info = readChannel(release, htmlUrl);
                if (info != null) {
                    parsed.putIfAbsent(channel, info);
                }
            }
            return parsed.isEmpty() ? null : parsed;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Derives the channel entry from the first parseable mod-jar asset. */
    private static UpdateManifest.ChannelInfo readChannel(JsonObject release, String htmlUrl) {
        if (!release.has("assets") || !release.get("assets").isJsonArray()) {
            return null;
        }
        for (JsonElement element : release.getAsJsonArray("assets")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            String name = string(asset, "name");
            String url = string(asset, "browser_download_url");
            if (name == null || url == null) {
                continue;
            }
            Matcher matcher = ASSET_PATTERN.matcher(name);
            if (!matcher.matches()) {
                continue;
            }
            String version = matcher.group(2);
            ChannelVersions.Version parsedVersion = ChannelVersions.parse(version);
            int build = parsedVersion == null ? 0 : parsedVersion.build();
            String sha = string(asset, "digest");
            if (sha != null && sha.startsWith("sha256:")) {
                sha = sha.substring("sha256:".length());
            } else {
                sha = null;
            }
            return new UpdateManifest.ChannelInfo(version, build, string(release, "published_at"),
                    htmlUrl, java.util.List.of(new UpdateManifest.FileEntry(matcher.group(1), url, sha)));
        }
        return null;
    }

    private static String string(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}
