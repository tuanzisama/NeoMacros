package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

/**
 * The poll result of one tokenless pairing request: pending while the
 * in-game decision card is live, then approved (with the client id to
 * store), denied, or expired past the request window.
 */
public record PairApplyStatus(
        @NmDesc("\"pending\", \"approved\", \"denied\", or \"expired\"") String status,
        @NmDesc("The client id once approved") @NmNullable String clientId) {

    /** The shape for an unknown or swept request id. */
    public static PairApplyStatus expired() {
        return new PairApplyStatus("expired", null);
    }
}
