[English](README.md) | 简体中文

# product

Fabric 模组本体：唯一依赖具体版本的模块。Stonecutter 为每个目标版本创建一个虚拟节点（`:product:<mc>`），统一由本目录的 `build.gradle.kts` 驱动。

- `adapter` — 基于 Fabric/Yarn 实现 [ports](../ports/) 接口：收集游戏事件的 hub 类（聊天、实体、世界、音效、滚轮、Boss 血条等）、各 `Fabric*Port` 实现、mixin，以及主线程调度
- `fabric` — 模组入口与游戏内界面（脚本管理器、编辑器）

版本相关的构建设置位于 `versions/<mc>/gradle.properties`；1.x 节点使用映射（混淆）工具链，26.x 节点使用非混淆工具链。

当前活跃节点可用 `./gradlew :product:runClient` 启动开发客户端。
