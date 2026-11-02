# Cognia API 参考

> Base URL：`http://localhost:8080/api/v1`
> 所有请求/响应均为 `application/json; charset=UTF-8`（`204` 无响应体）。

---

## 1. 通用约定

### 1.1 统一响应

成功：

```json
{
  "code": 0,
  "message": "success",
  "data": { "id": "2114256826568491009" },
  "timestamp": 1792913158000
}
```

失败：

```json
{
  "code": 40103,
  "message": "用户名或密码错误，还可尝试 4 次",
  "data": null,
  "timestamp": 1792913158000
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | number | 业务码，`0` 表示成功 |
| `message` | string | 可直接展示给用户的消息 |
| `data` | any | 业务数据；错误时为 `null` |
| `timestamp` | number | 服务端毫秒时间戳 |

### 1.2 HTTP 状态码

| HTTP | 含义 | 典型业务码 |
| --- | --- | --- |
| `200` | 查询/更新/操作成功 | `0` |
| `201` | 资源创建成功（响应头带 `Location`） | `0` |
| `204` | 成功但无响应体（删除、状态变更、登出、改密） | — |
| `400` | 参数校验失败 | `40000` |
| `401` | 未登录、令牌过期/失效、密码错误、账户禁用/锁定 | `40100`–`40105` |
| `403` | 已登录但缺少权限 | `40300` |
| `404` | 资源或接口不存在 | `40400` |
| `405` | 方法不支持 | `40500` |
| `409` | 唯一性/状态冲突 | `40900` |
| `429` | 限流 | `42900` |
| `500` | 服务端异常 | `50000` |

### 1.3 认证

除以下公开接口外，所有接口都需要：

```http
Authorization: Bearer <accessToken>
```

公开接口：

- `POST /auth/register/code`
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `GET /settings/public`
- `GET /legal/documents`

### 1.4 分页

列表接口返回：

```json
{
  "records": [],
  "total": 42,
  "page": 1,
  "size": 10,
  "pages": 5
}
```

查询参数：`page` 从 1 开始，`size` 范围 `1–100`。

### 1.5 ID 表示

数据库主键为 `BIGINT`，为避免 JavaScript 精度丢失，所有响应中的 `id`、`parentId`、`permissionIds`、`roleIds` 均以**字符串**返回；请求中接受字符串或数字。

### 1.6 时间格式

统一为 `yyyy-MM-dd HH:mm:ss`（服务器时区，默认 `Asia/Shanghai`）。

### 1.7 校验与错误消息

- Bean Validation 失败返回 `40000`，`message` 为中文提示，多个字段用 `；` 连接。
- 业务异常返回对应 HTTP 状态 + 可读消息。
- 不暴露堆栈或 SQL。

---

## 2. 认证接口

### 2.1 发送注册验证码

`POST /auth/register/code` · 公开

请求：

```json
{
  "email": "tester@cognia.local",
  "turnstileToken": "0.xxxxx..."
}
```

响应 `200`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "message": "验证码已发送，请注意查收",
    "expiresInSeconds": 300,
    "resendAfterSeconds": 60,
    "devCode": "981277"
  },
  "timestamp": 1792913118000
}
```

说明：

- `devCode` 仅在 `app.mail.expose-code=true` 时出现，生产环境必须关闭。
- 同一邮箱 60 秒内只能发送一次；每天最多 10 次；同一 IP 每小时最多 30 次。
- 邮箱已注册时返回 `409 / 40900`。
- 控制台关闭注册时返回 `403 / 40300`；关闭「注册邮箱验证」或「验证码开关」时返回 `400 / 40000`。
- 开启「验证码开关」（注册发信人机验证）后，`turnstileToken` 必填；缺失返回 `400 / 40000`，校验失败返回 `400 / 40000`，验证服务不可用返回 `500 / 50000`。
- 开启注册邮箱白名单时，不在白名单内的邮箱返回 `400 / 40000`。

### 2.2 注册

`POST /auth/register` · 公开 · 成功 `201`

请求：

```json
{
  "username": "tester",
  "nickname": "测试用户",
  "email": "tester@cognia.local",
  "password": "Tester@123",
  "code": "981277"
}
```

规则：

- 用户名：字母开头，字母/数字/下划线，3–32 位
- 密码：8–32 位，同时包含字母和数字
- 验证码：6 位数字，5 分钟有效，错误 5 次后作废
- 新用户默认分配 `USER` 角色
- 注册成功后自动记录当前隐私政策/用户协议版本；关闭「注册邮箱验证」时 `code` 可省略
- 开启「注册邮箱验证」前必须启用并配置 SMTP；否则保存设置会返回 `400`
- 开启注册邮箱白名单时，邮箱必须匹配白名单中的邮箱或域名，否则返回 `400 / 40000`
- 控制台关闭注册时返回 `403 / 40300`

响应 `201`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "2114257432301486081",
    "username": "tester",
    "nickname": "测试用户",
    "email": "tester@cognia.local",
    "status": 1,
    "roles": [{ "id": "2114256826019037185", "code": "USER", "name": "普通用户" }],
    "permissions": [],
    "createTime": "2026-10-25 15:26:11"
  },
  "timestamp": 1792913171000
}
```

### 2.3 登录

`POST /auth/login` · 公开

请求：

```json
{
  "account": "admin",
  "password": "Admin@123",
  "turnstileToken": "0.xxxxx...",
  "rememberMe": true
}
```

`account` 支持用户名或邮箱；`rememberMe` 可选，默认 `false`。开启「登录人机验证」后 `turnstileToken` 必填，缺失返回 `400 / 40000`，校验失败返回 `400 / 40000`，验证服务不可用返回 `500 / 50000`。

响应 `200`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "refreshExpiresIn": 2592000,
    "user": { "...": "同 UserResponse" }
  },
  "timestamp": 1792913158000
}
```

- `expiresIn` / `refreshExpiresIn` 单位为秒。
- 连续 5 次密码错误后锁定 15 分钟（`401 / 40105`）。
- 禁用账户返回 `401 / 40104`。

### 2.4 刷新令牌

`POST /auth/refresh` · 公开

请求：

```json
{ "refreshToken": "eyJhbGciOiJIUzUxMiJ9..." }
```

响应结构同登录。Refresh Token **一次性使用**：

- 刷新成功后旧 Refresh Token 立即失效
- 重复使用返回 `401 / 40102`
- 已登出/被撤销返回 `401 / 40102`

### 2.5 当前用户

`GET /auth/me` · 已登录

返回 `UserResponse`，包含 `roles` 与 `permissions`。

### 2.6 登出

`POST /auth/logout` · 已登录 · 成功 `204`

删除当前 Access Token 对应会话及其配对 Refresh Token；该 Access Token 立即失效。

---

## 3. 用户接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/users` | `user:read` | 分页查询用户 |
| `GET` | `/users/{id}` | `user:read` | 用户详情（含权限） |
| `POST` | `/users` | `user:create`（指定角色另需 `user:assign-role`） | 创建用户 |
| `PUT` | `/users/{id}` | `user:update` | 更新资料/状态（不含角色） |
| `DELETE` | `/users/{id}` | `user:delete` | 软删除用户 |
| `PATCH` | `/users/{id}/status` | `user:update` | 启用/禁用 |
| `PUT` | `/users/{id}/roles` | `user:assign-role` | 全量替换角色 |
| `PUT` | `/users/{id}/password` | `user:reset-password` | 管理员重置密码 |
| `GET` | `/users/me` | 已登录 | 我的资料 |
| `PUT` | `/users/me` | 已登录 | 修改我的昵称/头像；手机号需短信验证码绑定，暂不支持直接修改 |
| `PUT` | `/users/me/password` | 已登录 | 修改我的密码 |

### 3.1 查询用户

`GET /users?page=1&size=10&keyword=bob&status=1&roleId=2114...`

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 默认 1 |
| `size` | 否 | 默认 10，最大 100 |
| `keyword` | 否 | 模糊匹配用户名/昵称/邮箱 |
| `status` | 否 | `1` 启用 / `0` 禁用 |
| `roleId` | 否 | 按角色筛选 |

响应中的 `permissions` 在列表场景省略（`non_null`），详情/个人资料会返回。

### 3.2 创建用户

`POST /users` · 成功 `201` + `Location: /api/v1/users/{id}`

```json
{
  "username": "bob",
  "nickname": "Bob",
  "email": "bob@cognia.local",
  "password": "Bob@12345",
  "phone": "13800138000",
  "status": 1,
  "roleIds": ["2114256826019037185"]
}
```

- `roleIds` 省略时默认分配 `USER`。
- 非空 `roleIds` 需要 `user:assign-role`，否则 `403`。

### 3.3 更新用户

`PUT /users/{id}`
只更新非空字段：

```json
{
  "nickname": "Bobby",
  "email": "bob@cognia.local",
  "phone": "13800138000",
  "avatar": "https://...",
  "status": 1,
  "remark": "备注"
}
```

- **不包含角色字段**；角色变更请使用 `PUT /users/{id}/roles`。
- 不能禁用当前登录账户。
- 不能禁用/删除/移除最后一个启用的 `ADMIN`。

### 3.4 删除用户

`DELETE /users/{id}` · 成功 `204`

- 软删除（`is_deleted=true`），用户名与邮箱可重新注册。
- 同时删除用户-角色关联并撤销全部会话。
- 不能删除自己；不能删除最后一个启用的管理员。

### 3.5 状态

`PATCH /users/{id}/status` · 成功 `204`

```json
{ "status": 0 }
```

禁用会立即撤销该用户全部会话。

### 3.6 分配角色

`PUT /users/{id}/roles`

```json
{ "roleIds": ["2114256826019037185"] }
```

全量替换；空数组表示不分配任何角色。变更后撤销该用户全部会话。

### 3.7 重置密码

`PUT /users/{id}/password` · 成功 `204`

```json
{ "newPassword": "NewPass@123" }
```

### 3.8 个人资料与改密

`GET /users/me`、`PUT /users/me`、`PUT /users/me/password`

```json
// PUT /users/me
{ "nickname": "新昵称", "avatar": "/logo.png" }

// PUT /users/me/password
{ "oldPassword": "Tester@123", "newPassword": "Tester@456" }
```

修改密码会撤销所有会话，前端应引导重新登录。

---

## 4. 角色与权限接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/roles?keyword=` | `role:read` | 角色列表（含权限 ID、用户数） |
| `GET` | `/roles/{id}` | `role:read` | 角色详情 |
| `POST` | `/roles` | `role:create` | 创建角色（`201` + `Location`） |
| `PUT` | `/roles/{id}` | `role:update` | 更新角色；可同时更新权限 |
| `DELETE` | `/roles/{id}` | `role:delete` | 删除角色（`204`） |
| `PUT` | `/roles/{id}/permissions` | `role:assign-permission` | 全量替换权限 |
| `GET` | `/permissions` | `permission:read` | 权限目录 |

### 4.1 创建/更新角色

```json
{
  "code": "AUDITOR",
  "name": "审计员",
  "description": "只读审计",
  "sort": 20,
  "status": 1,
  "permissionIds": ["2114...", "2115..."]
}
```

- `code`：大写字母开头，`A-Z0-9_`，2–32 位；内置角色不可修改编码。
- `permissionIds` 省略表示不修改（创建时为无权限）。
- 权限变更、编码变更或禁用角色会撤销该角色下所有用户的会话。
- 内置角色（`ADMIN`/`USER`）不可删除；角色下仍有用户时不可删除（`409`）。

### 4.2 角色响应

```json
{
  "id": "2114257434604158978",
  "code": "AUDITOR",
  "name": "审计员",
  "description": "只读审计",
  "sort": 20,
  "status": 1,
  "builtin": false,
  "permissionIds": ["2114256825926762498"],
  "userCount": 1,
  "createTime": "2026-10-25 15:28:54"
}
```

### 4.3 分配权限

`PUT /roles/{id}/permissions`

```json
{ "permissionIds": ["2114256825926762498", "2114256825960316929"] }
```

### 4.4 权限目录

`GET /permissions` 返回：

```json
[
  {
    "id": "2114256825859653634",
    "code": "console:access",
    "name": "访问管理控制台",
    "type": "MENU",
    "resource": "/console",
    "sort": 10
  },
  {
    "id": "2114256825884819457",
    "code": "user:read",
    "name": "查看用户",
    "type": "API",
    "resource": "/api/v1/users",
    "method": "GET",
    "sort": 20
  }
]
```

`type`：`MENU` / `API` / `BUTTON`。

---

## 5. 系统设置

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/settings/public` | 公开 | 站点品牌、注册/登录开关、上传类型等公开配置 |
| `GET` | `/settings` | `settings:read` | 管理端完整配置（不含 SMTP 明文密码） |
| `PUT` | `/settings` | `settings:update` | 保存系统设置 |
| `POST` | `/settings/smtp/test` | `settings:update` | 使用数据库中已保存的 SMTP 配置发送测试邮件 |
| `GET` | `/settings/mail-templates` | `settings:read` | 邮件模板列表 |
| `PUT` | `/settings/mail-templates/{code}` | `settings:update` | 保存指定邮件模板 |
| `POST` | `/settings/mail-templates/{code}/preview` | `settings:read` | 使用示例变量渲染 HTML 预览 |
| `POST` | `/settings/mail-templates/{code}/reset` | `settings:update` | 恢复系统默认模板 |
| `GET` | `/settings/downloads/public` | 公开 | 客户端下载配置（版本、链接、SHA256） |
| `GET` | `/settings/downloads` | `settings:read` | 管理端下载配置 |
| `PUT` | `/settings/downloads/{code}` | `settings:update` | 更新指定平台的下载配置 |

### 5.1 公开设置

`GET /settings/public`

```json
{
  "siteName": "Cognia",
  "logoUrl": "/logo.png",
  "siteDescription": "Cognia 开源的企业知识库平台",
  "keywords": ["Cognia", "企业知识库", "AI 搜索", "知识管理"],
  "routeSuffix": " - Cognia 开源的企业知识库平台",
  "defaultLanguage": "zhCn",
  "timezone": "Asia/Shanghai",
  "allowedUploadTypes": ["pdf", "docx", "md"],
  "registrationEnabled": true,
  "registrationEmailVerification": true,
  "registrationEmailWhitelistEnabled": false,
  "registerEmailCaptchaEnabled": true,
  "loginCaptchaEnabled": false,
  "appleLoginEnabled": false,
  "googleLoginEnabled": false,
  "turnstileConfigured": true,
  "turnstileSiteKey": "0x4AAAAAAA..."
}
```

前端在每次应用启动/路由首次进入时读取该接口，用于品牌展示、标题后缀、注册页/登录页开关、人机验证、白名单提示、第三方登录按钮和默认语言。

### 5.2 管理端设置

`GET /settings`

```json
{
  "siteName": "Cognia",
  "logoUrl": "/logo.png",
  "siteDescription": "……",
  "keywords": ["Cognia"],
  "routeSuffix": " - Cognia 开源的企业知识库平台",
  "defaultLanguage": "zhCn",
  "timezone": "Asia/Shanghai",
  "allowedUploadTypes": ["pdf", "docx"],
  "registrationEnabled": true,
  "registrationEmailVerification": true,
  "registerEmailCaptchaEnabled": true,
  "loginCaptchaEnabled": false,
  "registrationEmailWhitelistEnabled": false,
  "registrationEmailWhitelist": [],
  "appleLoginEnabled": false,
  "googleLoginEnabled": false,
  "turnstileSiteKey": "0x4AAAAAAA...",
  "turnstileSecretKeyConfigured": true,
  "smtpEnabled": true,
  "smtpHost": "smtp.example.com",
  "smtpPort": 587,
  "smtpUsername": null,
  "smtpPasswordConfigured": false,
  "smtpFrom": null,
  "smtpSsl": false,
  "smtpStarttls": true,
  "smtpAuth": true,
  "updatedAt": "2026-10-25 16:30:00"
}
```

> SMTP 密码永不返回；`smtpPasswordConfigured` 表示数据库中是否已有加密密码。

### 5.3 保存设置

`PUT /settings`（字段均可省略，省略表示保持原值；空字符串/空数组表示清空）

```json
{
  "siteName": "Cognia",
  "logoUrl": "/logo.png",
  "siteDescription": "Cognia 开源的企业知识库平台",
  "keywords": ["Cognia", "企业知识库"],
  "routeSuffix": " - Cognia 开源的企业知识库平台",
  "defaultLanguage": "zhCn",
  "timezone": "Asia/Shanghai",
  "allowedUploadTypes": ["pdf", "docx", "md"],
  "registrationEnabled": true,
  "registrationEmailVerification": true,
  "registerEmailCaptchaEnabled": true,
  "loginCaptchaEnabled": false,
  "registrationEmailWhitelistEnabled": true,
  "registrationEmailWhitelist": ["user@example.com", "@example.com"],
  "appleLoginEnabled": false,
  "googleLoginEnabled": false,
  "turnstileSiteKey": "0x4AAAAAAA...",
  "turnstileSecretKey": "0x4AAAAAAA...",
  "clearTurnstileSecretKey": false,
  "smtpEnabled": true,
  "smtpHost": "smtp.example.com",
  "smtpPort": 465,
  "smtpUsername": "no-reply@example.com",
  "smtpPassword": "authorization-code",
  "smtpFrom": "no-reply@example.com",
  "smtpSsl": true,
  "smtpStarttls": false,
  "smtpAuth": true,
  "clearSmtpPassword": false
}
```

规则：

- `smtpPassword` 留空表示保持原密码；`clearSmtpPassword=true` 显式清除。
- 启用 SMTP 时必须填写主机；`allowedUploadTypes` 至少保留一种。
- 开启 `registrationEmailVerification` 时必须先启用 SMTP 且填写主机，否则返回 `400`。
- 开启 `registerEmailCaptchaEnabled` / `loginCaptchaEnabled` 前必须同时配置 Turnstile Site Key 与 Secret Key。
- 开启 `registrationEmailWhitelistEnabled` 时白名单不能为空；条目支持完整邮箱、`@example.com`、`example.com`（域名含子域名）。
- 密码与 Turnstile Secret Key 使用 AES-GCM 加密存储；接口只返回 `smtpPasswordConfigured` / `turnstileSecretKeyConfigured`。
- Turnstile 只有 Site Key 与 Secret Key **同时配置**时可用；「验证码开关」控制发送注册验证码前是否校验，「登录人机验证」控制登录时是否校验；清除密钥时需同时清空 Site Key 并关闭相关开关。
- 保存后立即生效，无需重启；前端会重新拉取公开设置更新品牌与开关。
- 语言仅支持 `zhCn` / `enUs`；时区使用 IANA 名称（如 `Asia/Shanghai`），控制台以 Select 下拉方式提供常用 IANA 时区。

### 5.4 测试邮件

`POST /settings/smtp/test`

```json
{ "recipient": "admin@example.com" }
```

使用数据库中已保存的配置发送；SMTP 未启用/未配置时返回 `400 / 40000`，发送失败返回 `500 / 50000`。主题与正文读取 `SMTP_TEST` 邮件模板。

### 5.5 邮件模板

`GET /settings/mail-templates`

```json
[
  {
    "code": "REGISTER_CODE",
    "name": "注册验证码邮件",
    "description": "注册账户时发送的邮箱验证码（HTML）",
    "subject": "【{{siteName}}】邮箱注册验证码",
    "content": "<h1>验证您的邮箱</h1><p>验证码：{{code}}</p>...",
    "placeholders": ["siteName", "email", "code", "ttlMinutes", "expiresAt", "year"],
    "updatedAt": "2026-10-25 18:15:00"
  },
  {
    "code": "SMTP_TEST",
    "name": "SMTP 测试邮件",
    "description": "控制台发送测试邮件时使用的模板（HTML）",
    "subject": "【{{siteName}}】SMTP 配置测试",
    "content": "<h1>SMTP 配置测试</h1><p>这是一封来自 {{siteName}} 的邮件。</p>...",
    "placeholders": ["siteName", "recipient", "sentAt", "year"],
    "updatedAt": "2026-10-25 18:15:00"
  }
]
```

`PUT /settings/mail-templates/REGISTER_CODE`

```json
{
  "subject": "【{{siteName}}】邮箱注册验证码",
  "content": "<h1>验证您的邮箱</h1><p>验证码：<strong>{{code}}</strong></p><p>有效期：{{ttlMinutes}} 分钟（{{expiresAt}} 前）。</p>"
}
```

规则：

- 邮件模板正文为 **HTML 片段**，系统会套用表格布局、内联样式和 600px 最大宽度的邮件外壳，兼容 Gmail、Outlook、Apple Mail 等主流客户端；同时自动生成纯文本副本（`multipart/alternative`）。
- 主题最长 255 字符且不能包含换行，正文最长 20000 字符；正文必须是 HTML 片段（至少包含一个标签）。
- 正文中的 HTML 会原样发送，变量值会做 HTML 转义；不要依赖外部 CSS、JavaScript 或 `flex/grid` 布局。
- 变量使用 `{{name}}`，保存时会校验变量是否属于该模板；未知变量返回 `400 / 40000`。
- `REGISTER_CODE` 可用变量：`siteName`、`email`、`code`、`ttlMinutes`、`expiresAt`、`year`。
- `SMTP_TEST` 可用变量：`siteName`、`recipient`、`sentAt`、`year`。
- 保存后下一次发信立即使用新模板，无需重启。
- `POST /settings/mail-templates/{code}/reset` 恢复系统默认内容。
- 前端「系统设置 → 邮件服务 → 邮件模板」提供 HTML 源码高亮编辑、变量插入、后端同款 HTML 预览与恢复默认。

预览：

`POST /settings/mail-templates/REGISTER_CODE/preview`

```json
{
  "subject": "【{{siteName}}】邮箱注册验证码",
  "content": "<h1>验证您的邮箱</h1><p>验证码：<strong>{{code}}</strong></p>"
}
```

响应：

```json
{
  "code": 0,
  "data": {
    "subject": "【Cognia】邮箱注册验证码",
    "html": "<!DOCTYPE html>...",
    "text": "验证您的邮箱\n验证码：123456\n..."
  }
}
```

预览使用示例变量（`code=123456`、`email=user@example.com`、`ttlMinutes=5` 等），不发送邮件。

### 5.6 客户端下载

`GET /settings/downloads/public`

```json
[
  {
    "code": "MACOS_ARM64",
    "name": "macOS dmg（Apple 芯片）",
    "platform": "macos",
    "arch": "arm64",
    "packageType": "dmg",
    "version": "1.2.0",
    "downloadUrl": "https://download.example.com/Cognia-1.2.0-arm64.dmg",
    "sha256": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "fileSize": 134217728,
    "enabled": true,
    "updatedAt": "2026-10-26 14:30:00"
  }
]
```

`PUT /settings/downloads/MACOS_ARM64`

```json
{
  "name": "macOS dmg（Apple 芯片）",
  "version": "1.2.0",
  "downloadUrl": "https://download.example.com/Cognia-1.2.0-arm64.dmg",
  "sha256": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "fileSize": 134217728,
  "enabled": true
}
```

规则：

- 默认内置 10 个构建：
  - Windows：`WINDOWS_X64`、`WINDOWS_ARM64`（exe）
  - macOS：`MACOS_ARM64`、`MACOS_X64`（dmg）
  - Linux：`LINUX_X64`、`LINUX_APPIMAGE_ARM64`（AppImage），`LINUX_DEB_AMD64`、`LINUX_DEB_ARM64`（deb），`LINUX_RPM_AMD64`、`LINUX_RPM_ARM64`（rpm）
- `packageType` 为 `exe` / `dmg` / `appimage` / `deb` / `rpm`，由平台与构建决定，不支持在控制台修改。
- `downloadUrl` 仅接受 `http://` / `https://`，留空表示暂未配置。
- `sha256` 必须是 64 位十六进制字符串；`fileSize` 单位为字节。
- 公开接口只返回 `enabled=true` 的下载项；下载页按 Windows / macOS / Linux 三个平台分组，鼠标悬浮展开架构与格式。
- 下载页会检测系统与 CPU 架构，在对应平台按钮和安装包选项上显示推荐徽标。
- 点击已配置的下载项后开始下载，并弹出窗口展示版本号、文件大小和完整 SHA256。

---

## 5.9 团队

团队是知识库的归属单位。用户必须先创建或加入团队；不提供个人频道。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/teams/mine` | 已登录 | 当前用户加入的团队 |
| `GET` | `/teams/limits` | 已登录 | 当前用户创建/加入数量与系统上限 |
| `GET` | `/teams/{id}` | 已登录 | 团队成员查看详情、成员与公开频道 |
| `POST` | `/teams` | 已登录 | 创建团队，创建者为 `OWNER` |
| `GET` | `/teams/invitations` | 已登录 | 当前用户收到的待处理邀请 |
| `POST` | `/teams/invitations/{id}/accept` | 已登录 | 接受邀请，加入团队 |
| `POST` | `/teams/invitations/{id}/decline` | 已登录 | 拒绝邀请 |
| `GET` | `/teams/{id}/invitations` | 团队创建者 | 团队待处理邀请 |
| `POST` | `/teams/{id}/invitations` | 团队创建者 | 邀请用户，请求体 `{ "userId": "..." }` |
| `GET` | `/teams/{id}/invitees` | 团队创建者 | 搜索可邀请的用户，支持 `keyword` |
| `PUT` | `/teams/{id}/readme` | 团队创建者 | 更新团队 README（Markdown） |
| `PUT` | `/teams/{id}/members/{userId}/role` | 团队创建者 | 设置成员角色：`MANAGER` / `EDITOR` / `MEMBER` |
| `POST` | `/teams/{id}/channels` | 创建者/管理者/编辑 | 创建频道 |
| `PUT` | `/teams/{id}/channels/{channelId}` | 创建者/管理者/编辑 | 修改频道 |
| `DELETE` | `/teams/{id}/channels/{channelId}` | 创建者/管理者；编辑仅限自建 | 删除频道 |
| `POST` | `/teams/{id}/channels/{channelId}/pin` | 创建者/管理者 | 置顶/取消置顶，最多 6 个 |
| `PUT` | `/teams/{id}` | 团队创建者 | 更新团队名称、介绍、Logo、自定义链接（最多 4 个）、加入申请开关、新成员默认权限 |
| `DELETE` | `/teams/{id}` | 团队创建者 | 删除团队及其成员、频道、邀请 |
| `POST` | `/teams/{id}/transfer` | 团队创建者 | 转让团队创建者 |
| `DELETE` | `/teams/{id}/members/{userId}` | 团队创建者 | 移除成员（不可移除创建者） |

- 创建上限默认 5，加入上限默认 10，控制台「系统设置 → 团队设置」可修改。
- 创建团队计入创建上限；加入团队计入加入上限，自己创建的团队不计入加入上限。
- `publicChannels` 当前为空数组，频道、文档上传与参数配置将在后续版本实现。
- 团队采用邀请制：不提供团队搜索或公开团队列表。创建者在团队详情/成员设置中搜索用户并发送邀请；被邀请人在「我的团队」页面接受或拒绝。
- 团队详情仅团队成员可访问；邀请卡片展示团队名称、Logo、介绍和邀请人。
- 团队详情包含 `readme`、`channels`、`viewerRole` 和频道管理权限字段；`channels` 中的 `pinned` 表示 Popular Channel。
- 团队详情前端提供 介绍 / 频道 / 成员 / 设置 四个标签；设置仅团队创建者可见。
- 概览显示 README、置顶频道（最多 6 个）和热门公开频道（最多 10 个，排除已置顶频道）；未配置的区块不展示。
- 团队详情允许非成员预览；非成员只能看到公开频道，People 始终展示全部成员；设置和成员管理仅创建者可见。
- 频道创建、编辑、删除与置顶集中在独立的 `/teams/{id}/channels/manage` 页面，频道 Tab 只读。
- 成员角色：`OWNER` 创建者、`MANAGER` 管理者（创建/修改/删除频道、置顶）、`EDITOR` 编辑（创建/修改频道，仅删除自己创建的频道）、`MEMBER` 成员（查看）。
- 接受邀请时校验加入团队上限；创建团队时校验创建团队上限。数量上限由控制台「团队设置」配置。

## 6. 法律条款与同意

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/legal/documents` | 公开 | 当前隐私政策、用户协议与开放源代码许可 |
| `GET` | `/legal/consent` | 已登录 | 当前用户同意状态与是否需要重新同意 |
| `POST` | `/legal/consent` | 已登录 | 同意当前版本 |
| `PUT` | `/legal/{type}` | `settings:update` | 保存并发布修订，`type` 为 `PRIVACY` / `TERMS` / `OPEN_SOURCE` |

### 6.1 文档列表

`GET /legal/documents`

```json
[
  {
    "id": "2",
    "type": "PRIVACY",
    "title": "隐私政策",
    "content": "# Cognia 隐私政策\n\n## 一、信息收集……",
    "version": 1,
    "publishedAt": "2026-10-25 16:20:00",
    "updatedAt": "2026-10-25 16:20:00"
  }
]
```

> `content` 字段使用 Markdown；控制台「法律条款」提供编辑与 Markdown 预览，公开阅读页 `/legal/privacy`、`/legal/terms`、`/legal/licenses` 使用同一渲染组件。`OPEN_SOURCE` 仅用于展示开源许可，不参与用户同意校验，也不会在注册/登录时要求用户同意。

### 6.2 同意状态

`GET /legal/consent`

> 仅返回需要同意的 `PRIVACY`、`TERMS` 文档；`OPEN_SOURCE` 不进入同意状态。

```json
{
  "documents": [{ "...": "同 6.1" }],
  "consents": { "PRIVACY": 1, "TERMS": 1 },
  "requiresConsent": false
}
```

`requiresConsent=true` 时前端（App.vue）弹出不可通过 Esc/点击外部关闭的 Dialog；用户必须同意或选择退出。

### 6.3 提交同意

`POST /legal/consent`

```json
{ "privacyVersion": 1, "termsVersion": 1 }
```

- 版本必须与当前文档一致，否则 `409 / 40900`「协议内容已更新」。
- 同意记录写入 `sys_user_consent`；响应结构同 6.2。
- 注册成功时自动记录当前版本，因此新用户首次登录不会再次弹窗；后续修订会再次要求同意。

### 6.4 发布修订

`PUT /legal/PRIVACY`

```json
{
  "title": "隐私政策",
  "content": "# 隐私政策\n\n## 一、信息收集\n\n修订后的完整内容……"
}
```

- 内容发生变化时 `version + 1` 并更新 `publishedAt`；仅改标题不增加版本。
- 发布后所有用户（包括当前管理员）的旧同意版本失效，下次进入应用/登录时弹出重新同意 Dialog。
- 前端设置页会二次确认，提示「所有用户需要重新同意」。

---

## 7. CORS

后端通过 `app.cors.*` 统一配置，并在 Spring Security 过滤器链前处理预检请求：

```http
OPTIONS /api/v1/users HTTP/1.1
Origin: http://localhost:5173
Access-Control-Request-Method: GET
```

允许的方法：`GET, POST, PUT, PATCH, DELETE, OPTIONS`；允许携带凭证；暴露 `Authorization`、`Content-Disposition`。生产环境请将 `CORS_ALLOWED_ORIGINS` 设置为真实前端域名。

---

## 8. 错误码

| code | HTTP | 说明 |
| --- | --- | --- |
| `0` | 200/201 | 成功 |
| `40000` | 400 | 参数校验失败 |
| `40100` | 401 | 未认证或令牌失效 |
| `40101` | 401 | Access Token 过期/会话被撤销 |
| `40102` | 401 | 令牌无效（签名、类型、Refresh 复用） |
| `40103` | 401 | 用户名或密码错误 |
| `40104` | 401 | 账户被禁用 |
| `40105` | 401 | 登录失败次数过多，账户锁定 |
| `40300` | 403 | 权限不足 |
| `40400` | 404 | 资源/接口不存在 |
| `40500` | 405 | 方法不支持 |
| `40900` | 409 | 唯一性冲突 / 状态冲突 / 最后一个管理员保护 |
| `42900` | 429 | 验证码/登录限流 |
| `50000` | 500 | 服务端异常 |

---

## 9. 快速流程示例

```bash
BASE=http://localhost:8080/api/v1

# 1) 管理员登录
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"Admin@123"}' | jq -r '.data.accessToken')

# 2) 查看当前用户
curl -s $BASE/auth/me -H "Authorization: Bearer $TOKEN" | jq

# 3) 发送注册验证码（开发模式返回 devCode）
CODE=$(curl -s -X POST $BASE/auth/register/code \
  -H 'Content-Type: application/json' \
  -d '{"email":"tester@cognia.local"}' | jq -r '.data.devCode')

# 4) 注册
curl -s -X POST $BASE/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"tester\",\"email\":\"tester@cognia.local\",\"password\":\"Tester@123\",\"code\":\"$CODE\"}" | jq

# 5) 管理员分页查询
curl -s "$BASE/users?page=1&size=10&keyword=tester" \
  -H "Authorization: Bearer $TOKEN" | jq

# 6) 创建角色
ROLE_ID=$(curl -s -X POST $BASE/roles \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"AUDITOR","name":"审计员","status":1}' | jq -r '.data.id')

# 7) 分配权限（先在 /permissions 中查询权限 ID）
curl -s -X PUT $BASE/roles/$ROLE_ID/permissions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"permissionIds":[]}' | jq
```
