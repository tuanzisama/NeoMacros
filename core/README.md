English | [简体中文](README.zh-CN.md)

# core

Version-independent script runtime. Compiled once against the Java 17 baseline and embedded into every product node, so it never touches Minecraft classes directly — it talks to the game only through the [ports](../ports/) interfaces.

Main areas:

- `runtime` — script lifecycle and execution: `ScriptRuntime`, `ScriptEngine`, `ScriptContext`, error reporting
- `event` — event bus and catalog: subscription, cancellation flags, sync watchdog
- `module` — script module (addon) management and trust handling
- `config` — configuration codec, sections and options
- `storage` — persistent per-module storage
- `security` — sandbox policy and permission checks
- `thread` — threading primitives for scripts
