---
title: NeoMacros
description: 为 Minecraft（Fabric）提供 ECMAScript 客户端脚本
---

::u-page-hero
#title
NeoMacros
#description
为 Minecraft（Fabric）提供 ECMAScript 客户端脚本：基于 GraalJS 的 ES 模块，
支持 Promise、async/await，每个脚本拥有真实的事件循环——全部在你的游戏客户端
内运行。
#links
  :::u-button
  ---
  size: xl
  to: /zh-cn/guide/getting-started
  trailing-icon: i-lucide-arrow-right
  ---
  快速上手
  :::

  :::u-button
  ---
  color: neutral
  size: xl
  to: /zh-cn/api/
  variant: outline
  ---
  API 参考
  :::

  :::u-button
  ---
  color: neutral
  size: xl
  to: /zh-cn/examples/
  variant: outline
  ---
  示例
  :::
::

::u-page-section
  :::u-page-grid
    ::::u-page-card
    ---
    spotlight: true
    to: /zh-cn/guide/getting-started
    ---
    #title
    仅限 ECMAScript
    #description
    基于 GraalJS 的 ES 模块，支持 Promise、async/await，每个脚本都有真实的
    事件循环。
    ::::

    ::::u-page-card
    ---
    spotlight: true
    to: /zh-cn/examples/
    ---
    #title
    事件驱动
    #description
    通过 events.on 订阅事件——tick、聊天、实体、世界音效、Boss 条等。
    ::::

    ::::u-page-card
    ---
    spotlight: true
    to: /zh-cn/guide/permissions-and-trust
    ---
    #title
    沙箱隔离
    #description
    显式的能力授予、按脚本信任，默认无法访问宿主环境。
    ::::
  :::
::
