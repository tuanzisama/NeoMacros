package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.Pos3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Per-tick sliced execution of large block scans: every scheduled scan runs
 * a small time-budgeted slice per client tick on the main thread, so a big
 * volume never stalls a frame. Matched positions accumulate across slices
 * and complete the future handed back to the script.
 */
public final class BlockScanScheduler {

    /** At most this many scans run concurrently; excess submissions fail. */
    private static final int MAX_CONCURRENT = 4;
    /** Main-thread time budget for one scheduler drain (all tasks together). */
    private static final long SLICE_BUDGET_NANOS = 4_000_000L;
    /** Whole-scan wall-clock deadline. */
    private static final long TIMEOUT_MS = 10_000L;
    /** Largest accepted scan radius. */
    private static final int MAX_RANGE = 256;
    /** Largest accepted result count. */
    private static final int MAX_LIMIT = 65_536;
    /** Largest accepted scan volume in blocks. */
    private static final long MAX_VOLUME = 16_000_000L;

    private static final Deque<ScanTask> TASKS = new ConcurrentLinkedDeque<>();

    private BlockScanScheduler() {
    }

    /**
     * Validates the caps and schedules one sliced scan; the returned future
     * completes on the main thread with the matched positions.
     */
    public static CompletableFuture<List<Pos3d>> submit(List<String> blockIds,
            String idPattern, Map<String, Object> stateFilters, Pos3d center,
            int range, int limit, boolean sphere) {
        CompletableFuture<List<Pos3d>> future = new CompletableFuture<>();
        int clampedRange = Math.max(0, Math.min(MAX_RANGE, range));
        int clampedLimit = Math.max(1, Math.min(MAX_LIMIT, limit));
        if (volumeOf(clampedRange) > MAX_VOLUME) {
            future.completeExceptionally(new IllegalArgumentException(
                    "scanBlocks volume exceeds the "
                            + MAX_VOLUME + "-block budget; shrink the range"));
            return future;
        }
        synchronized (TASKS) {
            if (TASKS.size() >= MAX_CONCURRENT) {
                future.completeExceptionally(new IllegalStateException(
                        "scanBlocks already has " + MAX_CONCURRENT
                                + " scans running; await or retry later"));
                return future;
            }
            TASKS.add(new ScanTask(future, blockIds, idPattern, stateFilters,
                    center, clampedRange, clampedLimit, sphere));
        }
        return future;
    }

    /** Drives every pending scan one time-budgeted slice (main thread, per tick). */
    public static void tick() {
        if (TASKS.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + SLICE_BUDGET_NANOS;
        synchronized (TASKS) {
            for (ScanTask task : TASKS) {
                if (System.nanoTime() - deadline >= 0) {
                    break;
                }
                task.advance(deadline);
            }
            TASKS.removeIf(task -> task.done);
        }
    }

    private static long volumeOf(long range) {
        long side = 2 * range + 1;
        return side * side * side;
    }

    /** One running scan: cursor, accumulated hits, and completion future. */
    private static final class ScanTask {
        private final CompletableFuture<List<Pos3d>> future;
        private final List<String> blockIds;
        private final Pattern idPattern;
        private final Map<String, Object> stateFilters;
        private final Pos3d center;
        private final int range;
        private final int limit;
        private final boolean sphere;
        private final long deadlineMs = System.currentTimeMillis() + TIMEOUT_MS;
        private final List<Pos3d> hits = new ArrayList<>();

        private ClientLevel level;
        private int cursorX;
        private int cursorY;
        private int cursorZ;
        private boolean done;

        ScanTask(CompletableFuture<List<Pos3d>> future, List<String> blockIds,
                String idPattern, Map<String, Object> stateFilters, Pos3d center,
                int range, int limit, boolean sphere) {
            this.future = future;
            this.blockIds = blockIds;
            this.idPattern = idPattern != null ? BlockScanMatcher.compileGlob(idPattern) : null;
            this.stateFilters = stateFilters;
            this.center = center;
            this.range = range;
            this.limit = limit;
            this.sphere = sphere;
            this.cursorX = -range;
            this.cursorY = -range;
            this.cursorZ = -range;
        }

        /** Runs one slice bounded by the shared deadline, then completes if finished. */
        void advance(long sliceDeadline) {
            if (done) {
                return;
            }
            if (System.currentTimeMillis() > deadlineMs) {
                done = true;
                future.completeExceptionally(new TimeoutException(
                        "scanBlocks exceeded its " + (TIMEOUT_MS / 1000) + "s budget"));
                return;
            }
            if (level == null) {
                level = Minecraft.getInstance().level;
                if (level == null) {
                    done = true;
                    future.complete(List.of());
                    return;
                }
            } else if (Minecraft.getInstance().level != level) {
                done = true;
                future.completeExceptionally(new IllegalStateException(
                        "scanBlocks aborted: the client world changed mid-scan"));
                return;
            }
            int baseX = (int) Math.floor(center.x());
            int baseY = (int) Math.floor(center.y());
            int baseZ = (int) Math.floor(center.z());
            long squaredRange = (long) range * range;
            while (hits.size() < limit) {
                if (System.nanoTime() - sliceDeadline >= 0) {
                    return;
                }
                if (cursorY > range) {
                    done = true;
                    future.complete(List.copyOf(hits));
                    return;
                }
                long dx = cursorX;
                long dy = cursorY;
                long dz = cursorZ;
                boolean inside = !sphere || dx * dx + dy * dy + dz * dz <= squaredRange;
                if (inside) {
                    BlockPos pos = new BlockPos(baseX + (int) dx, baseY + (int) dy,
                            baseZ + (int) dz);
                    if (matches(level.getBlockState(pos))) {
                        hits.add(new Pos3d(pos.getX(), pos.getY(), pos.getZ()));
                    }
                }
                cursorX++;
                if (cursorX > range) {
                    cursorX = -range;
                    cursorZ++;
                    if (cursorZ > range) {
                        cursorZ = -range;
                        cursorY++;
                    }
                }
            }
            done = true;
            future.complete(List.copyOf(hits));
        }

        /** Whether one block state passes the id/glob and state-property filters. */
        private boolean matches(BlockState state) {
            if (idPattern == null && (blockIds == null || blockIds.isEmpty())) {
                return false;
            }
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (idPattern != null && !idPattern.matcher(id).matches()) {
                return false;
            }
            if (idPattern == null && !blockIds.contains(id)) {
                return false;
            }
            return BlockScanMatcher.stateMatches(state, stateFilters);
        }
    }
}

/** Shared block-scan matching rules: id globs and state-property filters. */
final class BlockScanMatcher {

    private BlockScanMatcher() {
    }

    /** Compiles an id glob ({@code *}/{@code ?} wildcards) into a matcher. */
    static Pattern compileGlob(String pattern) {
        StringBuilder regex = new StringBuilder(pattern.length() * 2);
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' ->
                        regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Whether the state's flattened property map contains every filter
     * entry (stringified comparison).
     */
    static boolean stateMatches(BlockState state, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        Map<String, String> properties = new java.util.LinkedHashMap<>();
        for (var entry : state.getProperties()) {
            properties.put(entry.getName(),
                    String.valueOf(state.getValue(entry)).toLowerCase(Locale.ROOT));
        }
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            String expected = String.valueOf(filter.getValue()).toLowerCase(Locale.ROOT);
            String actual = properties.get(filter.getKey());
            if (actual == null || !actual.equals(expected)) {
                return false;
            }
        }
        return true;
    }
}
