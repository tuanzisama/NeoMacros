package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import java.util.List;

/** The subscribable event catalog (builtin + script-registered custom events). */
public record EventsCatalogResponse(
        @NmDesc("Every registered event with its cancellation support") List<Entry> events) {

    public record Entry(
            @NmDesc("Full event name (domain:action, or bare when unprefixed)") String name,
            @NmDesc("Whether the event supports cancellation") boolean cancellable) {
    }
}
