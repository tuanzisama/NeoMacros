package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

/**
 * The save outcome for one written file: the owner macro's structured
 * reload error, or null when it reloaded cleanly (or no macro owns the
 * path).
 */
public record FileSaveResult(
        @NmDesc("The reload error of the owning macro, null when clean") @NmNullable LocatedError error) {
}
