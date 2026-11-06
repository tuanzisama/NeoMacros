package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Point-in-time copy of the connected server entry.
 *
 * @param name    display label of the server entry
 * @param address host:port the client is connected to
 */
public record ServerInfoData(String name, String address) {

    public ServerInfoData {
        Objects.requireNonNull(address, "address cannot be null");
    }
}
