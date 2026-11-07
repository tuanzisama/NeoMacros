package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Point-in-time copy of the connected server entry.
 *
 * @param name    display label of the server entry
 * @param address host:port the client is connected to
 * @param ping    last measured round-trip latency in milliseconds
 */
public record ServerInfoData(String name, String address, long ping) {

    public ServerInfoData {
        Objects.requireNonNull(address, "address cannot be null");
    }

    /** Convenience constructor without a measured latency. */
    public ServerInfoData(String name, String address) {
        this(name, address, 0);
    }
}
