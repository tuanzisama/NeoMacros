/**
 * Version-independent scripting engine core: the {@code ScriptRuntime} hub,
 * event framework, script lifecycle, threading model (per-context event loop
 * plus a watchdog-guarded synchronous channel), permission trust gate,
 * configuration, storage, and the script-facing API modules.
 *
 * <p>This package must not import {@code net.minecraft.*} or Fabric APIs; all
 * game access goes through the port interfaces in
 * {@code ink.tuanzi.neomacros.port}.</p>
 */
package ink.tuanzi.neomacros.core;
