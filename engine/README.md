English | [简体中文](README.zh-CN.md)

# engine

GraalJS integration for the script runtime. Version-independent, like [core](../core/).

Main pieces:

- `GraalJsEngine` — creates and configures the GraalJS contexts scripts run in
- `ScriptSandboxFileSystem` / `VirtualModulePath` — a confined filesystem view so scripts can only reach their own module tree
- `ModuleResolver` — resolves ES module imports against the sandbox paths
- `AsyncBinding` — plumbing for exposing async host functions to scripts
