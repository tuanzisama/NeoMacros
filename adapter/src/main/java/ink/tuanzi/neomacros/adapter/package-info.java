/**
 * Version-specific adapter layer: implementations of the port interfaces
 * declared in {@code ink.tuanzi.neomacros.port}.
 *
 * <p>This is the only shared source directory allowed to import
 * {@code net.minecraft.*} or Fabric APIs; {@code core}, {@code ports},
 * {@code engine} and {@code doc-api} must stay free of both (enforced by the
 * dependency-guard tests).</p>
 *
 * <p>Port interfaces only ever grow: add methods with {@code default}
 * implementations, never change existing signatures.</p>
 */
package ink.tuanzi.neomacros.adapter;
