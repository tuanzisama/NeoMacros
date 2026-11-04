package ink.tuanzi.neomacros.port.model;

/**
 * One raw keyboard edge: the vanilla key translation key, the GLFW key code,
 * and the direction. Delivered from the key-state hook on true transitions.
 *
 * @param keyName vanilla key translation key, e.g. {@code key.keyboard.r}
 * @param keyCode GLFW key code
 * @param down    true on the down edge, false on release
 */
public record KeyPressEdge(String keyName, int keyCode, boolean down) {
}
