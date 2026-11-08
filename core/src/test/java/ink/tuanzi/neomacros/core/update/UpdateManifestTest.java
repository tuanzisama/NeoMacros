package ink.tuanzi.neomacros.core.update;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The update manifest contract: tolerant parsing and the GitHub fallback adapter. */
class UpdateManifestTest {

    @Test
    void parsesChannelsAndFiles() {
        UpdateManifest manifest = UpdateManifest.parse("""
                {
                  "schema": 1,
                  "updated": "2026-11-08T03:04:05Z",
                  "channels": {
                    "release": {
                      "version": "0.1.0",
                      "build": 0,
                      "date": "2026-11-08T03:00:00Z",
                      "notes": "https://example.com/releases/v0.1.0",
                      "files": [
                        {"mc": "26.3", "url": "https://example.com/a.jar", "sha256": "aa"},
                        {"mc": "26.2", "url": "https://example.com/b.jar", "sha256": "bb"}
                      ]
                    },
                    "dev": {
                      "version": "0.1.0-dev.123+abc1234",
                      "build": 123,
                      "date": "2026-11-08T01:00:00Z",
                      "notes": "https://example.com/tags/dev",
                      "files": [{"mc": "26.3", "url": "https://example.com/c.jar"}]
                    }
                  }
                }
                """);
        assertEquals("0.1.0", manifest.channel("release").version());
        assertEquals(2, manifest.channel("release").files().size());
        assertEquals("https://example.com/a.jar", manifest.channel("release").files().get(0).url());
        assertEquals("0.1.0-dev.123+abc1234", manifest.channel("dev").version());
        assertEquals(123, manifest.channel("dev").build());
        assertNull(manifest.channel("canary"), "absent channels read as no data");
    }

    @Test
    void toleratesBrokenAndSparseInput() {
        assertNull(UpdateManifest.parse(null));
        assertNull(UpdateManifest.parse(""));
        assertNull(UpdateManifest.parse("{not json"));
        assertNull(UpdateManifest.parse("{\"channels\":{}}"));
        assertNull(UpdateManifest.parse("{\"channels\":{\"release\":{\"date\":\"x\"}}}"),
                "channel without version is dropped, empty manifest is null");

        UpdateManifest sparse = UpdateManifest.parse(
                "{\"unknownKey\":1,\"channels\":{\"canary\":{\"version\":\"0.1.0-canary.9+abc\"}}}");
        assertEquals("0.1.0-canary.9+abc", sparse.channel("canary").version());
        assertEquals(0, sparse.channel("canary").build());
        assertEquals(List.of(), sparse.channel("canary").files());
    }

    @Test
    void githubAdapterMapsTagsAndAssets() {
        Map<String, UpdateManifest.ChannelInfo> channels = GitHubReleases.parse("""
                [
                  {
                    "tag_name": "dev",
                    "prerelease": true,
                    "html_url": "https://github.com/tuanzisama/NeoMacros/releases/tag/dev",
                    "published_at": "2026-11-08T01:00:00Z",
                    "assets": [
                      {"name": "SHA256SUMS", "browser_download_url": "https://github.com/x/sums", "digest": null},
                      {"name": "neomacros-26.3-fabric-0.1.0-dev.200+feed123.jar",
                       "browser_download_url": "https://github.com/x/dev.jar",
                       "digest": "sha256:abc"}
                    ]
                  },
                  {
                    "tag_name": "v0.1.0",
                    "prerelease": false,
                    "html_url": "https://github.com/tuanzisama/NeoMacros/releases/tag/v0.1.0",
                    "published_at": "2026-11-01T00:00:00Z",
                    "assets": [
                      {"name": "neomacros-26.3-fabric-0.1.0.jar",
                       "browser_download_url": "https://github.com/x/rel.jar",
                       "digest": "sha256:def"}
                    ]
                  }
                ]
                """);
        assertEquals("0.1.0-dev.200+feed123", channels.get("dev").version());
        assertEquals(200, channels.get("dev").build());
        assertEquals("https://github.com/tuanzisama/NeoMacros/releases/tag/dev", channels.get("dev").notes());
        assertEquals("abc", channels.get("dev").files().get(0).sha256(), "digest prefix stripped");
        assertEquals("26.3", channels.get("dev").files().get(0).mc());

        assertEquals("0.1.0", channels.get("release").version());
        assertEquals("def", channels.get("release").files().get(0).sha256());
        assertNull(channels.get("canary"));
    }

    @Test
    void githubAdapterKeepsNewestReleaseAndToleratesGarbage() {
        // Newest-first list: the first v* non-prerelease entry must win even
        // when an older release follows.
        Map<String, UpdateManifest.ChannelInfo> channels = GitHubReleases.parse("""
                [
                  {"tag_name": "v0.2.0", "prerelease": false, "html_url": "n2",
                   "assets": [{"name": "neomacros-26.3-fabric-0.2.0.jar", "browser_download_url": "u2"}]},
                  {"tag_name": "v0.1.0", "prerelease": false, "html_url": "n1",
                   "assets": [{"name": "neomacros-26.3-fabric-0.1.0.jar", "browser_download_url": "u1"}]}
                ]
                """);
        assertEquals("0.2.0", channels.get("release").version());

        assertNull(GitHubReleases.parse(null));
        assertNull(GitHubReleases.parse("{}"));
        assertNull(GitHubReleases.parse("[]"));
        assertTrue(GitHubReleases.parse("[{\"tag_name\":\"other\",\"assets\":[]}]") == null);
    }
}
