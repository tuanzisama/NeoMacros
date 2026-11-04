# 配对体验改造：token 落盘 + 信任浏览器 + 编辑器自愈重连

## Context

玩家频繁调试时，配对链路摩擦大：每次开游戏要敲 `/neomacros editor` 拿一次性 token（180s、单次、重启即失效）；多浏览器/无痕/vite dev 多端口导致 IndexedDB 凭据互不相通，反复配对；游戏重启后面板 tab 变 disconnected 无法自愈，手动重连还被强制要求填 token。

已确认的需求决策：
1. **token 落盘**：跨游戏重启有效，TTL 可配置默认 24h，单次配对+新作废旧 token 语义不变；入口保持手动命令
2. **信任浏览器（免 token 配对）**：Origin + Host 双校验 + 游戏内聊天确认；确认时可选「仅允许此浏览器」或「信任本机」（回环粒度，本机所有浏览器后续免 token 免确认）；**局域网设备保持 token 模式**
3. **编辑器自愈**：服务不可达/游戏重启后自动退避重连，零操作恢复；已有凭据的重连不再强制填 token
4. 配对成功 = 宏执行权限，安全边界如上；顺带修复深链 host 硬编码 `127.0.0.1` 的问题

安全门（免 token 配对请求）：① 源地址必须回环（LAN 直接拒绝，保持 token 模式）；② Origin 必须在 CORS 白名单内（防浏览器 drive-by 网页，Origin 由浏览器保证不可伪造）；③ Host 已有全局 HostPolicy 校验；④ 首次需游戏内确认，信任本机后回环来源静默放行。

## Part 1 — 游戏：token 落盘 + 可配置 TTL

**`pair/PairingService.java` 重构为持久化**（复用 `DeviceRegistry` 惯例：pretty Gson、schema:1、`.bak`、`.corrupted` 隔离）：
- 新状态文件 `web-pairing.json`：`{schema, token, tokenExpiresAt(epoch millis), trustLoopback}`（trustLoopback 为 Part 2 预留）
- 构造签名改为 `PairingService(Path file, LongSupplier nowMillis, LongSupplier ttlMillis, Consumer<String> warn)`，构造时加载、mint/consume 时写盘；TTL 从 supplier 读取（配置改动即时生效）
- 现有 `newToken/validate/consume` 语义不变（单活 token、单次、新作废旧 token）

**`core/config/WebSettings.java`**：新增 `@Option(description=...) public int pairingTokenTtlMinutes = 1440;`（min 1, max 20160）——ConfigCodec 反射机制自动持久化。

**`web/WebService.java`**：`PairingService` 改为外部构造注入（NeoMacrosFabric 中与 DeviceRegistry 并排：`configDir.resolve("web-pairing.json")` + warn consumer），不再在 `start()` 里创建、`stop()` 里置空——token 跨服务重启存活。

**深链 host 小修**（`NeoMacrosFabric.webContext()` ~L686-708）：host 由 listen 设置推导（`127.0.0.1` / `[::1]`；`0.0.0.0` 仍回退 `127.0.0.1`），替换硬编码。

## Part 2 — 游戏：免 token 配对请求 + 游戏内确认

**新端点**（`v1/ApiV1.java`，均加入 `AuthFilter.EXEMPT` 精确条目）：
- `POST /api/v1/pair-request`，body `PairApplyRequest(label, algo, pubJwk)`（新 record，带 `@NmDesc`），参数注入 `ApiContext`（先例：`writeFile`）读取 Origin 头。校验链：Origin ∈ 白名单（给 `CorsPolicy` 加 `allowsOrigin(String)`，注入 ApiV1）→ 源地址回环（`ApiContext.source()` 解析，抽成可单测的 helper）→ `trustLoopback` 开启则**同步注册设备**返回 `{status:"paired", clientId}`；否则挂 pending 返回 `{status:"pending", requestId, expiresIn}`
- `GET /api/v1/pair-request/{id}` → `PairApplyStatus(status: pending|approved|denied|expired, clientId?)`。审批结果用轮询而非 SSE：未配对浏览器拿不到 NM1 ticket

**`pair/PairingRequests.java`**（新）：pending 请求表，requestId 短随机 hex、60s TTL、容量上限 8、惰性过期清理（NonceStore 惯例）、`ConcurrentHashMap`（HTTP 线程写 / 客户端线程审批）。approve 即 `DeviceRegistry.register`。

**游戏内聊天确认**（复用 trust_prompt 模式：`NeoMacrosFabric.onInitializeClient` L191-199）：
- PairingService/requests 收到新 pending 时回调 → `adapter.chat()` 在**单条消息**里发可点击片段（给 `FabricChatPort` 加一个多段 clickable 方法；`runOnMain` 已保证 HTTP 线程安全）：
  `面板配对请求：Chrome@Windows（来自 127.0.0.1）` + `[仅允许此浏览器]` `/neomacros editor approve <id>` + `[信任本机]` `/neomacros editor trust-host <id>` + `[拒绝]` `/neomacros editor deny <id>`

**`WebCommands.subtree` 新子命令**（suggests 补全从 pending 列表，复制 `disconnect` 写法）：
- `editor approve <id>`：注册设备 + 反馈
- `editor trust-host <id>`：注册设备 + `trustLoopback=true` + 落盘
- `editor deny <id>`：拒绝 + 反馈
- `editor untrust`：关闭信任本机（安全卫生出口）

**i18n**：`neomacros.chat.pair_request*` + `neomacros.cmd.web.approved/denied/trusted/untrusted/no_pending_request/request_expired`，en_us.json 与 zh_cn.json 同步。

**OpenAPI**：新 DTO 带描述；手工刷新 `product/src/test/resources/web/openapi-golden.json` + `WebApiIntegrationTest.theContractServesUnsignedAndCoversW1` 路径断言。

## Part 3 — 编辑器：自愈重连 + 免 token 配对 UI

**`src/stores/connection.ts`**（循环必须放 store——ConnectView 在 online 时卸载）：
- 新状态 `'reconnecting'`；`startReconnectLoop()`：已知 host（`connection.host || lastHost()`）时轮询 `getMeta()`（unsigned），退避 1s→2s→4s→8s 封顶（复制 `sse.ts` 惯例）；meta 可达后 `connect(host)` 恢复在线，App.vue 现有 status watch 自动触发 `onOnline()` 全量刷新。`disconnect()` 显式断开时停止循环。**轮询到 unpaired 时不自动发起配对请求**（避免未信任时向游戏刷确认请求），留给用户操作
- `pairWithoutToken()`：`requestPair()` → pending 则 1s 轮询 `getPairRequest(id)`，直至 approved（存凭据转 online）/ denied / expired（90s 上限）
- 401 自愈扩展（`client.onUnauthorized`）：`unknown device` 且无 token 时自动走 `pairWithoutToken()`（信任本机=全自动静默恢复；未信任=游戏内出确认卡片，用户可拒），失败再降级 unpaired
- `src/api/endpoints.ts`：新增 `requestPair` / `getPairRequest`

**`src/components/connect/ConnectView.vue`**：
- 已存凭据时 token 非必填：按钮变「重连」（仅 `connect(host)`）
- unpaired 时：填了 token 走原路；没填走「免 token 配对（需游戏内确认）」，显示等待倒计时状态；403（非回环/Origin 被拒）时提示改用 token

**展示层**：`TitleBar.vue` / `StatusBar.vue` 为 `reconnecting` 加标签与琥珀色圆点（StatusBar 已有「事件流重连中」槽位可复用）。

**`src/App.vue`**：`server:shutdown` 处理从「置 disconnected 不重连」（契约 §6）改为 `reconnecting` + 启动自愈循环 + toast「游戏已停止，重启后自动重连」——契约行为的有意修订。

**文档与 stub**（契约 §12.3 要求同步）：
- `docs/mod-auth-contract.md`：§4 增补 pair-request 流程与安全门、§6 修订重连语义、§12 开发说明
- `dev-stub/server.mjs`：实现 pair-request 两端点 + 默认 2s 自动批准（env 可关）+ `/dev/pair-requests` GET/POST 手动批准/拒绝模拟
- `endpoints.ts` 头部过时的 W1 注释顺手更正

## 关键文件

游戏仓库 `C:/Projects/fabric-mod/neomacros`：
- 改：`core/.../config/WebSettings.java`、`product/.../web/pair/PairingService.java`、`product/.../web/WebService.java`、`product/.../web/v1/ApiV1.java`、`product/.../web/http/AuthFilter.java`、`product/.../web/http/CorsPolicy.java`、`product/.../fabric/WebCommands.java`、`product/.../fabric/NeoMacrosFabric.java`、`product/.../adapter/FabricChatPort.java`、两个 lang json
- 新：`product/.../web/pair/PairingRequests.java`、`v1/PairApplyRequest.java`、`v1/PairApplyStatus.java`
- 测试：`SupportTest.java`（持久化/时钟/TTL/trustLoopback）、`WebApiIntegrationTest.java`（Origin 拒绝、pending→approve 轮询、自动批准、服务重启后旧 token 有效）、`openapi-golden.json` 刷新

编辑器仓库 `C:/Projects/fabric-mod/NeoMacros-editor`：`connection.ts`、`endpoints.ts`、`ConnectView.vue`、`App.vue`、`TitleBar.vue`、`StatusBar.vue`、`dev-stub/server.mjs`、`docs/mod-auth-contract.md`

## 验证

1. **游戏单测**：`./gradlew :product:test :core:test`（含刷新后的 OpenApiGoldenTest）
2. **编辑器构建**：`pnpm -C ../NeoMacros-editor build`（vue-tsc 类型检查是唯一自动门）
3. **stub 端到端**：`pnpm stub` + `pnpm dev` + Playwright 浏览器驱动：配对→停 stub→观察自动重连恢复→免 token 配对（stub 自动批准）→手动拒绝路径
4. **真机验证**（需你配合）：`runClient` 后本机浏览器验证落盘 token 重启有效、聊天确认卡片、信任本机后无痕窗口秒配

## 实施顺序

Part 1（独立可交付）→ Part 2 → Part 3 + stub/文档。每部分完成即跑对应测试。