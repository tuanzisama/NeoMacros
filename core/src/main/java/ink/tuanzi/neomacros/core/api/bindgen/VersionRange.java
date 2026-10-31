package ink.tuanzi.neomacros.core.api.bindgen;

/**
 * Minecraft version range of an {@code @ApiMC} annotation: a comparison
 * ({@code >=1.20.6}, {@code >1.19}, {@code <2.0}) or an inclusive range
 * ({@code 1.18-1.21}). Bindings outside the current node's range are omitted
 * at registry build time.
 */
public final class VersionRange {

    private final String expression;
    private final String lowerInclusive;
    private final String lowerExclusive;
    private final String upperExclusive;
    private final String upperInclusive;

    private VersionRange(String expression, String lowerInclusive, String lowerExclusive,
                         String upperExclusive, String upperInclusive) {
        this.expression = expression;
        this.lowerInclusive = lowerInclusive;
        this.lowerExclusive = lowerExclusive;
        this.upperExclusive = upperExclusive;
        this.upperInclusive = upperInclusive;
    }

    /** Parses an {@code @ApiMC} expression; "*" or blank matches everything. */
    public static VersionRange parse(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) {
            return new VersionRange(trimmed, null, null, null, null);
        }
        if (trimmed.startsWith(">=")) {
            return new VersionRange(trimmed, trimmed.substring(2), null, null, null);
        }
        if (trimmed.startsWith(">")) {
            return new VersionRange(trimmed, null, trimmed.substring(1), null, null);
        }
        if (trimmed.startsWith("<=")) {
            return new VersionRange(trimmed, null, null, null, trimmed.substring(2));
        }
        if (trimmed.startsWith("<")) {
            return new VersionRange(trimmed, null, null, trimmed.substring(1), null);
        }
        int dash = trimmed.indexOf('-');
        if (dash > 0) {
            return new VersionRange(trimmed, trimmed.substring(0, dash), null,
                    null, trimmed.substring(dash + 1));
        }
        return new VersionRange(trimmed, trimmed, null, null, trimmed);
    }

    /** Returns whether the given Minecraft version satisfies the range. */
    public boolean test(String version) {
        if (lowerInclusive != null && compare(version, lowerInclusive) < 0) {
            return false;
        }
        if (lowerExclusive != null && compare(version, lowerExclusive) <= 0) {
            return false;
        }
        if (upperExclusive != null && compare(version, upperExclusive) >= 0) {
            return false;
        }
        return upperInclusive == null || compare(version, upperInclusive) <= 0;
    }

    /** The raw expression as written in the annotation. */
    public String expression() {
        return expression;
    }

    /** Numeric segment comparison of dotted versions ("1.20.6" vs "1.9"). */
    private static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < a.length ? Integer.parseInt(a[i]) : 0;
            int rightPart = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }
}
