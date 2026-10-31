package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.util.AnnotatedClassScanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of event descriptors. Built-in events are collected from
 * {@code @ApiEvent}-annotated payload records (explicit registration or a
 * package scan); scripts register custom events into the same catalog, marked
 * with {@code custom=true}.
 */
public final class EventCatalog {

    private final Map<String, EventDescriptor> descriptors = new ConcurrentHashMap<>();

    /** Registers one annotated event payload record. */
    public void register(Class<?> eventRecord) {
        EventDescriptor descriptor = EventDescriptor.from(eventRecord, false);
        EventDescriptor existing = descriptors.putIfAbsent(descriptor.name(), descriptor);
        if (existing != null && existing.payloadType() != descriptor.payloadType()) {
            throw new IllegalStateException("event name collision: " + descriptor.name()
                    + " registered for both " + existing.payloadType().getName()
                    + " and " + descriptor.payloadType().getName());
        }
    }

    /** Registers a batch of annotated event payload records. */
    public void registerAll(List<Class<?>> eventRecords) {
        eventRecords.forEach(this::register);
    }

    /** Scans a package for annotated event payload records; returns how many were registered. */
    public int scanPackage(String packageName) {
        List<Class<?>> found = AnnotatedClassScanner.scan(packageName, ink.tuanzi.neomacros.doc.api.ApiEvent.class,
                EventCatalog.class.getClassLoader());
        List<Class<?>> fresh = found.stream()
                .filter(type -> descriptor(EventDescriptor.from(type, false).name()).isEmpty())
                .toList();
        registerAll(fresh);
        return fresh.size();
    }

    /**
     * Registers a custom event owned by a script module. The name must be
     * namespaced ({@code domain:rest}) and must not collide with a built-in
     * descriptor; taking over a built-in domain is rejected so custom events
     * cannot shadow the script event catalog.
     */
    public void registerCustom(String name) {
        EventDescriptor existing = descriptors.get(name);
        if (existing != null) {
            throw new IllegalStateException("event already registered: " + name);
        }
        if (!name.contains(":")) {
            throw new IllegalArgumentException("custom event names must be namespaced (domain:rest): " + name);
        }
        String domain = EventDescriptor.domainOf(name);
        boolean builtinDomain = descriptors.values().stream()
                .anyMatch(d -> !d.custom() && d.domain().equals(domain));
        if (builtinDomain) {
            throw new IllegalArgumentException("custom event may not use the built-in domain '" + domain + "': " + name);
        }
        descriptors.putIfAbsent(name, new EventDescriptor(name, domain, false, null, null, true));
    }

    /** Returns the descriptor of the event, or empty when unknown. */
    public Optional<EventDescriptor> descriptor(String name) {
        return Optional.ofNullable(descriptors.get(name));
    }

    /** Returns all registered descriptors, ordered by name. */
    public Collection<EventDescriptor> all() {
        List<EventDescriptor> out = new ArrayList<>(descriptors.values());
        out.sort(java.util.Comparator.comparing(EventDescriptor::name));
        return List.copyOf(out);
    }

    /** Returns whether the event exists and supports the synchronous channel. */
    public boolean isCancellable(String name) {
        EventDescriptor descriptor = descriptors.get(name);
        return descriptor != null && descriptor.cancellable();
    }
}
