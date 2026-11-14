package ink.tuanzi.neomacros.port.model;

/**
 * Outcome of a client-side block-break attempt reported through the break
 * override: a diagnostic {@code reason} code and the position it applies to.
 *
 * <p>Reason codes: {@code SUCCESS}, {@code CANCELLED}, {@code RESET},
 * {@code NO_TARGET}, {@code TARGET_LOST}, {@code TARGET_CHANGE},
 * {@code IS_AIR}, {@code NOT_BREAKING}; {@code pos} carries a position only
 * for {@code SUCCESS}. These are client-side proxy reports, never proof
 * that the server accepted the break.</p>
 *
 * @zh 方块破坏覆盖上报的结果：诊断 {@code reason} 码与其适用位置。
 * 理由码为代理词表；{@code pos} 仅 {@code SUCCESS} 携带位置。
 * 这些是客户端代理上报，不代表服务端已接受该次破坏。
 */
public record BreakBlockResult(String reason, Pos3d pos) {
}
