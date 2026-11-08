package ink.tuanzi.neomacros.fabric.update;

import ink.tuanzi.neomacros.core.config.UpdateSettings;
import ink.tuanzi.neomacros.core.update.ChannelVersions;
import ink.tuanzi.neomacros.core.update.GitHubReleases;
import ink.tuanzi.neomacros.core.update.UpdateManifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The update checker: fetches the channel manifest from the self-hosted
 * endpoint (reachable from mainland China) with the GitHub releases API as
 * the fallback, compares the running build's channel, and hands any newer
 * version to the notice consumer. It owns a dedicated single-thread daemon
 * executor so a slow endpoint can never block the shared io executor, and
 * reschedules itself after every cycle reading the live config, so
 * enabled/interval/channel changes take effect without a restart. Local
 * development builds never auto-check; {@link #checkNow()} stays available
 * for the manual command.
 */
public final class UpdateChecker {
    public static final Logger LOGGER = LoggerFactory.getLogger("neomacros-update");

    /** Built-in source chain: the self-hosted manifest first, GitHub second. */
    static final List<String> DEFAULT_SOURCES = List.of(
            "https://neomacros.tuanzi.ink/update/updates.json",
            "https://api.github.com/repos/tuanzisama/NeoMacros/releases?per_page=30");

    /** Delay before the first automatic check, letting the game settle. */
    private static final long FIRST_CHECK_DELAY_MILLIS = 30_000;
    /** Re-evaluation cadence while automatic checks are disabled. */
    private static final long DISABLED_POLL_MILLIS = 300_000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final UpdateSettings options;
    private final String runtimeVersion;
    private final Consumer<UpdateNotice> onNotice;
    private final HttpClient http;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean inFlight = new AtomicBoolean();

    /** The most recent notice of any check, for {@code /neomacros update ignore}. */
    private volatile UpdateNotice lastNotice;

    public UpdateChecker(UpdateSettings options, String runtimeVersion, Consumer<UpdateNotice> onNotice) {
        this.options = options;
        this.runtimeVersion = runtimeVersion;
        this.onNotice = onNotice;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "neomacros-update");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Starts the self-rescheduling check cycle. */
    public void start() {
        scheduleNext(FIRST_CHECK_DELAY_MILLIS);
    }

    /** Stops the checker; the daemon executor dies with the process. */
    public void stop() {
        executor.shutdownNow();
    }

    /** The most recent notice of any check, or null when none was produced yet. */
    public UpdateNotice lastNotice() {
        return lastNotice;
    }

    /** One immediate check (manual command path); completes with null when nothing is new. */
    public CompletableFuture<UpdateNotice> checkNow() {
        return CompletableFuture.supplyAsync(this::check, executor);
    }

    private void scheduleNext(long delayMillis) {
        executor.schedule(this::runScheduled, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void runScheduled() {
        if (executor.isShutdown()) {
            return;
        }
        try {
            if (!options.enabled) {
                scheduleNext(DISABLED_POLL_MILLIS);
                return;
            }
            check();
        } catch (RuntimeException e) {
            LOGGER.debug("update check failed: {}", e.toString());
        }
        scheduleNext(TimeUnit.HOURS.toMillis(Math.max(1, options.intervalHours)));
    }

    private UpdateNotice check() {
        // One probe at a time; a manual check while a scheduled one runs is
        // a no-op returning the shared state.
        if (!inFlight.compareAndSet(false, true)) {
            return lastNotice;
        }
        try {
            Map<String, UpdateManifest.ChannelInfo> channels = fetchChannels();
            if (channels == null) {
                return null;
            }
            UpdateNotice notice = evaluate(channels);
            if (notice != null) {
                lastNotice = notice;
                onNotice.accept(notice);
            }
            return notice;
        } finally {
            inFlight.set(false);
        }
    }

    /** First source whose payload parses into channel data wins; failures stay debug-quiet. */
    Map<String, UpdateManifest.ChannelInfo> fetchChannels() {
        List<String> sources = options.sources.isEmpty() ? DEFAULT_SOURCES : options.sources;
        for (String source : sources) {
            String body = get(source);
            if (body == null) {
                continue;
            }
            Map<String, UpdateManifest.ChannelInfo> parsed = parseSource(body);
            if (parsed != null && !parsed.isEmpty()) {
                return parsed;
            }
            LOGGER.debug("update source returned no usable channels: {}", source);
        }
        return null;
    }

    /** Auto-detects the payload shape: our manifest schema, else the GitHub releases list. */
    private static Map<String, UpdateManifest.ChannelInfo> parseSource(String body) {
        UpdateManifest manifest = UpdateManifest.parse(body);
        if (manifest != null) {
            Map<String, UpdateManifest.ChannelInfo> channels = new LinkedHashMap<>();
            for (String name : List.of("release", "dev", "canary")) {
                UpdateManifest.ChannelInfo info = manifest.channel(name);
                if (info != null) {
                    channels.put(name, info);
                }
            }
            return channels;
        }
        return GitHubReleases.parse(body);
    }

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException | java.io.IOException e) {
            LOGGER.debug("update source unreachable: {} ({})", url, e.toString());
            return null;
        }
    }

    /**
     * Compares the running build against the fetched channels: the target
     * channel (configured, or auto = the build's own channel with local
     * reading as release) must be strictly newer, and prerelease builds
     * additionally surface a newer stable baseline once.
     */
    UpdateNotice evaluate(Map<String, UpdateManifest.ChannelInfo> channels) {
        ChannelVersions.Version own = ChannelVersions.parse(runtimeVersion);
        if (own == null) {
            return null;
        }
        String target = targetChannel(own);
        UpdateManifest.ChannelInfo info = channels.get(target);
        if (info != null) {
            ChannelVersions.Version remote = ChannelVersions.parse(info.version());
            if (remote != null && ChannelVersions.compareSameChannel(remote, own) > 0) {
                return new UpdateNotice(target, own.raw(), info.version(), info.notes(), info.date());
            }
        }
        if (own.channel() == ChannelVersions.Channel.DEV
                || own.channel() == ChannelVersions.Channel.CANARY) {
            UpdateManifest.ChannelInfo stable = channels.get("release");
            if (stable != null) {
                ChannelVersions.Version stableVersion = ChannelVersions.parse(stable.version());
                if (stableVersion != null && ChannelVersions.compareSameChannel(stableVersion, own) > 0) {
                    return new UpdateNotice("release", own.raw(), stable.version(),
                            stable.notes(), stable.date());
                }
            }
        }
        return null;
    }

    private String targetChannel(ChannelVersions.Version own) {
        String configured = options.channel == null ? "auto" : options.channel.strip()
                .toLowerCase(Locale.ROOT);
        if (!"auto".equals(configured)) {
            return configured;
        }
        return own.isLocal() ? "release" : own.channel().name().toLowerCase(Locale.ROOT);
    }
}
