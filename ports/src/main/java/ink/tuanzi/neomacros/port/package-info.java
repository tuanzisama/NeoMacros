/**
 * Version-independent SPI: port interfaces ({@code WorldPort},
 * {@code ChatPort}, ...) and neutral data types ({@code Pos3d},
 * {@code EntitySnapshot}, {@code ItemStackData}, ... records).
 *
 * <p>Like {@code core}, this package must not import {@code net.minecraft.*}
 * or Fabric APIs. Port interfaces only ever grow: add methods with
 * {@code default} implementations, never change existing signatures.</p>
 */
package ink.tuanzi.neomacros.port;
