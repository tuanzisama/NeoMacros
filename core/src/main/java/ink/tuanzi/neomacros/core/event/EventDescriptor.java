package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Immutable metadata of one script event: namespaced name, derived domain,
 * cancellation support, payload type, and catalog origin.
 *
 * <p>Descriptors are produced from {@code @ApiEvent}-annotated payload records
 * (built-in events) or created for script-registered custom events; wildcard
 * derivation, sync-channel legality checks, and the documentation pipeline all
 * consume this one record.</p>
 */
public record EventDescriptor(String name, String domain, boolean cancellable,
                              Class<?> payloadType, String since, boolean custom) {

    /** Builds the descriptor of an annotated event payload record. */
    public static EventDescriptor from(Class<?> eventRecord, boolean custom) {
        ApiEvent event = eventRecord.getAnnotation(ApiEvent.class);
        if (event == null) {
            throw new IllegalArgumentException(
                    eventRecord.getName() + " is not annotated with @ApiEvent");
        }
        ApiSince since = eventRecord.getAnnotation(ApiSince.class);
        return new EventDescriptor(event.name(), domainOf(event.name()), event.cancellable(),
                eventRecord, since == null ? null : since.value(), custom);
    }

    /** Returns the domain part of the name: the prefix before the colon, or the name itself. */
    public static String domainOf(String name) {
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(0, colon);
    }
}
