/**
 * Neutral value types crossing port signatures and event payloads: immutable
 * records ({@code Pos3d}, {@code EntitySnapshot}, {@code ItemStackData}, ...)
 * that never hold live game references.
 *
 * <p>Immutability contract: components are primitives or immutable JDK types;
 * list components are copied defensively at construction and stored
 * unmodifiable. These types are the vocabulary of the version-independent
 * layer — port signatures and event payload records compose them instead of
 * exposing game classes.</p>
 */
package ink.tuanzi.neomacros.port.model;
