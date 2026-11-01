package ink.tuanzi.neomacros.fabric.web.http;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * The Host header allowlist — the primary DNS-rebinding defense, applied
 * before every other check. Loopback mode accepts only the loopback names
 * with the bound port; the experimental LAN mode additionally accepts the
 * machine's own interface addresses.
 */
public final class HostPolicy {

    private final int port;
    private final Set<String> allowedHosts;

    private HostPolicy(int port, Set<String> allowedHosts) {
        this.port = port;
        this.allowedHosts = allowedHosts;
    }

    /** The loopback-only policy for the given bound port. */
    public static HostPolicy loopback(int port) {
        Set<String> hosts = new HashSet<>();
        for (String name : new String[] {"127.0.0.1", "localhost", "[::1]", "::1"}) {
            hosts.add(name + ":" + port);
        }
        return new HostPolicy(port, hosts);
    }

    /**
     * The LAN policy: loopback names plus every locally assigned interface
     * address (numerically, and without the port, since clients may omit it).
     */
    public static HostPolicy lan(int port) {
        Set<String> hosts = new HashSet<>(loopback(port).allowedHosts);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                Enumeration<InetAddress> addresses =
                        interfaces.nextElement().getInetAddresses();
                while (addresses.hasMoreElements()) {
                    hosts.add(addresses.nextElement().getHostAddress() + ":" + port);
                }
            }
        } catch (SocketException e) {
            // fall back to loopback-only acceptance
        }
        return new HostPolicy(port, hosts);
    }

    /** Whether the Host header value names this service. */
    public boolean allows(String hostHeader) {
        if (hostHeader == null || hostHeader.isEmpty()) {
            return false;
        }
        if (allowedHosts.contains(hostHeader)) {
            return true;
        }
        // A client may send the default-port form without the port suffix;
        // the host part must still match an allowed entry exactly.
        int colon = hostHeader.lastIndexOf(':');
        if (colon > 0 && isDigits(hostHeader.substring(colon + 1))) {
            return false;
        }
        return allowedHosts.contains(hostHeader + ":" + port);
    }

    private static boolean isDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
