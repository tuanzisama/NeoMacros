package ink.tuanzi.neomacros.port.model;

/**
 * Immutable view rotation: yaw around the vertical axis and pitch, both in
 * degrees following Minecraft's convention (yaw 0 = +Z south; pitch positive
 * = looking down).
 *
 * <p>Neutral value object carried across port signatures and event payloads.</p>
 */
public record Rot2d(float yawDeg, float pitchDeg) {
}
