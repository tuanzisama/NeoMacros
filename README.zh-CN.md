[English](README.md) | 简体中文

# NeoMacros

面向 Minecraft 客户端的 ECMAScript 脚本模组，仅支持 Fabric。脚本可以挂钩聊天、实体、输入、物品栏、渲染、HUD、音效与世界事件。

多个 Minecraft 版本由 [Stonecutter](https://stonecutter.kikugie.dev/) 从同一份代码构建——每个目标版本一个虚拟节点（`1.21.6`、`26.1.2`、`26.2`、`26.3`）。

## 模块

| 模块 | 职责 |
| --- | --- |
| [core](core/) | 版本无关的脚本运行时：生命周期、事件、配置、存储、安全 |
| [engine](engine/) | GraalJS 引擎集成，带沙箱化模块文件系统 |
| [ports](ports/) | 版本无关的端口接口与适配器 SPI |
| [product](product/) | Fabric 适配器与模组入口；每个 MC 版本一个 Stonecutter 节点 |
| [doc-api](doc-api/) | 标记脚本公开 API 面的注解集 |
| [e2e](e2e/) | 无头端到端测试，不启动游戏即可运行脚本 |
| [script-tests](script-tests/) | 游戏内 E2E 用例集，与 e2e fixtures 共用同一份文件 |

文档站及其生成器在独立的
[NeoMacros-Doc](https://github.com/tuanzisama/NeoMacros-Doc)
仓库里；本仓库的 API 源码推送经 `docs-dispatch` 工作流触发其再生成。

## 构建

```bash
./gradlew build           # 所有 product 节点
./gradlew :product:build  # 仅当前活跃节点
```

## 许可

MIT
