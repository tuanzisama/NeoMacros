package ink.tuanzi.neomacros.fabric.web.v1;

/** The web service's own status block inside the state snapshot. */
public record ServerInfo(
        int port,
        long uptimeMs,
        String listen) {

    /** The listen scopes. */
    public enum Scope {
        loopback, lan
    }
}
