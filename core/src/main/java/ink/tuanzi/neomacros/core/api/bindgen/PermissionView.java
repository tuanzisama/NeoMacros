package ink.tuanzi.neomacros.core.api.bindgen;

import java.util.Map;
import java.util.Set;

/**
 * The capability view of one script: consulted at binding invocation time to
 * gate {@code @ApiPermission}-annotated members. The trust gate (T-100)
 * supplies the real view from the script's declared permissions; tests and
 * early wiring use the stub implementations here.
 */
public interface PermissionView {

    /** Returns whether the script may call members requiring the capability. */
    boolean allows(String capability);

    /** View granting every capability (trusted context, runtime internals). */
    PermissionView ALLOW_ALL = capability -> true;

    /** View denying every capability. */
    PermissionView DENY_ALL = capability -> false;

    /** Stub view backed by a fixed capability set. */
    static PermissionView of(Set<String> capabilities) {
        return capabilities::contains;
    }

    /** Stub view backed by a per-script capability map keyed by script name. */
    static PermissionView of(Map<String, Set<String>> capabilitiesByScript, String scriptName) {
        Set<String> capabilities = capabilitiesByScript.getOrDefault(scriptName, Set.of());
        return capabilities::contains;
    }
}
