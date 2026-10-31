package ink.tuanzi.neomacros.core.api;

/**
 * Marker for live facade handles crossing the engine boundary. Implementing
 * classes are rendered by the engine as guest-readable member proxies (bean
 * getters as properties, zero-argument public methods as functions), since
 * raw host objects are unreadable under the closed host-access policy.
 */
public interface ScriptHandle {
}
