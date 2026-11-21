# Cognia 订阅与 Token 额度 API 设计

> 状态：核心后端已实现；支付渠道适配仍需按选定渠道配置签名密钥和交易字段映射<br>
> 适用范围：Cognia Spring Boot 后端与其管理/用户 API
> API 前缀：`/api/v1`

## 1. 目标

本方案支持：

1. 管理员创建、发布、下架订阅方案，并为方案配置允许使用的聊天模型。
2. 管理员配置新注册普通用户可获得的默认方案和初始 Token 额度。
3. 管理员创建独立的 Token 补充包。
4. 用户通过已登录的 REST API 查询方案、创建购买订单、查询订单与额度。
5. 只有经过后端验证的支付结果才能启用订阅或增加 Token。
6. 聊天接口在调用模型前校验模型权限和可用额度，并在结束后按实际用量结算。

此处的“用户通过 API 购买”是指用户携带 Cognia 的 Bearer access token 调用平台 REST API，不等同于 OpenAI 兼容 API Key 或第三方开发者计费 API。

## 2. 与现有后端的衔接

当前后端采用 Spring Boot、PostgreSQL、MyBatis-Plus、`/api/v1` 路由、`ApiResponse<T>` 统一响应和 `@PreAuthorize` 权限控制。现有 `sys_model_config` 是模型目录，`GET /api/v1/chat/models` 为登录用户返回可用模型；聊天 SSE 已采集输入/输出 Token 数据。

实现时应遵循以下原则：

- 复用 `sys_model_config.id` 关联模型，不在订阅表中重复维护模型定义。
- `GET /api/v1/chat/models` 和 `POST /api/v1/chat/stream` 都必须执行订阅权限检查，不能只在前端隐藏模型。
- 注册、OAuth 注册及管理员创建普通用户等用户创建路径统一初始化默认方案和初始额度。
- 现有 `sys_metric_event` 是分析/仪表盘用途，写入失败可以被忽略；不能把它当扣款账本。计费需要新的事务性 Token 账本和幂等结算记录。
- 新增权限应加入 `DataInitializer` 的权限目录，并沿用现有 `model:read`、`settings:update` 一类权限控制方式。
- 所有 API 的 BIGINT ID 对外使用字符串，时间和分页沿用 [API.md](./API.md) 的约定。

## 3. 推荐的产品规则

### 3.1 免费默认方案

- 设置一个唯一的免费默认方案，未购买有效订阅的用户使用该方案的模型权限。
- 新用户注册成功时获得一次性初始额度；额度数由管理员配置。
- 注册额度是否过期可配置；不配置过期时间时按永久有效处理。
- 额度发放只针对新建用户，修改设置不追溯补发给老用户。
- 免费方案仅控制模型权限；购买的补充包不会自动解锁模型。

### 3.2 付费订阅方案

一个方案至少包含名称、价格、币种、有效期、每周期 Token 配额、周期长度、可用模型和发布状态。建议将“有效期”和“配额周期”分开，例如有效期 365 天、每 30 天发放一次额度。

- 付费订阅未自动续费；到期后回退到免费默认方案，用户可再次下单购买。
- 周期配额在每个配额周期开始时发放，到下一个周期或订阅结束时失效，不跨周期累计。
- 同一方案续期时从当前订阅结束时间顺延；不同方案的购买可排队至当前订阅结束后生效。具体规则需要在实现前确认。
- 已支付订单应保存方案和模型权限快照，避免管理员修改方案后追溯改变用户已经购买的权益。模型被管理员全局禁用时仍应立即禁止调用。

### 3.3 Token 补充包

- 补充包只增加 Token 余额，不改变订阅有效期和模型权限。
- 补充 Token 单独记账，按最早过期优先消费；没有设置有效期时可永久有效。
- Token 扣减顺序建议为：即将过期的订阅周期额度、其他订阅额度、注册赠送额度、补充包额度。
- 用户订阅到期时，未使用的订阅周期额度失效；补充包和未过期的注册额度仍保留。

### 3.4 Token 计费单位

建议余额使用整数 Token 额度，聊天实际输入与输出 Token 均参与扣除。为避免不同模型成本差异过大，可给每个模型配置输入/输出计费倍率：

```text
扣除额度 = ceil(
  (实际输入 Token × 每百万输入 Token 的计费额度
   + 实际输出 Token × 每百万输出 Token 的计费额度)
  / 1,000,000
)
```

例如，每百万输入 Token 的计费额度设为 `1,000,000` 即表示输入按 1:1 扣除。倍率是否由管理员配置、还是由平台固定，需要在支付上线前确认。用量 API 同时返回原始输入/输出 Token 与最终扣除额度。

## 4. 核心数据模型

金额使用最小货币单位的整数存储（例如分），禁止使用浮点数。额度和 Token 计数使用 BIGINT。以下是逻辑模型，具体字段、索引和迁移应按现有 `schema.sql` 风格落地。

| 表 | 用途 | 关键字段建议 |
| --- | --- | --- |
| `sys_billing_setting` | 全局计费设置 | `default_plan_id`、`registration_tokens`、`registration_token_expire_days`、`currency` |
| `sys_billing_plan` | 免费/订阅方案 | `code`、`name`、`plan_type`、`price_minor`、`currency`、`duration_days`、`quota_tokens`、`quota_cycle_days`、`status`、`is_default`、`version` |
| `sys_billing_plan_model` | 方案可用模型 | `plan_id`、`model_config_id`；对二者建立唯一约束 |
| `sys_model_billing_rate` | 可选的模型 Token 倍率 | `model_config_id`、`input_credits_per_million`、`output_credits_per_million` |
| `sys_token_pack` | Token 补充包目录 | `code`、`name`、`price_minor`、`currency`、`tokens`、`expire_days`、`status` |
| `sys_billing_order` | 用户购买订单 | `order_no`、`user_id`、`product_type`、`product_id`、`status`、`amount_minor`、`currency`、`product_snapshot`、`idempotency_key`、`expire_time`、`paid_time` |
| `sys_payment_transaction` | 支付渠道交易记录 | `order_id`、`provider`、`provider_transaction_id`、`amount_minor`、`status`、`paid_time` |
| `sys_payment_webhook_event` | Webhook 幂等与审计 | `provider`、`provider_event_id`、`payload_hash`、`processed_time`；`provider + provider_event_id` 唯一 |
| `sys_user_subscription` | 用户订阅实例/历史 | `user_id`、`plan_id`、`status`、`start_time`、`end_time`、`next_quota_time`、`product_snapshot` |
| `sys_token_lot` | 按来源和过期时间分桶的额度余额 | `user_id`、`source_type`、`source_id`、`granted_tokens`、`remaining_tokens`、`held_tokens`、`expire_time` |
| `sys_token_ledger` | 不可变的额度变动流水 | `user_id`、`event_type`、`delta_tokens`、`balance_after`、`source_type`、`source_id`、`request_id`、`created_at` |
| `sys_token_reservation` | 聊天请求预扣与释放 | `request_id`、`user_id`、`reserved_tokens`、`settled_tokens`、`status` |
| `sys_token_usage` | 实际模型调用用量（管理员统计的权威来源） | `request_id`、`user_id`、`model_config_id`、`provider_id`、`model_name_snapshot`、`input_tokens`、`output_tokens`、`charged_tokens`、`duration_ms`、`status`、`created_at` |
| `sys_admin_token_grant` | 管理员手动发放 Token 包的审计记录 | `grant_no`、`user_id`、`token_pack_id`、`operator_id`、`tokens`、`reason`、`expire_time`、`idempotency_key`、`ledger_id` |
| `sys_billing_notification_outbox` | 计费事件的可靠站务通知队列 | `event_key`（唯一）、`user_id`、`type`、`title`、`content`、`payload_json`、`status`、`attempts`、`next_attempt_time`、`processed_time` |

要求：

- `sys_token_ledger` 只追加，不直接更新或删除；更正通过新的反向流水完成。
- 下单请求按用户和 `Idempotency-Key` 唯一；重复请求返回原订单，不重复创建支付。
- `sys_token_usage.request_id`、Webhook 事件 ID、支付渠道交易 ID 均建立唯一约束。
- 为 `sys_token_usage` 建立 `(created_at, model_config_id)`、`(created_at, user_id)` 等统计索引，并按时间范围限制后台聚合查询。
- 订单持久化商品快照（名称、金额、币种、Token 数、有效期、模型权限、方案版本），支付确认只按快照履约。
- 余额可由 Token 分桶聚合，或维护带行锁的账户汇总表作为缓存；权威历史以流水和额度分桶为准。
- 手动发放记录、额度分桶、额度流水和通知 outbox 在同一数据库事务中提交。
- `sys_admin_token_grant.idempotency_key` 唯一；重复请求返回原发放记录，不重复增加余额。
- 在 `schema.sql` 中为 `sys_notification` 幂等扩展增加 `source_key` 可空列和非空唯一索引；通知服务增加支持 `source_key` 的幂等调用，避免 outbox 重试产生重复站务通知，同时保持既有调用兼容。

## 5. API 草案

所有管理端和用户接口使用：

```http
Authorization: Bearer <accessToken>
```

管理端和用户接口统一使用 `ApiResponse<T>`；示例仅展示 `data` 内容。支付 Webhook 按支付渠道协议返回其要求的应答格式。

### 5.1 管理员 API

管理端权限建议新增 `billing:read`、`billing:update`、`billing:order-read`、`billing:usage-read`、`billing:grant`、`billing:adjust`。使用量和用户详情接口需 `billing:usage-read`；手动发放接口需 `billing:grant`。管理员接口使用 `@PreAuthorize`，普通用户不能通过修改请求体访问他人权益。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/billing/settings` | 查询默认方案和新用户初始 Token 设置 |
| `PUT` | `/api/v1/admin/billing/settings` | 更新默认方案、注册赠送额度及有效期 |
| `GET` / `POST` | `/api/v1/admin/billing/plans` | 分页查询 / 创建方案 |
| `GET` / `PUT` | `/api/v1/admin/billing/plans/{id}` | 查询 / 更新方案 |
| `PUT` | `/api/v1/admin/billing/plans/{id}/models` | 全量替换方案允许使用的模型 ID 列表 |
| `POST` | `/api/v1/admin/billing/plans/{id}/publish` | 发布方案 |
| `POST` | `/api/v1/admin/billing/plans/{id}/disable` | 下架方案；不物理删除有历史订单的方案 |
| `GET` / `POST` | `/api/v1/admin/billing/token-packs` | 查询 / 创建补充包 |
| `GET` / `PUT` | `/api/v1/admin/billing/token-packs/{id}` | 查询 / 更新补充包 |
| `POST` | `/api/v1/admin/billing/token-packs/{id}/disable` | 下架补充包 |
| `GET` | `/api/v1/admin/billing/orders` | 按订单状态、用户、时间分页查询订单 |
| `GET` | `/api/v1/admin/billing/usage/summary` | 按时间范围查看全站模型调用量、输入/输出 Token、扣除额度、用户数及失败量 |
| `GET` | `/api/v1/admin/billing/usage/by-model` | 按模型汇总消耗，支持时间范围、模型/提供商筛选和分页 |
| `GET` | `/api/v1/admin/billing/usage/trend` | 按小时、日或月查看调用量与 Token 消耗趋势 |
| `GET` | `/api/v1/admin/billing/models/{modelId}/usage/users` | 查看指定模型的用户消耗排行 |
| `GET` | `/api/v1/admin/billing/users/{userId}` | 查看单个用户的订阅、额度分桶及指定时间范围内的消耗汇总 |
| `GET` | `/api/v1/admin/billing/users/{userId}/usage` | 分页查询该用户的逐次模型消耗，可按模型、状态和时间过滤 |
| `GET` | `/api/v1/admin/billing/users/{userId}/ledger` | 查询用户额度流水 |
| `POST` | `/api/v1/admin/billing/users/{userId}/token-grants` | 免支付向用户手动发放一个已配置的 Token 包 |
| `POST` | `/api/v1/admin/billing/users/{userId}/adjustments` | 管理员手动纠正额度，必须填写原因并写审计记录；不用于常规发包 |

#### 模型用量查询约定

- 汇总和趋势默认查询最近 30 天，单次范围建议限制在 90 天内；后台可分页，时间按站点时区聚合。
- 汇总至少返回调用次数、成功/失败次数、活跃用户数、输入 Token、输出 Token、扣除额度和平均耗时。
- 按模型结果包含模型/提供商 ID 与名称、输入/输出 Token、扣除额度、调用次数及失败数；趋势接口返回相同指标的时间桶。
- 单用户逐次记录返回时间、模型、输入/输出 Token、扣除额度、状态和耗时，不返回 Prompt/回答正文。用户详情响应应将“总 Token 用量”和“扣除额度”分开显示。
- 管理员用量报表以 `sys_token_usage` 为权威数据源，`sys_metric_event` 继续服务现有仪表盘，不参与余额核算。未配置模型成本价格时只展示 Token 消耗，不显示推算成本。

查询参数示例：

```text
GET /api/v1/admin/billing/usage/summary?from=2026-11-01&to=2026-11-21
GET /api/v1/admin/billing/usage/by-model?from=2026-11-01&to=2026-11-21&page=1&size=20
GET /api/v1/admin/billing/usage/trend?from=2026-11-01&to=2026-11-21&interval=DAY
GET /api/v1/admin/billing/users/2114256826568491009/usage?modelId=12001&status=SUCCESS&page=1&size=20
```

汇总响应的 `data` 建议形状：

```json
{
  "range": { "from": "2026-11-01", "to": "2026-11-21" },
  "requestCount": 2480,
  "successCount": 2401,
  "failedCount": 79,
  "activeUserCount": 138,
  "inputTokens": 9823400,
  "outputTokens": 2145800,
  "chargedTokens": 14329800,
  "averageDurationMs": 1840
}
```

单次用户用量记录建议至少包含 `requestId`、`createdAt`、`modelId`、`modelName`、`inputTokens`、`outputTokens`、`chargedTokens`、`status`、`durationMs` 和可选的 `conversationId`；不要返回聊天消息内容。

#### 手动发放 Token 包

请求示例：

```http
POST /api/v1/admin/billing/users/2114256826568491009/token-grants
Idempotency-Key: 2f58cabc-...
```

```json
{
  "tokenPackId": "51001",
  "reason": "活动奖励"
}
```

发放时由服务端读取 Token 包的 Token 数和有效期，不接受客户端传入用户以外的归属、额度或过期时间；操作人从认证上下文取得。接口直接创建 `ADMIN_GRANT` 额度分桶和流水，不创建已支付订单。响应包含发放记录 ID、发放额度、到期时间和发放后的可用余额。

方案创建请求示例：

```json
{
  "code": "pro-monthly",
  "name": "Pro 月度",
  "description": "适用于个人高频使用",
  "priceMinor": 3900,
  "currency": "CNY",
  "durationDays": 30,
  "quotaTokens": 1000000,
  "quotaCycleDays": 30,
  "modelIds": ["12001", "12002"],
  "sort": 10
}
```

模型 ID 必须存在于 `sys_model_config`，类型必须为 `CHAT`，且模型及提供商处于启用状态。服务端校验，不接受前端自行提交模型名称或权限字段。

### 5.2 用户 API

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/v1/billing/catalog` | 查询当前可购买的订阅方案和补充包 |
| `GET` | `/api/v1/billing/me` | 查询当前有效方案、模型权限和额度分桶 |
| `GET` | `/api/v1/billing/me/ledger` | 查询自己的额度流水，分页 |
| `GET` | `/api/v1/billing/me/usage` | 查询自己的 Token 用量，分页/时间过滤 |
| `POST` | `/api/v1/billing/orders` | 创建待支付订单并返回支付渠道发起信息 |
| `GET` | `/api/v1/billing/orders/{orderNo}` | 查询自己的订单状态 |
| `POST` | `/api/v1/billing/orders/{orderNo}/cancel` | 取消尚未支付且允许取消的订单 |

创建订单请求：

```http
POST /api/v1/billing/orders
Idempotency-Key: 8bc2f7b7-...
```

```json
{
  "productType": "SUBSCRIPTION",
  "productId": "31001",
  "paymentProvider": "ALIPAY",
  "returnUrl": "https://app.example.com/billing/return"
}
```

补充包订单将 `productType` 设为 `TOKEN_PACK`。服务端根据产品 ID 查询当前已发布商品并生成金额、币种和权益快照；禁止客户端提交金额、赠送 Token 数或任意用户 ID。

创建订单成功响应示例：

```json
{
  "orderNo": "CB202611210001",
  "status": "PENDING_PAYMENT",
  "amountMinor": 3900,
  "currency": "CNY",
  "expireTime": "2026-11-21 20:14:00",
  "payment": {
    "type": "REDIRECT",
    "url": "https://payment.example/checkout/..."
  }
}
```

`payment` 的具体字段由支付渠道适配器返回；可支持跳转链接、二维码或客户端 SDK 参数。

`GET /api/v1/billing/me` 响应建议：

```json
{
  "defaultPlan": { "id": "30001", "name": "免费版" },
  "activeSubscription": {
    "id": "41001",
    "planId": "31001",
    "planName": "Pro 月度",
    "startTime": "2026-11-21 19:00:00",
    "endTime": "2026-12-21 19:00:00",
    "nextQuotaTime": "2026-12-21 19:00:00"
  },
  "balance": {
    "availableTokens": 980000,
    "heldTokens": 2000,
    "subscriptionTokens": 800000,
    "topUpTokens": 180000,
    "nextExpiryTime": "2026-12-21 19:00:00"
  },
  "allowedModels": [
    { "id": "12001", "name": "Model A" }
  ]
}
```

### 5.3 支付渠道 Webhook

| 方法 | 路径 | 认证 |
| --- | --- | --- |
| `POST` | `/api/v1/billing/webhooks/{provider}` | 不使用用户 JWT；必须验证渠道签名 |

Webhook 处理要求：

1. 验证签名、时间戳、商户号和事件 ID，防重放。
2. 按订单号读取订单并锁定，核对支付金额、币种和订单状态。
3. 同一渠道事件只处理一次；重复回调返回渠道要求的成功应答。
4. 在单个数据库事务中更新支付记录、订单状态、订阅/额度分桶和 Token 流水。
5. 只有 Webhook 或经服务端主动向渠道核实的结果可以把订单标记为已支付；浏览器 `returnUrl` 不能直接发放权益。
6. 记录可审计的处理结果；验证失败时拒绝并告警，不静默发放。

路由需在 Spring Security 中允许渠道请求到达 Controller，但这不代表信任匿名请求；签名验证必须在应用层完成。

支付渠道尚未指定。建议先定义 `PaymentGateway` 接口和渠道适配层，选定支付宝、微信支付或 Stripe 后再实现对应签名、退款和异步通知。

### 5.4 站务通知集成

计费功能复用现有 `NotificationService.notify(...)`、通知收件箱 `/api/v1/notifications` 和通知 SSE `/api/v1/notifications/stream`；不另建一套通知 API。通知写入用户的站务通知列表，用户在线时由现有 SSE 即时推送，离线后仍可在通知列表查看。

| 事件 | 接收对象 | 建议通知类型 |
| --- | --- | --- |
| 支付成功并开通订阅 | 购买用户 | `BILLING_SUBSCRIPTION_ACTIVATED` |
| 订阅周期额度发放 | 订阅用户 | `BILLING_QUOTA_GRANTED` |
| 订阅到期 | 订阅用户 | `BILLING_SUBSCRIPTION_EXPIRED` |
| Token 补充包支付成功 | 购买用户 | `BILLING_TOKEN_PACK_PURCHASED` |
| 管理员手动发放 Token 包 | 被发放用户 | `BILLING_TOKEN_PACK_GRANTED` |
| 支付失败或订单过期 | 下单用户 | `BILLING_ORDER_FAILED` / `BILLING_ORDER_EXPIRED` |
| 用量异常告警（启用阈值规则后） | 管理员 | `BILLING_USAGE_ALERT` |

通知 payload 仅包含订单号、方案/Token 包名称、额度、到期时间及站内跳转路径等必要信息，不包含支付凭据、Prompt 或回答正文。只读报表查询不触发通知，避免管理员查看数据时产生通知噪声。

#### 通知可靠性与幂等

- 不在支付或发放的业务事务提交前直接调用 `NotificationService.notify`，因为现有服务会立即推送 SSE；事务随后回滚会造成用户收到与实际权益不一致的通知。
- 业务事务中写入 `sys_billing_notification_outbox`，事务提交后由 dispatcher 调用现有通知服务，成功后标记已处理；失败按退避策略重试并记录最后错误。
- 每条事件使用稳定的 `event_key` 作为通知 `source_key`；由唯一索引保证重复 Webhook、任务重试或进程重启不会给用户重复发通知。
- 手动发包成功后通知目标用户；管理员操作结果同时写审计日志。订阅发放或支付的异常状态可按配置通知管理员。

## 6. 聊天计费与权限流程

### 6.1 请求开始前

1. 从认证上下文取得当前用户 ID；忽略请求中任何用户 ID。
2. 加载当前有效订阅；若无有效订阅，则回退到默认免费方案。
3. 确认请求模型存在、全局启用且包含在用户当前方案的允许模型列表中。
4. 估算本次可能的输入和最大输出 Token 额度，使用短事务锁定可用额度分桶并预扣。余额不足时在启动模型调用/SSE 前返回额度不足错误。
5. 为每次聊天请求生成服务端 `requestId`，作为预扣、用量和流水的幂等关联键。

### 6.2 请求完成或失败

- 流式生成期间不持有数据库事务。
- 成功结束后，记录模型返回的原始输入/输出 Token，计算最终扣款；结算预扣与实际扣款的差额，并把未用预扣返还到原额度分桶。
- 模型失败或用户取消时，按可确认的实际用量结算；没有可靠用量时释放预扣，并记录相应状态。
- 结算接口按 `requestId` 幂等，重复的 `done`、超时补偿或重试不得重复扣款。
- 定时任务扫描超时预扣并执行补偿释放；需保留审计事件。

### 6.3 模型列表

- `GET /api/v1/chat/models` 只返回“全局开放且当前用户方案允许”的模型。
- `POST /api/v1/chat/stream` 必须再次做权限校验，不能信任客户端之前拉取的模型列表。
- `resolveChat` 的默认模型回退也必须从当前用户允许的模型中选择，不能回退到未授权模型。
- 订阅或模型权限变更应立即影响后续请求，不影响已经完成的历史对话。

## 7. 错误与订单状态

建议新增明确的业务错误码，并保持当前 `ApiResponse` 错误格式：

| HTTP | 业务错误 | 含义 |
| --- | --- | --- |
| `402` | `TOKEN_BALANCE_INSUFFICIENT` | 可用额度不足；返回当前可用额度与建议购买链接 |
| `403` | `MODEL_NOT_ENTITLED` | 当前方案不允许使用该模型 |
| `409` | `ORDER_STATE_CONFLICT` | 订单状态不允许执行当前操作 |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 同一幂等键对应不同下单请求 |
| `404` | `BILLING_PRODUCT_NOT_FOUND` | 商品不存在、已下架或不可购买 |
| `422` | `PAYMENT_AMOUNT_MISMATCH` | 渠道回调金额或币种与订单不一致 |

订单状态建议：`PENDING_PAYMENT` → `PAID`；或在支付前进入 `CANCELED` / `EXPIRED`；支付完成后的退款使用独立退款状态/记录，不能把已履约订单直接覆盖为“未支付”。

错误响应不得暴露支付密钥、渠道原始凭据、内部堆栈或 SQL。

## 8. 安全、并发与审计要求

- 商品价格、优惠、用户归属、额度、模型权限一律由服务端读取和计算。
- 下单要求认证；订单、余额、用量、流水查询必须校验资源归属。
- Webhook 使用渠道签名校验、时间窗口和唯一事件 ID；支付密钥由环境变量/安全配置管理，不返回给前端。
- 额度预扣与结算需要行锁或等价原子更新，防止并发请求花费相同余额。
- 余额分桶、流水和支付状态转换必须在事务中；外部支付 HTTP 调用不得放在数据库长事务中。
- 下架商品采用软删除/状态变更，历史订单、订阅和审计数据永久保留。
- 管理员手动调账必须记录操作人、原因、前后余额和关联流水。
- 单用户用量详情和手动发包接口仅向专门的 `billing:usage-read` / `billing:grant` 权限开放，并将操作人、查询范围和发放原因写入审计日志。
- 每个订单和聊天结算操作都应有唯一幂等键；增加订单过期清理、Webhook 重试和预扣超时补偿监控。

## 9. 建议实施顺序

1. **定规则**：确认支付渠道、订阅额度周期、续费/升级策略、补充包有效期和 Token 倍率。
2. **数据库与管理 API**：新增方案、方案模型关联、补充包、注册默认额度配置、用户手动发包审计记录和管理权限。
3. **用户权益与注册发放**：实现默认方案、一次性注册额度和 `/billing/me` 查询；覆盖密码注册、OAuth 注册及其他用户创建路径。
4. **计费账本与聊天限制**：实现额度分桶、预扣/结算、并发与幂等；记录可审计的逐次模型用量；过滤模型列表并在聊天服务端强制授权。
5. **订单、通知和分析 API**：加入订单及支付适配器、Webhook、通知 outbox、管理员模型用量/用户明细查询和手动发包 API。
6. **前端接入**：管理端方案、用量看板、用户详情与发包界面；用户购买页、站务通知和订单状态刷新。
7. **灰度上线**：先对测试用户开放；核对模型原始 Token、扣款流水、用户余额、站务通知与管理员报表一致，再开放真实支付。

## 10. 上线验收标准

- 新用户只获得一次配置的初始额度；重复注册回调或用户创建重试不会重复发放。
- 用户不能通过修改 `modelId`、`userId`、订单金额或商品快照绕过限制。
- 未授权模型调用在任何模型请求发出前被拒绝。
- 余额不足时不启动模型生成；并发请求不会透支或重复消费同一额度。
- 重复下单请求、重复支付 Webhook 和重复聊天结算均保持幂等。
- 未支付、金额不匹配或签名错误的订单不会增加额度或启用订阅。
- 订阅周期额度按期发放与过期；补充包按自身有效期和消费顺序结算。
- 管理员用量汇总、按模型/趋势统计和单用户逐次明细均与计费流水一致，且不泄漏聊天正文。
- 管理员手动发包需填写原因，重复请求不重复增加余额，并能在用户额度流水和审计记录中追溯。
- 支付成功、额度发放/到期和管理员手动发包能进入既有站务通知收件箱；SSE 在线推送、离线补发、重试幂等均正常。
- 管理员调账、退款和支付状态变化均有可追溯流水。

## 11. 实现前待确认

以下产品规则会影响数据库和 API，应在进入编码前确定：

1. 首期支付渠道：支付宝、微信支付、Stripe，还是先支持其中一种？
2. 订阅额度是每月重置，还是购买时一次性发放整期额度？年度订阅是否每月发额度？
3. 补充包 Token 是否过期；若过期，默认有效期是多少？
4. 订阅是否自动续费？本草案建议第一期只做手动下单续期。
5. 不同模型是否使用不同 Token 扣费倍率？本草案建议至少预留倍率字段。
6. 当前订阅期间购买其他方案时立即切换、排队，还是禁止购买？
7. 初始注册额度适用于所有新用户，还是只用于普通注册用户（不含管理员/导入用户）？
8. 是否启用模型高消耗/异常失败的自动站务告警；若启用，阈值和接收管理员范围如何配置？
