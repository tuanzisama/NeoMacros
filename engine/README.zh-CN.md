[English](README.md) | 简体中文

# engine

脚本运行时的 GraalJS 集成。与 [core](../core/) 一样，版本无关。

主要构成：

- `GraalJsEngine` — 创建并配置脚本运行的 GraalJS 上下文
- `ScriptSandboxFileSystem` / `VirtualModulePath` — 受限的文件系统视图，脚本只能访问自身模块树
- `ModuleResolver` — 依据沙箱路径解析 ES module 导入
- `AsyncBinding` — 向脚本暴露异步宿主函数的桥接
