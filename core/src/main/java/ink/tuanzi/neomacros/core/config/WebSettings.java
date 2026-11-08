package ink.tuanzi.neomacros.core.config;

import java.util.List;

/**
 * The {@code core.web} section: the local web panel service. The service is
 * off by default; {@code /neomacros editor} brings it up and persists the
 * intent here. Field defaults are the shipped values.
 */
public final class WebSettings {

    /** Whether the web service should be running (the last start/stop intent). */
    @Option(description = "Web panel service enabled state")
    public boolean enabled = false;

    /** Whether the web service starts with the client, regardless of {@link #enabled}. */
    @Option(description = "Start the web panel service with the client")
    public boolean autoStart = false;

    /** TCP port to bind. */
    @Option(description = "Web panel port", min = 0, max = 65535)
    public int port = 30636;

    /**
     * Lifetime of the deep-link pairing token; it persists across restarts,
     * so a chat link stays clickable for this long.
     */
    @Option(description = "Pairing token lifetime in minutes", min = 1, max = 20160)
    public int pairingTokenTtlMinutes = 1440;

    /** Bind address: {@code 127.0.0.1}, {@code 0.0.0.0}, or {@code ::1}. */
    @Option(description = "Bind address: 127.0.0.1, 0.0.0.0, or ::1")
    public String listen = "127.0.0.1";

    /** Static site the panel is served from; its origin is the default CORS echo. */
    @Option(description = "Panel site origin used for CORS")
    public String panelUrl = "https://neomacro-editor.tuanzi.ink";

    /**
     * Extra origins CORS may echo besides the panel site (mirrors, local
     * dev servers); the entry {@code *} allows any origin.
     */
    @Option(description = "Additional CORS origins beyond the panel site; * allows any origin")
    public List<String> allowedOrigins = List.of("*");

    /**
     * Memory sampling gate for the panel's memory surfaces: {@code auto}
     * samples while a panel is connected (default), {@code always} samples
     * regardless, {@code off} disables sampling entirely.
     */
    @Option(description = "Memory sampling: auto (panel connected), always, off")
    public String memorySampling = "auto";

    /** Memory sampling interval in milliseconds (JVM heap + per-script thread counters). */
    @Option(description = "Memory sampling interval in milliseconds", min = 250, max = 60_000)
    public int memorySampleIntervalMs = 1000;
}
