/**
 * ports：版本无关 SPI —— 端口接口（WorldPort/ChatPort/…）与中立数据类型
 * （Pos3d/EntitySnapshot/ItemStackData… record）。
 *
 * <p>本模块与 core 一样禁止 import {@code net.minecraft.*} 与 Fabric API（架构文档 06 §1）。
 * 端口清单与演进规则（default 方法兜底）由 T-011 落地。</p>
 */
package ink.tuanzi.neomacros.port;
