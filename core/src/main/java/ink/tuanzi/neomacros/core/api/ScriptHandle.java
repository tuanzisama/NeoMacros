package ink.tuanzi.neomacros.core.api;

/**
 * Marker for live facade handles crossing the engine boundary. Classes
 * carrying {@code @ApiClass} render as instances of a real guest ES class
 * ({@code instanceof} and user subclassing work natively); un-annotated
 * implementations render as flat member proxies (bean getters as properties,
 * zero-argument public methods as functions), since raw host objects are
 * unreadable under the closed host-access policy.
 */
public interface ScriptHandle {
}
