package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

/**
 * The answer to a tokenless pairing request: either immediately paired
 * (loopback trust is on) or pending the in-game decision, in which case
 * the request id is the poll handle.
 */
public record PairApplyResponse(
        @NmDesc("\"paired\" or \"pending\"") String status,
        @NmDesc("The poll handle when pending") @NmNullable String requestId,
        @NmDesc("The client id when paired") @NmNullable String clientId,
        @NmDesc("Seconds the pending request stays valid") @NmNullable Integer expiresIn) {

    /** The instant-approval shape (loopback trust path). */
    public static PairApplyResponse paired(String clientId) {
        return new PairApplyResponse("paired", null, clientId, null);
    }

    /** The waiting-for-the-in-game-decision shape. */
    public static PairApplyResponse pending(String requestId, int expiresIn) {
        return new PairApplyResponse("pending", requestId, null, expiresIn);
    }
}
