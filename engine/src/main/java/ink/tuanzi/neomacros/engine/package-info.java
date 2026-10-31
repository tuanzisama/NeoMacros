/**
 * Script engine layer: the GraalJS implementation of the internal
 * {@code ScriptEngine} interface.
 *
 * <p>Owns GraalJS context management, resolution of the {@code @neomacros/*}
 * ES module specifiers, module caching for hot reload, injection of the
 * {@code neo} global namespace, the Promise-to-event-loop bridge, host access
 * restrictions, and translation of {@code PolyglotException} into
 * {@code ScriptError} with source positions.</p>
 */
package ink.tuanzi.neomacros.engine;
