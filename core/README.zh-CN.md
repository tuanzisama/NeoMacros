[English](README.md) | 简体中文

# core

版本无关的脚本运行时。针对 Java 17 基线只编译一次，随后嵌入每个 product 节点，因此不直接依赖 Minecraft 类——只通过 [ports](../ports/) 接口与游戏交互。

主要内容：

- `runtime` — 脚本生命周期与执行：`ScriptRuntime`、`ScriptEngine`、`ScriptContext`、错误上报
- `event` — 事件总线与事件目录：订阅、取消标记、同步看门狗
- `module` — 脚本模块（插件）管理与信任处理
- `config` — 配置编解码、配置节与选项
- `storage` — 模块级持久化存储
- `security` — 沙箱策略与权限检查
- `thread` — 面向脚本的线程原语
