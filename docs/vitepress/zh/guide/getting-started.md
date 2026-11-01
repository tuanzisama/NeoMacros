# 快速上手

NeoMacros 在 Minecraft 客户端内运行 ECMAScript（JavaScript）脚本。脚本
是普通 `.js` ES 模块，放在 `config/neomacros/scripts` 目录，由 GraalJS
驱动——每个脚本拥有独立的 Promise、`async`/`await` 与事件循环。

## 环境要求

- Minecraft 26.1+ 与 [Fabric Loader](https://fabricmc.net/use/)
- 与游戏版本匹配的 [Fabric API](https://modrinth.com/mod/fabric-api)
- NeoMacros 本体：从
  [Releases](https://github.com/tuanzisama/NeoMacros/releases)
  下载 `neomacros-<mc>-fabric-<version>.jar`

## 安装

1. 为你的游戏版本安装 Fabric Loader。
2. 将 NeoMacros 与 Fabric API 的 jar 放入 `mods` 目录。
3. 启动一次游戏；NeoMacros 会创建 `config/neomacros/scripts`。

## 目录结构

脚本接触的所有内容都在游戏目录下：

```
.minecraft/
└── config/
    └── neomacros/
        ├── options.json   # 模组设置
        └── macros/        # 你的脚本，一个文件夹一个脚本
            ├── keypress/  # 按键触发
            ├── event/     # 事件驱动
            └── services/  # 常驻服务
```

每个脚本文件夹包含一份 `neomacro.yml`（或 `.json` / `.json5`）清单和入口
文件——见[第一个脚本](./first-script)。

脚本的持久化数据存放在各自独立的存储里（见
[`storage`](/zh/api/storage)），文件保持可读、可随时替换。

## 游戏内命令

所有管理操作都通过 `/neomacros` 客户端命令完成：

| 命令 | 用途 |
|---|---|
| `/neomacros list` | 查看已加载脚本 |
| `/neomacros reload` | 重新扫描脚本目录并加载变更 |
| `/neomacros trust <script>` | 为脚本授予能力 |
| `/neomacros errors` | 打开最近脚本错误界面 |

## 下一步

- 动手写[第一个脚本](./first-script)。
- 了解[事件模型与异步模式](./events-and-async)。
- 浏览 [API 参考](/zh/api/) 查看全部门面与事件负载。
