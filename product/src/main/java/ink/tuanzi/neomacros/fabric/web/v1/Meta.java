package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

import java.util.List;

/**
 * The unsigned version-negotiation probe the panel sends first. Contains no
 * sensitive data; hardening (Host, CORS) still applies.
 */
public record Meta(
        @NmDesc("Supported API major versions") List<Integer> api,
        @NmDesc("The mod version") String modVersion,
        @NmDesc("The running Minecraft version") String gameVersion) {
}
