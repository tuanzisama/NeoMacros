package ink.tuanzi.neomacros.core.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing and comparison for the channel-suffixed version scheme
 * (docs/releasing.md): {@code 0.1.0} (release), {@code 0.1.0-dev.123+abc1234}
 * (dev), {@code 0.1.0-canary.45+def5678} (canary) and {@code 0.1.0-local}
 * (local development builds). Pure string logic living in core so it is
 * unit-testable without a Fabric runtime.
 */
public final class ChannelVersions {

    /** The release channel a build belongs to. */
    public enum Channel { RELEASE, DEV, CANARY, LOCAL }

    /**
     * A parsed version: its channel, the numeric {@code X.Y.Z} base, and for
     * dev/canary builds the CI build number that orders them.
     */
    public record Version(Channel channel, int major, int minor, int patch, int build, String raw) {

        /** The {@code X.Y.Z} string without channel suffix or build metadata. */
        public String base() {
            return major + "." + minor + "." + patch;
        }

        /** Whether this is an unpublishable local development build. */
        public boolean isLocal() {
            return channel == Channel.LOCAL;
        }
    }

    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(dev|canary)\\.(\\d+))?(?:-local)?(?:\\+.+)?$");

    private ChannelVersions() {
    }

    /**
     * Parses a version string; returns {@code null} when it does not follow
     * the scheme, so callers can fall back to the plain baseline.
     */
    public static Version parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }
        String suffix = matcher.group(4);
        Channel channel;
        int build;
        if ("dev".equals(suffix) || "canary".equals(suffix)) {
            channel = "dev".equals(suffix) ? Channel.DEV : Channel.CANARY;
            build = Integer.parseInt(matcher.group(5));
        } else if (raw.contains("-local")) {
            channel = Channel.LOCAL;
            build = 0;
        } else {
            channel = Channel.RELEASE;
            build = 0;
        }
        return new Version(channel,
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                build, raw);
    }

    /**
     * Same-channel ordering: dev/canary compare by CI build number, release
     * by the {@code X.Y.Z} base. Non-negative means {@code a} is at least as
     * new as {@code b}.
     */
    public static int compareSameChannel(Version a, Version b) {
        if (a.channel == Channel.DEV || a.channel == Channel.CANARY) {
            return Integer.compare(a.build, b.build);
        }
        if (a.major != b.major) {
            return Integer.compare(a.major, b.major);
        }
        if (a.minor != b.minor) {
            return Integer.compare(a.minor, b.minor);
        }
        return Integer.compare(a.patch, b.patch);
    }
}
