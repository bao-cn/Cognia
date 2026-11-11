-- Cognia RBAC schema (PostgreSQL)
-- The application is idempotent: every statement can be executed on each boot.

CREATE TABLE IF NOT EXISTS sys_user
(
    id              BIGINT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL,
    password        VARCHAR(100) NOT NULL,
    nickname        VARCHAR(64),
    email           VARCHAR(128) NOT NULL,
    phone           VARCHAR(32),
    avatar          VARCHAR(512),
    status          SMALLINT     NOT NULL DEFAULT 1,
    last_login_time TIMESTAMP,
    last_login_ip   VARCHAR(64),
    create_time     TIMESTAMP,
    update_time     TIMESTAMP,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    remark          VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_username
    ON sys_user (username) WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_email
    ON sys_user (email) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_sys_user_status ON sys_user (status);

CREATE TABLE IF NOT EXISTS sys_role
(
    id          BIGINT PRIMARY KEY,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    sort        INTEGER     NOT NULL DEFAULT 0,
    status      SMALLINT    NOT NULL DEFAULT 1,
    builtin     BOOLEAN     NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
    remark      VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_code
    ON sys_role (code) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS sys_permission
(
    id          BIGINT PRIMARY KEY,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    type        VARCHAR(16)  NOT NULL DEFAULT 'API',
    resource    VARCHAR(255),
    method      VARCHAR(10),
    parent_id   BIGINT,
    sort        INTEGER      NOT NULL DEFAULT 0,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    remark      VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_permission_code
    ON sys_permission (code) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS sys_user_role
(
    id         BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    role_id    BIGINT NOT NULL,
    create_time TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_role
    ON sys_user_role (user_id, role_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_role ON sys_user_role (role_id);

CREATE TABLE IF NOT EXISTS sys_role_permission
(
    id            BIGINT PRIMARY KEY,
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time   TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_permission
    ON sys_role_permission (role_id, permission_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_permission_permission ON sys_role_permission (permission_id);

-- ---------------------------------------------------------------------------
-- Site settings (single row, id = 1). SMTP credentials live here instead of
-- application.yaml; the password is stored AES-GCM encrypted by the backend.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_site_setting
(
    id                               BIGINT PRIMARY KEY,
    logo_url                         VARCHAR(512),
    site_name                        VARCHAR(64)  NOT NULL DEFAULT 'Cognia',
    site_description                 VARCHAR(512),
    site_keywords                    VARCHAR(512),
    route_suffix                     VARCHAR(128),
    default_language                 VARCHAR(16)  NOT NULL DEFAULT 'zhCn',
    timezone                         VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    allowed_upload_types             VARCHAR(512),
    registration_enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    registration_email_verification  BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Legacy column name: now means "require human verification before sending
    -- a registration email code".
    verification_code_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    login_captcha_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    registration_email_whitelist_enabled BOOLEAN  NOT NULL DEFAULT FALSE,
    registration_email_whitelist     VARCHAR(2000),
    apple_login_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    google_login_enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    turnstile_site_key               VARCHAR(255),
    turnstile_secret_key             VARCHAR(1024),
    smtp_enabled                     BOOLEAN      NOT NULL DEFAULT FALSE,
    smtp_host                        VARCHAR(255),
    smtp_port                        INTEGER               DEFAULT 587,
    smtp_username                    VARCHAR(255),
    smtp_password                    VARCHAR(1024),
    smtp_from                        VARCHAR(255),
    smtp_ssl                         BOOLEAN      NOT NULL DEFAULT FALSE,
    smtp_starttls                    BOOLEAN      NOT NULL DEFAULT TRUE,
    smtp_auth                        BOOLEAN      NOT NULL DEFAULT TRUE,
    team_creation_limit              INTEGER      NOT NULL DEFAULT 5,
    team_join_limit                  INTEGER      NOT NULL DEFAULT 10,
    updated_by                       BIGINT,
    updated_at                       TIMESTAMP,
    create_time                      TIMESTAMP
);

-- Existing installations predate the Turnstile / captcha / whitelist columns.
ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS turnstile_site_key VARCHAR(255);
ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS turnstile_secret_key VARCHAR(1024);
ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS login_captcha_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS registration_email_whitelist_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS registration_email_whitelist VARCHAR(2000);
ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS team_creation_limit INTEGER NOT NULL DEFAULT 5;
ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS team_join_limit INTEGER NOT NULL DEFAULT 10;

-- Registration email verification requires a usable outbound mail service.
UPDATE sys_site_setting
SET registration_email_verification = FALSE
WHERE registration_email_verification = TRUE
  AND (smtp_enabled = FALSE OR smtp_host IS NULL OR smtp_host = '');

INSERT INTO sys_site_setting (id, logo_url, site_name, site_description, site_keywords, route_suffix,
                              default_language, timezone, allowed_upload_types,
                              registration_enabled, registration_email_verification, verification_code_enabled,
                              apple_login_enabled, google_login_enabled,
                              smtp_enabled, smtp_port, smtp_ssl, smtp_starttls, smtp_auth,
                              create_time, updated_at)
VALUES (1, '/logo.png', 'Cognia', 'Cognia 开源的企业知识库平台',
        'Cognia,企业知识库,AI 搜索,知识管理',
        ' - Cognia 开源的企业知识库平台', 'zhCn', 'Asia/Shanghai',
        'pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,html,csv',
        TRUE, FALSE, TRUE, FALSE, FALSE, FALSE, 587, FALSE, TRUE, TRUE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Legal documents and per-user consent versions.
-- Bumping `version` (on content change) invalidates existing consents and
-- triggers the re-consent dialog in the SPA.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_legal_document
(
    id           BIGINT PRIMARY KEY,
    doc_type     VARCHAR(16)  NOT NULL UNIQUE,
    title        VARCHAR(128) NOT NULL,
    content      TEXT         NOT NULL,
    version      INTEGER      NOT NULL DEFAULT 1,
    published_at TIMESTAMP,
    updated_by   BIGINT,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_consent
(
    id           BIGINT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    doc_type     VARCHAR(16) NOT NULL,
    version      INTEGER     NOT NULL,
    consented_at TIMESTAMP   NOT NULL,
    CONSTRAINT uk_sys_user_consent UNIQUE (user_id, doc_type)
);

CREATE INDEX IF NOT EXISTS idx_sys_user_consent_user ON sys_user_consent (user_id);

-- ---------------------------------------------------------------------------
-- Teams and memberships. A user's personal space is represented by a team
-- with a single owner, so every user must create/join a team before using
-- channel features.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_team
(
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    slug        VARCHAR(64)  NOT NULL,
    description VARCHAR(512),
    logo_url    TEXT,
    readme      TEXT,
    custom_links TEXT,
    join_requests_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    default_member_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    invite_code VARCHAR(16),
    owner_id    BIGINT       NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

ALTER TABLE sys_team ADD COLUMN IF NOT EXISTS invite_code VARCHAR(16);
ALTER TABLE sys_team ADD COLUMN IF NOT EXISTS readme TEXT;
ALTER TABLE sys_team ALTER COLUMN logo_url TYPE TEXT;
ALTER TABLE sys_team ADD COLUMN IF NOT EXISTS custom_links TEXT;
ALTER TABLE sys_team ADD COLUMN IF NOT EXISTS join_requests_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_team ADD COLUMN IF NOT EXISTS default_member_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER';
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_team_slug ON sys_team (slug);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_team_invite_code ON sys_team (invite_code) WHERE invite_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sys_team_owner ON sys_team (owner_id);

CREATE TABLE IF NOT EXISTS sys_team_member
(
    id         BIGINT PRIMARY KEY,
    team_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at  TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_team_member ON sys_team_member (team_id, user_id);
CREATE INDEX IF NOT EXISTS idx_sys_team_member_user ON sys_team_member (user_id);

CREATE TABLE IF NOT EXISTS sys_team_channel
(
    id           BIGINT PRIMARY KEY,
    team_id      BIGINT       NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    description  VARCHAR(512),
    is_public    BOOLEAN      NOT NULL DEFAULT TRUE,
    pinned       BOOLEAN      NOT NULL DEFAULT FALSE,
    pinned_order INTEGER      NOT NULL DEFAULT 0,
    created_by   BIGINT       NOT NULL,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_team_channel_team ON sys_team_channel (team_id);
CREATE INDEX IF NOT EXISTS idx_sys_team_channel_pinned ON sys_team_channel (team_id, pinned, pinned_order);

CREATE TABLE IF NOT EXISTS sys_team_invitation
(
    id          BIGINT PRIMARY KEY,
    team_id     BIGINT      NOT NULL,
    inviter_id  BIGINT      NOT NULL,
    invitee_id  BIGINT      NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_team_invitation_pending
    ON sys_team_invitation (team_id, invitee_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_sys_team_invitation_invitee
    ON sys_team_invitation (invitee_id, status);
CREATE INDEX IF NOT EXISTS idx_sys_team_invitation_team
    ON sys_team_invitation (team_id, status);

INSERT INTO sys_legal_document (id, doc_type, title, content, version, published_at, create_time, update_time)
VALUES (1, 'PRIVACY', '隐私政策', $privacy$
# Cognia 隐私政策

**生效日期：2026-01-01**

## 一、信息收集

我们仅收集为您提供知识库服务所必需的信息，包括账户信息（用户名、邮箱、昵称）、登录日志（IP、浏览器标识）以及您主动上传的文档内容。

## 二、信息使用

上述信息用于身份认证、权限控制、服务改进与安全审计，不会用于与知识库服务无关的用途。

## 三、信息存储与保护

数据存储于您所在组织部署的服务器中。密码使用 BCrypt 加密，SMTP 凭据加密存储，访问令牌采用服务器端会话白名单管理。

## 四、信息共享

除法律法规要求或您明确授权外，我们不会向第三方共享您的个人信息。

## 五、您的权利

您可以访问、更正或删除您的账户信息，也可以联系管理员注销账户。

## 六、政策更新

本政策修订后，系统会要求您重新阅读并同意。不同意的，您将无法继续使用本服务。
$privacy$, 1, NOW(), NOW(), NOW())
ON CONFLICT (doc_type) DO NOTHING;

INSERT INTO sys_legal_document (id, doc_type, title, content, version, published_at, create_time, update_time)
VALUES (2, 'TERMS', '用户协议', $terms$
# Cognia 用户协议

**生效日期：2026-01-01**

## 一、服务说明

Cognia 为企业提供知识库管理、检索与智能问答能力。您应在组织授权的范围内使用本服务。

## 二、账户与安全

您应妥善保管账户密码，不得将账户转借他人。因您保管不当造成的损失由您自行承担。

## 三、使用规范

您不得上传违法、侵权或危害系统安全的内容，不得利用本服务从事任何违反法律法规的活动。

## 四、内容与知识产权

您上传的文档归您或原权利人所有。您应确保拥有合法授权。

## 五、服务变更与终止

我们可能根据业务需要调整或终止部分功能，重大变更将提前通知。

## 六、协议更新

本协议修订后，系统会要求您重新阅读并同意。不同意的，您将无法继续使用本服务。
$terms$, 1, NOW(), NOW(), NOW())
ON CONFLICT (doc_type) DO NOTHING;

INSERT INTO sys_legal_document (id, doc_type, title, content, version, published_at, create_time, update_time)
VALUES (3, 'OPEN_SOURCE', '开放源代码许可', $license$
# Cognia 开放源代码许可

本页面列出 Cognia 使用或分发的主要开源软件。各组件的完整许可文本、版权声明与源码地址以其官方仓库为准。

## 核心框架

- Vue.js — MIT License
- Vite — MIT License
- Electron — MIT License
- Spring Boot — Apache License 2.0
- PostgreSQL — PostgreSQL License
- MyBatis-Plus — Apache License 2.0

## 界面与内容

- Tailwind CSS — MIT License
- Reka UI — MIT License
- Tabler Icons — MIT License
- Shiki — MIT License
- KaTeX — MIT License
- Markdown-It — MIT License

## 说明

本页面仅用于开源许可信息展示，不构成需要用户确认的协议。若需获取某一组件的完整许可文本，请访问对应项目的官方代码仓库。
$license$, 1, NOW(), NOW(), NOW())
ON CONFLICT (doc_type) DO NOTHING;


-- ---------------------------------------------------------------------------
-- Configurable HTML mail templates. Content is an HTML fragment; the server
-- wraps it in a table-based, inline-styled shell for mainstream mail clients.
-- Placeholders use {{name}} and are rendered before sending.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_mail_template
(
    id          BIGINT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    subject     VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    updated_by  BIGINT,
    updated_at  TIMESTAMP,
    create_time TIMESTAMP
);

INSERT INTO sys_mail_template (id, code, name, description, subject, content, create_time, updated_at)
VALUES (1, 'REGISTER_CODE', '注册验证码邮件', '注册账户时发送的邮箱验证码（HTML）',
        '【{{siteName}}】邮箱注册验证码', $register_html$
<h1 style="margin:0 0 16px;font-size:22px;line-height:1.35;color:#111827;">验证您的邮箱</h1>
<p style="margin:0 0 16px;">您好！</p>
<p style="margin:0 0 16px;">您正在注册 {{siteName}} 账户，本次验证码为：</p>
<div style="margin:24px 0;padding:16px 20px;background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;text-align:center;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:28px;font-weight:700;letter-spacing:8px;color:#111827;">{{code}}</div>
<p style="margin:0 0 16px;">验证码 {{ttlMinutes}} 分钟内有效（{{expiresAt}} 前）。如非本人操作，请忽略本邮件。</p>
<p style="margin:0;">—— {{siteName}} 团队</p>
$register_html$, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO sys_mail_template (id, code, name, description, subject, content, create_time, updated_at)
VALUES (2, 'SMTP_TEST', 'SMTP 测试邮件', '控制台发送测试邮件时使用的模板（HTML）',
        '【{{siteName}}】SMTP 配置测试', $smtp_html$
<h1 style="margin:0 0 16px;font-size:22px;line-height:1.35;color:#111827;">SMTP 配置测试</h1>
<p style="margin:0 0 16px;">这是一封来自 {{siteName}} 的 SMTP 测试邮件。</p>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:16px 0;border-collapse:collapse;">
  <tr>
    <td style="padding:8px 0;width:96px;color:#6b7280;font-size:13px;">收件人</td>
    <td style="padding:8px 0;color:#111827;font-size:14px;">{{recipient}}</td>
  </tr>
  <tr>
    <td style="padding:8px 0;color:#6b7280;font-size:13px;">发送时间</td>
    <td style="padding:8px 0;color:#111827;font-size:14px;">{{sentAt}}</td>
  </tr>
</table>
<p style="margin:0 0 16px;">如果您收到此邮件，说明控制台中的邮件服务配置已生效。</p>
<p style="margin:0;">—— {{siteName}} 团队</p>
$smtp_html$, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- Migrate the previous plain-text defaults to HTML. A customized template that
-- starts with another phrase is left untouched.
UPDATE sys_mail_template
SET description = '注册账户时发送的邮箱验证码（HTML）',
    content = $register_html$
<h1 style="margin:0 0 16px;font-size:22px;line-height:1.35;color:#111827;">验证您的邮箱</h1>
<p style="margin:0 0 16px;">您好！</p>
<p style="margin:0 0 16px;">您正在注册 {{siteName}} 账户，本次验证码为：</p>
<div style="margin:24px 0;padding:16px 20px;background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;text-align:center;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:28px;font-weight:700;letter-spacing:8px;color:#111827;">{{code}}</div>
<p style="margin:0 0 16px;">验证码 {{ttlMinutes}} 分钟内有效（{{expiresAt}} 前）。如非本人操作，请忽略本邮件。</p>
<p style="margin:0;">—— {{siteName}} 团队</p>
$register_html$, updated_at = NOW()
WHERE code = 'REGISTER_CODE' AND trim(content) LIKE '您好！%';

UPDATE sys_mail_template
SET description = '控制台发送测试邮件时使用的模板（HTML）',
    content = $smtp_html$
<h1 style="margin:0 0 16px;font-size:22px;line-height:1.35;color:#111827;">SMTP 配置测试</h1>
<p style="margin:0 0 16px;">这是一封来自 {{siteName}} 的 SMTP 测试邮件。</p>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:16px 0;border-collapse:collapse;">
  <tr>
    <td style="padding:8px 0;width:96px;color:#6b7280;font-size:13px;">收件人</td>
    <td style="padding:8px 0;color:#111827;font-size:14px;">{{recipient}}</td>
  </tr>
  <tr>
    <td style="padding:8px 0;color:#6b7280;font-size:13px;">发送时间</td>
    <td style="padding:8px 0;color:#111827;font-size:14px;">{{sentAt}}</td>
  </tr>
</table>
<p style="margin:0 0 16px;">如果您收到此邮件，说明控制台中的邮件服务配置已生效。</p>
<p style="margin:0;">—— {{siteName}} 团队</p>
$smtp_html$, updated_at = NOW()
WHERE code = 'SMTP_TEST' AND trim(content) LIKE '这是一封来自%';

-- ---------------------------------------------------------------------------
-- Desktop client download artifacts. The admin console can update version,
-- URL and SHA-256 checksum for each build.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_download_asset
(
    id           BIGINT PRIMARY KEY,
    code         VARCHAR(32)  NOT NULL UNIQUE,
    name         VARCHAR(64)  NOT NULL,
    platform     VARCHAR(16)  NOT NULL,
    arch         VARCHAR(16)  NOT NULL,
    package_type VARCHAR(16)  NOT NULL DEFAULT '',
    version      VARCHAR(32)  NOT NULL DEFAULT '1.0.0',
    download_url VARCHAR(1024),
    sha256       VARCHAR(64),
    file_size    BIGINT,
    sort         INTEGER      NOT NULL DEFAULT 0,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by   BIGINT,
    updated_at   TIMESTAMP,
    create_time  TIMESTAMP
);

INSERT INTO sys_download_asset (id, code, name, platform, arch, version, sort, enabled, create_time, updated_at)
VALUES (1, 'MACOS_ARM64', 'macOS（Apple 芯片）', 'macos', 'arm64', '1.0.0', 1, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
INSERT INTO sys_download_asset (id, code, name, platform, arch, version, sort, enabled, create_time, updated_at)
VALUES (2, 'MACOS_X64', 'macOS（Intel）', 'macos', 'x64', '1.0.0', 2, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
INSERT INTO sys_download_asset (id, code, name, platform, arch, version, sort, enabled, create_time, updated_at)
VALUES (3, 'WINDOWS_X64', 'Windows（64 位）', 'windows', 'x64', '1.0.0', 3, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
INSERT INTO sys_download_asset (id, code, name, platform, arch, version, sort, enabled, create_time, updated_at)
VALUES (4, 'LINUX_X64', 'Linux（64 位）', 'linux', 'x64', '1.0.0', 4, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
ALTER TABLE sys_download_asset ADD COLUMN IF NOT EXISTS package_type VARCHAR(16) NOT NULL DEFAULT '';

-- Expand the original four builds into the full platform/format matrix.
UPDATE sys_download_asset SET name = 'macOS dmg（Apple 芯片）', arch = 'arm64', package_type = 'dmg', sort = 3 WHERE code = 'MACOS_ARM64' AND package_type = '';
UPDATE sys_download_asset SET name = 'macOS dmg（Intel）', arch = 'x64', package_type = 'dmg', sort = 4 WHERE code = 'MACOS_X64' AND package_type = '';
UPDATE sys_download_asset SET name = 'Windows exe（x64）', arch = 'x64', package_type = 'exe', sort = 1 WHERE code = 'WINDOWS_X64' AND package_type = '';
UPDATE sys_download_asset SET name = 'Linux AppImage（amd64）', arch = 'amd64', package_type = 'appimage', sort = 5 WHERE code = 'LINUX_X64' AND package_type = '';

INSERT INTO sys_download_asset (id, code, name, platform, arch, package_type, version, sort, enabled, create_time, updated_at)
VALUES (5, 'WINDOWS_ARM64', 'Windows exe（ARM64）', 'windows', 'arm64', 'exe', '1.0.0', 2, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
INSERT INTO sys_download_asset (id, code, name, platform, arch, package_type, version, sort, enabled, create_time, updated_at)
VALUES (6, 'LINUX_APPIMAGE_ARM64', 'Linux AppImage（arm64）', 'linux', 'arm64', 'appimage', '1.0.0', 6, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
INSERT INTO sys_download_asset (id, code, name, platform, arch, package_type, version, sort, enabled, create_time, updated_at)
VALUES (7, 'LINUX_DEB_AMD64', 'Linux deb（amd64）', 'linux', 'amd64', 'deb', '1.0.0', 7, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
INSERT INTO sys_download_asset (id, code, name, platform, arch, package_type, version, sort, enabled, create_time, updated_at)
VALUES (8, 'LINUX_DEB_ARM64', 'Linux deb（arm64）', 'linux', 'arm64', 'deb', '1.0.0', 8, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
INSERT INTO sys_download_asset (id, code, name, platform, arch, package_type, version, sort, enabled, create_time, updated_at)
VALUES (9, 'LINUX_RPM_AMD64', 'Linux rpm（amd64）', 'linux', 'amd64', 'rpm', '1.0.0', 9, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;
INSERT INTO sys_download_asset (id, code, name, platform, arch, package_type, version, sort, enabled, create_time, updated_at)
VALUES (10, 'LINUX_RPM_ARM64', 'Linux rpm（arm64）', 'linux', 'arm64', 'rpm', '1.0.0', 10, TRUE, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- S3-compatible object storage configuration. A singleton row (id = 1) keeps
-- the credentials out of application.yaml; the secret key is stored as an
-- AES-GCM ciphertext.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_storage_setting
(
    id                             BIGINT PRIMARY KEY,
    enabled                        BOOLEAN      NOT NULL DEFAULT FALSE,
    endpoint                       VARCHAR(512),
    region                         VARCHAR(64)  NOT NULL DEFAULT 'us-east-1',
    bucket                         VARCHAR(128),
    access_key                     VARCHAR(255),
    secret_key                     VARCHAR(2048),
    path_style_access              BOOLEAN      NOT NULL DEFAULT FALSE,
    public_base_url                VARCHAR(512),
    object_prefix                  VARCHAR(255),
    max_file_size_mb               INTEGER      NOT NULL DEFAULT 100,
    presigned_url_expiry_seconds   INTEGER      NOT NULL DEFAULT 3600,
    updated_by                     BIGINT,
    updated_at                     TIMESTAMP,
    create_time                    TIMESTAMP
);

-- Widen the ciphertext column for deployments that created the table before
-- the secret key length was aligned with the encrypted storage format.
ALTER TABLE sys_storage_setting ALTER COLUMN secret_key TYPE VARCHAR(2048);

INSERT INTO sys_storage_setting (id, enabled, region, path_style_access,
                                 max_file_size_mb, presigned_url_expiry_seconds,
                                 create_time, updated_at)
VALUES (1, FALSE, 'us-east-1', FALSE, 100, 3600, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Audit log. Every authenticated mutating console/admin request is recorded by
-- the web audit interceptor; scheduled task runs are recorded by the task
-- runner itself. Payloads are sanitized before storage.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_audit_log
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(64),
    module          VARCHAR(64)  NOT NULL,
    action          VARCHAR(128) NOT NULL,
    description     VARCHAR(500),
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(128),
    method          VARCHAR(8),
    path            VARCHAR(512),
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    request_payload TEXT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
    error_message   VARCHAR(1000),
    duration_ms     INTEGER,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_log_create_time ON sys_audit_log (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON sys_audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_module ON sys_audit_log (module);
CREATE INDEX IF NOT EXISTS idx_audit_log_status ON sys_audit_log (status);

-- ---------------------------------------------------------------------------
-- User notifications. Online users receive the notification over SSE; the row
-- is still persisted so the bell keeps history and offline users can catch up
-- when they reconnect (delivered = FALSE rows are replayed on SSE connect).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_notification
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    type           VARCHAR(64)  NOT NULL,
    title          VARCHAR(200),
    content        VARCHAR(1000),
    payload_json   TEXT,
    read_flag      BOOLEAN      NOT NULL DEFAULT FALSE,
    delivered      BOOLEAN      NOT NULL DEFAULT FALSE,
    read_time      TIMESTAMP,
    delivered_time TIMESTAMP,
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notification_user_read ON sys_notification (user_id, read_flag);
CREATE INDEX IF NOT EXISTS idx_notification_user_created ON sys_notification (user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_notification_undelivered ON sys_notification (user_id, delivered);

-- ---------------------------------------------------------------------------
-- Preset scheduled tasks. There is intentionally no user-defined task: rows
-- are seeded here and admins may only tune enabled/cron/parameters.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_scheduled_task
(
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(64)  NOT NULL UNIQUE,
    name              VARCHAR(128) NOT NULL,
    description       VARCHAR(500),
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    cron_expression   VARCHAR(64)  NOT NULL,
    threshold_minutes INTEGER,
    retention_days    INTEGER,
    older_than_hours  INTEGER,
    batch_size        INTEGER      NOT NULL DEFAULT 100,
    last_run_at       TIMESTAMP,
    last_status       VARCHAR(16),
    last_message      VARCHAR(1000),
    last_duration_ms  INTEGER,
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by        BIGINT
);

INSERT INTO sys_scheduled_task (code, name, description, enabled, cron_expression,
                                threshold_minutes, retention_days, older_than_hours,
                                batch_size, create_time, update_time)
VALUES ('DOCUMENT_TIMEOUT_SCAN', '文档处理超时巡检',
        '扫描长时间停留在待处理/处理中的文档，标记为失败并通知上传者手动重试。',
        TRUE, '0 */5 * * * *', 30, NULL, NULL, 200, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_scheduled_task (code, name, description, enabled, cron_expression,
                                threshold_minutes, retention_days, older_than_hours,
                                batch_size, create_time, update_time)
VALUES ('MULTIPART_CLEANUP', '清理残留分片',
        '清理 kb/ 目录下超过阈值的未完成分片上传（S3 生命周期无法按前缀单独设置）。',
        TRUE, '0 30 3 * * *', NULL, NULL, 24, 200, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_scheduled_task (code, name, description, enabled, cron_expression,
                                threshold_minutes, retention_days, older_than_hours,
                                batch_size, create_time, update_time)
VALUES ('RECYCLE_BIN_CLEANUP', '清理回收站文件',
        '按回收站保留天数清理对象、文档记录、pgvector 向量与回收站记录。',
        TRUE, '0 0 4 * * *', NULL, 30, NULL, 200, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_scheduled_task (code, name, description, enabled, cron_expression,
                                threshold_minutes, retention_days, older_than_hours,
                                batch_size, create_time, update_time)
VALUES ('DELETED_OWNER_CLEANUP', '清理注销用户/团队文件',
        '清理已标记删除的用户/团队的对象前缀与业务记录，并清除头像、Logo 等字段。',
        TRUE, '0 0 0 * * *', NULL, NULL, NULL, 100, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_scheduled_task (code, name, description, enabled, cron_expression,
                                threshold_minutes, retention_days, older_than_hours,
                                batch_size, create_time, update_time)
VALUES ('STORAGE_LIFECYCLE_SYNC', '同步临时文件生命周期',
        '确保单桶中存在 tmp/ 前缀的生命周期规则，临时文件到期后由 S3 自动清理。',
        TRUE, '0 0 5 * * *', NULL, NULL, NULL, 1, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Knowledge base documents. Status machine:
--   PENDING -> PROCESSING -> READY | FAILED ; FAILED -> PENDING (retry)
--   any -> DELETED (moved to kb_recycle_bin)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_document
(
    id            BIGSERIAL PRIMARY KEY,
    owner_type    VARCHAR(16)  NOT NULL DEFAULT 'USER',
    owner_id      BIGINT       NOT NULL,
    channel_id    BIGINT,
    name          VARCHAR(255) NOT NULL,
    object_key    VARCHAR(1024) NOT NULL,
    original_key  VARCHAR(1024),
    size          BIGINT       NOT NULL DEFAULT 0,
    content_type  VARCHAR(128),
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1000),
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    created_by    BIGINT,
    processed_at  TIMESTAMP,
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kb_document_status_update ON kb_document (status, update_time);
CREATE INDEX IF NOT EXISTS idx_kb_document_owner ON kb_document (owner_type, owner_id);

CREATE TABLE IF NOT EXISTS kb_recycle_bin
(
    id                  BIGSERIAL PRIMARY KEY,
    document_id         BIGINT       NOT NULL,
    owner_type          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    owner_id            BIGINT       NOT NULL,
    channel_id          BIGINT,
    object_key          VARCHAR(1024) NOT NULL,
    original_object_key VARCHAR(1024),
    name                VARCHAR(255),
    size                BIGINT       NOT NULL DEFAULT 0,
    content_type        VARCHAR(128),
    deleted_by          BIGINT,
    delete_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kb_recycle_delete_time ON kb_recycle_bin (delete_time);
CREATE INDEX IF NOT EXISTS idx_kb_recycle_owner ON kb_recycle_bin (owner_type, owner_id);

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS files_cleaned_at TIMESTAMP;
ALTER TABLE sys_team ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_team ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE sys_team ADD COLUMN IF NOT EXISTS files_cleaned_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_team_deleted ON sys_team (is_deleted, files_cleaned_at);

ALTER TABLE sys_storage_setting ADD COLUMN IF NOT EXISTS temp_retention_days INTEGER NOT NULL DEFAULT 7;

-- ---------------------------------------------------------------------------
-- Owner profiles / channels / folders
-- A channel belongs to exactly one owner (personal USER or TEAM) and is
-- addressed as /{ownerUsername}/{channelSlug}. Owner namespaces are global:
-- a team slug may not collide with a user username (or another team slug).
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS gender VARCHAR(16);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS bio VARCHAR(512);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS company VARCHAR(128);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS hometown VARCHAR(128);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS timezone VARCHAR(64);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS social_links TEXT;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS readme TEXT;

ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS max_folder_depth INTEGER NOT NULL DEFAULT 5;

CREATE TABLE IF NOT EXISTS kb_channel
(
    id                BIGSERIAL PRIMARY KEY,
    owner_type        VARCHAR(16)  NOT NULL,
    owner_id          BIGINT       NOT NULL,
    name              VARCHAR(64)  NOT NULL,
    slug              VARCHAR(63)  NOT NULL,
    description       VARCHAR(512),
    is_public         BOOLEAN      NOT NULL DEFAULT FALSE,
    readme_key        VARCHAR(1024),
    agent_key         VARCHAR(1024),
    agent_name        VARCHAR(128),
    agent_description VARCHAR(512),
    agent_tags        TEXT,
    pinned            BOOLEAN      NOT NULL DEFAULT FALSE,
    pinned_order      INTEGER      NOT NULL DEFAULT 0,
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_kb_channel_owner_slug ON kb_channel (owner_type, owner_id, slug);
CREATE INDEX IF NOT EXISTS idx_kb_channel_owner ON kb_channel (owner_type, owner_id);

CREATE TABLE IF NOT EXISTS kb_folder
(
    id          BIGSERIAL PRIMARY KEY,
    channel_id  BIGINT       NOT NULL,
    parent_id   BIGINT,
    name        VARCHAR(128) NOT NULL,
    depth       INTEGER      NOT NULL DEFAULT 1,
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_kb_folder_parent_name
    ON kb_folder (channel_id, COALESCE(parent_id, 0), name);
CREATE INDEX IF NOT EXISTS idx_kb_folder_channel ON kb_folder (channel_id);

ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS folder_id BIGINT;
ALTER TABLE kb_recycle_bin ADD COLUMN IF NOT EXISTS retention_days INTEGER NOT NULL DEFAULT 30;

-- Direct pgvector storage for document chunks. Spring AI's PgVectorStore
-- auto-configuration is intentionally disabled; the ingestion service writes
-- this table directly so the knowledge base can evolve independently.
CREATE TABLE IF NOT EXISTS vector_store
(
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(1536)
);
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata ON vector_store USING GIN (metadata);

ALTER TABLE sys_site_setting ADD COLUMN IF NOT EXISTS recycle_retention_days INTEGER NOT NULL DEFAULT 30;

-- ---------------------------------------------------------------------------
-- Model providers / models. Administrators configure OpenAI-compatible
-- endpoints (DeepSeek, Qwen, custom variants) and choose which models are
-- visible to users. API keys are AES-GCM encrypted by SecurityCryptoService.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_model_provider
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    vendor      VARCHAR(32)  NOT NULL DEFAULT 'OPENAI',
    base_url    VARCHAR(512) NOT NULL,
    api_key     VARCHAR(2048),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by  BIGINT
);

CREATE TABLE IF NOT EXISTS sys_model_config
(
    id               BIGSERIAL PRIMARY KEY,
    provider_id      BIGINT       NOT NULL,
    model_id         VARCHAR(255) NOT NULL,
    display_name     VARCHAR(128),
    model_type       VARCHAR(16)  NOT NULL DEFAULT 'CHAT',
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    visible          BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_tools   BOOLEAN      NOT NULL DEFAULT FALSE,
    thinking_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    reasoning_effort VARCHAR(16),
    extra_body       TEXT,
    dimension        INTEGER,
    context_window   INTEGER      NOT NULL DEFAULT 32000,
    create_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time      TIMESTAMP    NOT NULL DEFAULT NOW()
);
ALTER TABLE sys_model_config ADD COLUMN IF NOT EXISTS context_window INTEGER NOT NULL DEFAULT 32000;
ALTER TABLE sys_model_config ADD COLUMN IF NOT EXISTS supports_vision BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_model_config_provider_model
    ON sys_model_config (provider_id, model_id, model_type);
CREATE INDEX IF NOT EXISTS idx_model_config_type ON sys_model_config (model_type, enabled, visible);

-- Singleton RAG/chunk parameters, edited from the console.
CREATE TABLE IF NOT EXISTS sys_rag_setting
(
    id                    BIGINT PRIMARY KEY,
    chat_model_id         BIGINT,
    embedding_model_id    BIGINT,
    rerank_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    rerank_model_id        BIGINT,
    chunk_size            INTEGER NOT NULL DEFAULT 1200,
    chunk_overlap         INTEGER NOT NULL DEFAULT 120,
    retrieval_top_k       INTEGER NOT NULL DEFAULT 20,
    retrieval_top_n       INTEGER NOT NULL DEFAULT 5,
    web_search_endpoint   VARCHAR(512),
    web_search_api_key    VARCHAR(2048),
    context_compression_threshold INTEGER NOT NULL DEFAULT 80,
    compression_prompt    TEXT,
    semantic_cache_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    retrieval_cache_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    semantic_cache_threshold      DOUBLE PRECISION NOT NULL DEFAULT 0.92,
    semantic_cache_ttl_minutes    INTEGER NOT NULL DEFAULT 60,
    semantic_cache_max_entries    INTEGER NOT NULL DEFAULT 500,
    retrieval_cache_ttl_minutes   INTEGER NOT NULL DEFAULT 1440,
    retrieval_cache_max_entries   INTEGER NOT NULL DEFAULT 1000,
    updated_by            BIGINT,
    update_time           TIMESTAMP NOT NULL DEFAULT NOW()
);
INSERT INTO sys_rag_setting (id, chunk_size, chunk_overlap, retrieval_top_k, retrieval_top_n)
VALUES (1, 1200, 120, 20, 5)
ON CONFLICT (id) DO NOTHING;

-- Document-level metadata additions (chunks live in document_chunk).
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS title VARCHAR(512);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS author VARCHAR(255);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS document_type VARCHAR(64);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS expiration_time TIMESTAMP;
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Chunk-level metadata + vector. Dimension follows the configured embedding
-- model; the application alters this column when the model dimension changes.
CREATE TABLE IF NOT EXISTS document_chunk
(
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT      NOT NULL,
    chunk_id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    chunk_index      INTEGER     NOT NULL,
    chunk_text       TEXT        NOT NULL,
    chunk_text_hash  VARCHAR(64) NOT NULL,
    embedding_model  VARCHAR(255),
    embedding        VECTOR(1536),
    page_number      INTEGER     NOT NULL DEFAULT 1,
    title            VARCHAR(512),
    author           VARCHAR(255),
    document_type    VARCHAR(64),
    s3_path          VARCHAR(1024),
    expiration_time  TIMESTAMP,
    version          INTEGER     NOT NULL DEFAULT 1,
    metadata         JSONB,
    create_time      TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_chunk_index ON document_chunk (document_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_document_chunk_document ON document_chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_document_chunk_hash ON document_chunk (chunk_text_hash);

-- Singleton system prompt configuration, edited from Console → Prompt 配置.
CREATE TABLE IF NOT EXISTS sys_prompt_setting
(
    id              BIGINT PRIMARY KEY,
    system_prompt   TEXT,
    rag_prompt      TEXT,
    expired_prompt  TEXT,
    tool_prompt     TEXT,
    updated_by      BIGINT,
    update_time     TIMESTAMP NOT NULL DEFAULT NOW()
);
INSERT INTO sys_prompt_setting (id, system_prompt, rag_prompt, expired_prompt, tool_prompt)
VALUES (1,
        $$You are Cognia, a precise knowledge-base assistant. Answer in the user's language.$$,
        $$When retrieved knowledge is provided, cite the source file name and channel, and base the answer on that knowledge when it is relevant.$$,
        $$If a retrieved chunk is marked 状态：已过期, you must explicitly tell the user the information is expired, state its expiration time, and must not present it as a current fact.$$,
        $$Use web_search and web_fetch when current external information is needed, and cite the sources you used.$$)
ON CONFLICT (id) DO NOTHING;

-- Chat conversations and messages (sidebar history)
CREATE TABLE IF NOT EXISTS chat_conversation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    model_id BIGINT,
    channel_ids TEXT,
    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_update
    ON chat_conversation (user_id, update_time DESC);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT,
    reasoning TEXT,
    create_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation
    ON chat_message (conversation_id, id);

-- Public random identifiers for chat conversations (never expose numeric PKs)
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS public_id VARCHAR(32);
UPDATE chat_conversation
SET public_id = replace(gen_random_uuid()::text, '-', '')
WHERE public_id IS NULL;
ALTER TABLE chat_conversation ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_conversation_public_id
    ON chat_conversation (public_id);

-- ---------------------------------------------------------------------------
-- Discover plaza: featured channels, subscriptions and channel broadcasts.
-- ---------------------------------------------------------------------------
ALTER TABLE kb_channel ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_channel ADD COLUMN IF NOT EXISTS featured_time TIMESTAMP;
ALTER TABLE kb_channel ADD COLUMN IF NOT EXISTS web_search_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_kb_channel_featured
    ON kb_channel (featured_time DESC, id DESC) WHERE featured = TRUE;
CREATE INDEX IF NOT EXISTS idx_kb_channel_public_updated
    ON kb_channel (is_public, update_time DESC);

-- A user following a public channel (drives the hot ranking and "我的订阅").
CREATE TABLE IF NOT EXISTS kb_channel_subscription
(
    id          BIGSERIAL PRIMARY KEY,
    channel_id  BIGINT    NOT NULL,
    user_id     BIGINT    NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_kb_channel_subscription
    ON kb_channel_subscription (channel_id, user_id);
CREATE INDEX IF NOT EXISTS idx_kb_channel_subscription_user
    ON kb_channel_subscription (user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_kb_channel_subscription_channel
    ON kb_channel_subscription (channel_id, create_time DESC);

-- Opt-in list for channel broadcast notifications (distinct from follow).
CREATE TABLE IF NOT EXISTS kb_channel_notify_subscription
(
    id          BIGSERIAL PRIMARY KEY,
    channel_id  BIGINT    NOT NULL,
    user_id     BIGINT    NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_kb_channel_notify_subscription
    ON kb_channel_notify_subscription (channel_id, user_id);
CREATE INDEX IF NOT EXISTS idx_kb_channel_notify_subscription_user
    ON kb_channel_notify_subscription (user_id, create_time DESC);

-- Broadcast messages sent by a channel owner (one per channel per day).
CREATE TABLE IF NOT EXISTS kb_channel_notification
(
    id            BIGSERIAL PRIMARY KEY,
    channel_id    BIGINT       NOT NULL,
    sender_id     BIGINT       NOT NULL,
    title         VARCHAR(200) NOT NULL,
    content       TEXT,
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    notified_date DATE
);
CREATE INDEX IF NOT EXISTS idx_kb_channel_notification_channel
    ON kb_channel_notification (channel_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_kb_channel_notification_date
    ON kb_channel_notification (channel_id, notified_date);

-- Global web-search switches for RAG.
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS web_search_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS allow_user_web_search BOOLEAN NOT NULL DEFAULT TRUE;

-- Context-window compression + two-layer cache configuration.
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS context_compression_threshold INTEGER NOT NULL DEFAULT 80;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS compression_prompt TEXT;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS semantic_cache_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS retrieval_cache_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS semantic_cache_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.92;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS semantic_cache_ttl_minutes INTEGER NOT NULL DEFAULT 60;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS semantic_cache_max_entries INTEGER NOT NULL DEFAULT 500;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS retrieval_cache_ttl_minutes INTEGER NOT NULL DEFAULT 1440;
ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS retrieval_cache_max_entries INTEGER NOT NULL DEFAULT 1000;

UPDATE sys_rag_setting
SET compression_prompt = $$请把下面的多轮对话历史压缩成一段简洁的中文摘要，要求：
1. 保留用户的核心目标、关键事实、数字、约束条件与偏好；
2. 保留已经确认的结论、做出的决定以及尚未完成的事项；
3. 删除寒暄、重复表达和无关内容，不要编造历史中不存在的信息；
4. 只输出摘要正文，不要添加标题或解释。$$
WHERE compression_prompt IS NULL;

-- ---------------------------------------------------------------------------
-- Two-layer chat cache.
--
-- sys_semantic_cache stores (query embedding -> answer) scoped by viewer and
-- the selected channel set so answers never leak across users. The embedding
-- column intentionally has no fixed dimension: it follows the configured
-- embedding model, and the row keeps its own dimension for safety.
--
-- sys_retrieval_cache stores the serialized RetrievedChunk list for a
-- normalized query, so a repeated question skips embedding + vector search.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_semantic_cache
(
    id                   BIGSERIAL PRIMARY KEY,
    scope_hash           VARCHAR(64) NOT NULL,
    query                TEXT        NOT NULL,
    answer               TEXT,
    context_json         TEXT,
    embedding            VECTOR,
    dimension            INTEGER,
    similarity_threshold REAL,
    create_time          TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_access_time     TIMESTAMP   NOT NULL DEFAULT NOW(),
    hits                 BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_semantic_cache_scope
    ON sys_semantic_cache (scope_hash, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_semantic_cache_last_access
    ON sys_semantic_cache (last_access_time DESC);

CREATE TABLE IF NOT EXISTS sys_retrieval_cache
(
    id               BIGSERIAL PRIMARY KEY,
    scope_hash       VARCHAR(64) NOT NULL,
    query_hash       VARCHAR(64) NOT NULL,
    normalized_query TEXT        NOT NULL,
    chunks_json      TEXT,
    document_ids     TEXT,
    create_time      TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_access_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    hits             BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_retrieval_cache_scope_query
    ON sys_retrieval_cache (scope_hash, query_hash);
CREATE INDEX IF NOT EXISTS idx_retrieval_cache_last_access
    ON sys_retrieval_cache (last_access_time DESC);

-- A channel may broadcast at most one notification per calendar day.
CREATE UNIQUE INDEX IF NOT EXISTS uq_kb_channel_notification_day
    ON kb_channel_notification (channel_id, notified_date)
    WHERE notified_date IS NOT NULL;

ALTER TABLE sys_rag_setting ADD COLUMN IF NOT EXISTS title_generation_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Citation sources persisted with assistant messages so history keeps references.
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS citations_json TEXT;

-- ---------------------------------------------------------------------------
-- Conversation pinning and favorites (pinned chats sort above history).
-- ---------------------------------------------------------------------------
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS pinned_time TIMESTAMP WITHOUT TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_chat_conversation_pinned
    ON chat_conversation (user_id, pinned DESC, pinned_time DESC, update_time DESC);

CREATE TABLE IF NOT EXISTS sys_favorite
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    item_type       VARCHAR(16)  NOT NULL,
    conversation_id BIGINT       NOT NULL,
    message_id      BIGINT,
    title           VARCHAR(255),
    content         TEXT,
    create_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sys_favorite_conversation
    ON sys_favorite (user_id, conversation_id) WHERE item_type = 'CONVERSATION';
CREATE UNIQUE INDEX IF NOT EXISTS uq_sys_favorite_message
    ON sys_favorite (user_id, message_id) WHERE item_type = 'MESSAGE';
CREATE INDEX IF NOT EXISTS idx_sys_favorite_user
    ON sys_favorite (user_id, create_time DESC);

ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS attachments_json TEXT;

-- ---------------------------------------------------------------------------
-- Config convergence: prompt compression belongs to prompts, web search
-- credentials belong to tools; neither belongs to retrieval parameters.
-- ---------------------------------------------------------------------------
ALTER TABLE sys_prompt_setting ADD COLUMN IF NOT EXISTS compression_prompt TEXT;
UPDATE sys_prompt_setting
   SET compression_prompt = (SELECT compression_prompt FROM sys_rag_setting WHERE id = 1)
 WHERE id = 1
   AND (compression_prompt IS NULL OR compression_prompt = '')
   AND EXISTS (SELECT 1 FROM sys_rag_setting WHERE id = 1 AND compression_prompt IS NOT NULL);

CREATE TABLE IF NOT EXISTS sys_tool_setting
(
    id                  BIGINT PRIMARY KEY,
    web_search_enabled  BOOLEAN   NOT NULL DEFAULT TRUE,
    web_search_endpoint VARCHAR(512),
    web_search_api_key  VARCHAR(2048),
    web_fetch_enabled   BOOLEAN   NOT NULL DEFAULT TRUE,
    max_results         INTEGER   NOT NULL DEFAULT 5,
    updated_by          BIGINT,
    update_time         TIMESTAMP NOT NULL DEFAULT NOW()
);
INSERT INTO sys_tool_setting (id, web_search_enabled, web_fetch_enabled, max_results)
VALUES (1, TRUE, TRUE, 5)
ON CONFLICT (id) DO NOTHING;
UPDATE sys_tool_setting t
   SET web_search_endpoint = r.web_search_endpoint,
       web_search_api_key  = r.web_search_api_key
  FROM sys_rag_setting r
 WHERE t.id = 1
   AND r.id = 1
   AND t.web_search_endpoint IS NULL
   AND (r.web_search_endpoint IS NOT NULL OR r.web_search_api_key IS NOT NULL);

-- Provider protocol: OpenAI Chat Completions / OpenAI Responses / Anthropic Messages.
ALTER TABLE sys_model_provider ADD COLUMN IF NOT EXISTS protocol VARCHAR(32) NOT NULL DEFAULT 'OPENAI_CHAT';

ALTER TABLE sys_model_config ADD COLUMN IF NOT EXISTS max_output_tokens INTEGER NOT NULL DEFAULT 4096;
ALTER TABLE sys_model_config ADD COLUMN IF NOT EXISTS supports_text BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sys_model_config ADD COLUMN IF NOT EXISTS reasoning_levels TEXT;

-- Per-request chain trace for the admin dev console.
CREATE TABLE IF NOT EXISTS chat_trace
(
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(40)  NOT NULL,
    conversation_id VARCHAR(32),
    user_id         BIGINT,
    model_id        BIGINT,
    model_name      VARCHAR(255),
    protocol        VARCHAR(32),
    question        TEXT,
    answer          TEXT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    duration_ms     BIGINT,
    trace_json      TEXT,
    create_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_trace_trace_id ON chat_trace (trace_id);
CREATE INDEX IF NOT EXISTS idx_chat_trace_create ON chat_trace (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_chat_trace_user ON chat_trace (user_id, create_time DESC);

-- ============================================================
-- 开发模式（全局开关；仅对硬编码管理员记录全量对话链路）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_debug_setting (
    id                 BIGINT PRIMARY KEY,
    enabled            BOOLEAN NOT NULL DEFAULT FALSE,
    hardcoded_username VARCHAR(64) NOT NULL DEFAULT 'admin',
    updated_by         BIGINT,
    updated_at         TIMESTAMP,
    create_time        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO sys_debug_setting (id, enabled, hardcoded_username)
VALUES (1, FALSE, 'admin')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Dashboard metrics
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_metric_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(24) NOT NULL,
    user_id BIGINT,
    channel_id BIGINT,
    conversation_id BIGINT,
    model_id BIGINT,
    model_name VARCHAR(160),
    status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    error_type VARCHAR(32),
    duration_ms INTEGER,
    retrieval_used BOOLEAN NOT NULL DEFAULT FALSE,
    retrieval_hit BOOLEAN NOT NULL DEFAULT FALSE,
    rerank_used BOOLEAN NOT NULL DEFAULT FALSE,
    citation_count INTEGER NOT NULL DEFAULT 0,
    llm_rounds INTEGER NOT NULL DEFAULT 0,
    refused BOOLEAN NOT NULL DEFAULT FALSE,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    embedding_tokens INTEGER NOT NULL DEFAULT 0,
    retrieval_duration_ms INTEGER NOT NULL DEFAULT 0,
    rerank_duration_ms INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_metric_event_type_time ON sys_metric_event(event_type, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_metric_event_user ON sys_metric_event(user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_metric_event_channel ON sys_metric_event(channel_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_metric_event_model ON sys_metric_event(model_name, create_time DESC);

-- Conversation archive (no hard deletion from UI; archive instead)
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS archived_time TIMESTAMP WITHOUT TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_archived
    ON chat_conversation (user_id, archived, update_time DESC);

-- User preferences and TOTP two-factor authentication
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS auto_web_search BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(512);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- External identity providers (OIDC / OAuth2) --------------------------------
CREATE TABLE IF NOT EXISTS sys_oauth_provider (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    provider_type VARCHAR(16) NOT NULL DEFAULT 'OIDC',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    client_id VARCHAR(255),
    client_secret VARCHAR(2048),
    issuer VARCHAR(512),
    authorization_uri VARCHAR(512),
    token_uri VARCHAR(512),
    userinfo_uri VARCHAR(512),
    scopes VARCHAR(512) NOT NULL DEFAULT 'openid email profile',
    redirect_uri VARCHAR(512),
    username_claim VARCHAR(64) NOT NULL DEFAULT 'email',
    email_claim VARCHAR(64) NOT NULL DEFAULT 'email',
    name_claim VARCHAR(64) NOT NULL DEFAULT 'name',
    auto_create BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_oauth (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(128),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_oauth_subject UNIQUE (provider, subject)
);
CREATE INDEX IF NOT EXISTS idx_user_oauth_user ON sys_user_oauth(user_id);

INSERT INTO sys_oauth_provider (code, name, provider_type, enabled, issuer, authorization_uri, token_uri, userinfo_uri, scopes, username_claim, email_claim, name_claim)
VALUES
 ('GOOGLE', 'Google', 'OIDC', FALSE, 'https://accounts.google.com', 'https://accounts.google.com/o/oauth2/v2/auth', 'https://oauth2.googleapis.com/token', 'https://openidconnect.googleapis.com/v1/userinfo', 'openid email profile', 'email', 'email', 'name'),
 ('APPLE', 'Apple', 'OIDC', FALSE, 'https://appleid.apple.com', 'https://appleid.apple.com/auth/authorize', 'https://appleid.apple.com/auth/token', '', 'openid email name', 'email', 'email', 'name'),
 ('GITHUB', 'GitHub', 'OAUTH2', FALSE, '', 'https://github.com/login/oauth/authorize', 'https://github.com/login/oauth/access_token', 'https://api.github.com/user', 'read:user user:email', 'login', 'email', 'name'),
 ('OIDC', '统一身份认证', 'OIDC', FALSE, '', '', '', '', 'openid email profile', 'email', 'email', 'name')
ON CONFLICT (code) DO NOTHING;
