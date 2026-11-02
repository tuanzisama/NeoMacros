package ink.tuanzi.neomacros.core.config;

import java.util.List;

/**
 * The {@code core.web} section: the local web panel service. The service is
 * off by default; {@code /neomacros web start} brings it up and persists the
 * intent here. Field defaults are the shipped values.
 */
public final class WebSettings {

    /** Whether the web service should be running (the last start/stop intent). */
    @Option(description = "Web panel service enabled state")
    public boolean enabled = false;

    /** Whether the web service starts with the client, regardless of {@link #enabled}. */
    @Option(description = "Start the web panel service with the client")
    public boolean autoStart = false;

    /** TCP port to bind; {@code 0} picks a free ephemeral port. */
    @Option(description = "Web panel port, 0 picks a random free port", min = 0, max = 65535)
    public int port = 0;

    /** Interface scope: {@code loopback} (default) or the experimental {@code lan}. */
    @Option(description = "Listen scope: loopback or lan (experimental)")
    public String listen = "loopback";

    /** Static site the panel is served from; its origin is the default CORS echo. */
    @Option(description = "Panel site origin used for CORS")
    public String panelUrl = "https://panel.neomacros.app";

    /** Extra origins CORS may echo besides the panel site (mirrors, local dev servers). */
    @Option(description = "Additional CORS origins beyond the panel site")
    public List<String> allowedOrigins = List.of();
}
