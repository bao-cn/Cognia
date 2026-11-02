<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconSettings class="size-5 text-muted-foreground" />
          系统设置
        </h1>
        <p class="text-sm text-muted-foreground">
          站点品牌、注册策略、团队上限、邮件服务、客户端下载与法律条款均保存在数据库，保存后即时生效。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Badge v-if="updatedAt" variant="outline" class="h-8 px-3">
          更新于 {{ updatedAt }}
        </Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="loadAll">
          <IconRefresh class="mr-1" />
          重新加载
        </Button>
        <Button v-if="canUpdate" :disabled="saving || !isDirty" @click="saveSettings">
          <Spinner v-if="saving" class="mr-2" />
          保存设置
        </Button>
      </div>
    </div>

    <Alert v-if="!canUpdate" variant="destructive">
      <AlertDescription>当前账户只有查看系统设置的权限，修改与发布操作已禁用。</AlertDescription>
    </Alert>
    <Alert v-if="settingsError" variant="destructive">
      <AlertDescription>{{ settingsError }}</AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <Tabs v-else v-model="activeTab" class="gap-4">
      <TabsList class="h-10 w-full justify-start overflow-x-auto bg-muted/60 p-1">
        <TabsTrigger value="site" class="flex-none px-3">
          <IconWorld />
          站点信息
        </TabsTrigger>
        <TabsTrigger value="registration" class="flex-none px-3">
          <IconUserPlus />
          注册与登录
        </TabsTrigger>
        <TabsTrigger value="smtp" class="flex-none px-3">
          <IconMail />
          邮件服务
        </TabsTrigger>
        <TabsTrigger value="team" class="flex-none px-3">
          <IconUsers />
          团队设置
        </TabsTrigger>
        <TabsTrigger value="downloads" class="flex-none px-3">
          <IconDownload />
          客户端下载
        </TabsTrigger>
        <TabsTrigger value="legal" class="flex-none px-3">
          <IconFileText />
          法律条款
        </TabsTrigger>
      </TabsList>

      <TabsContent value="site">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">品牌信息</h2>
              <p class="text-xs text-muted-foreground">用于登录页、控制台侧边栏、浏览器标题和站点元信息。</p>
            </header>
            <div class="grid gap-4 p-4 lg:grid-cols-[180px_minmax(0,1fr)]">
              <div class="flex h-24 items-center justify-center rounded-lg border bg-muted/30 p-3">
                <img :src="form.logoUrl || '/logo.png'" alt="站点 Logo 预览" class="max-h-16 max-w-full object-contain" />
              </div>
              <div class="grid gap-4 md:grid-cols-2">
                <div class="grid gap-2 md:col-span-2">
                  <Label for="site-logo">Logo 地址</Label>
                  <Input id="site-logo" v-model="form.logoUrl" placeholder="/logo.png 或 https://..." />
                  <p class="text-xs text-muted-foreground">支持站内相对路径或 HTTPS 图片地址。</p>
                </div>
                <div class="grid gap-2">
                  <Label for="site-name">站点名称</Label>
                  <Input id="site-name" v-model="form.siteName" placeholder="Cognia" />
                </div>
                <div class="grid gap-2">
                  <Label for="site-language">全站默认语言</Label>
                  <Select v-model="form.defaultLanguage">
                    <SelectTrigger id="site-language" class="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="zhCn">简体中文</SelectItem>
                      <SelectItem value="enUs">English</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div class="grid gap-2 md:col-span-2">
                  <Label for="site-description">站点描述</Label>
                  <Textarea id="site-description" v-model="form.siteDescription" :rows="3" placeholder="一句话介绍站点" />
                </div>
                <div class="grid gap-2 md:col-span-2">
                  <Label for="site-keywords">关键词</Label>
                  <Input id="site-keywords" v-model="keywordsText" placeholder="Cognia, 企业知识库, AI 搜索" />
                  <p class="text-xs text-muted-foreground">使用逗号分隔，最多 20 个，写入页面 meta keywords。</p>
                </div>
              </div>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">路由与区域</h2>
              <p class="text-xs text-muted-foreground">标题后缀会追加到所有路由页面标题，例如「用户管理{{ form.routeSuffix }}」。</p>
            </header>
            <div class="grid gap-4 p-4 md:grid-cols-2">
              <div class="grid gap-2">
                <Label for="route-suffix">路由默认后缀文本</Label>
                <Input id="route-suffix" v-model="form.routeSuffix" placeholder=" - Cognia 开源的企业知识库平台" />
                <p class="text-xs text-muted-foreground">
                  浏览器标题预览：<span class="font-medium text-foreground">用户管理{{ form.routeSuffix }}</span>
                </p>
              </div>
              <div class="grid gap-2">
                <Label for="site-timezone">时区</Label>
                <Select v-model="form.timezone">
                  <SelectTrigger id="site-timezone" class="w-full">
                    <SelectValue placeholder="选择时区" />
                  </SelectTrigger>
                  <SelectContent class="max-h-72">
                    <SelectItem v-for="zone in timezoneOptions" :key="zone.value" :value="zone.value">
                      {{ zone.label }}
                    </SelectItem>
                  </SelectContent>
                </Select>
                <p class="text-xs text-muted-foreground">从 IANA 时区列表中选择，影响接口返回的日期时间展示。</p>
              </div>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-sm font-semibold">允许上传的文档类型</h2>
                <Badge variant="secondary">{{ form.allowedUploadTypes.length }} 种</Badge>
              </div>
              <p class="text-xs text-muted-foreground">控制知识库后续文档上传的扩展名校验，不带点号。</p>
            </header>
            <div class="flex flex-col gap-3 p-4">
              <Input v-model="uploadTypesText" placeholder="pdf, docx, md" />
              <div class="flex flex-wrap gap-2">
                <label
                  v-for="type in commonUploadTypes"
                  :key="type"
                  class="flex cursor-pointer items-center gap-2 rounded-md border px-2.5 py-1.5 text-xs"
                >
                  <Checkbox
                    :model-value="form.allowedUploadTypes.includes(type)"
                    @update:model-value="toggleUploadType(type)"
                  />
                  {{ type }}
                </label>
              </div>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">知识库结构</h2>
              <p class="text-xs text-muted-foreground">控制频道的文件夹层级上限与回收站留存时长。</p>
            </header>
            <div class="grid gap-4 p-4 md:grid-cols-2">
              <div class="grid gap-2">
                <Label for="max-folder-depth">文件夹最大层级</Label>
                <Input id="max-folder-depth" v-model.number="form.maxFolderDepth" type="number" min="1" max="10" />
                <p class="text-xs text-muted-foreground">默认 5 层，最大 10 层。</p>
              </div>
              <div class="grid gap-2">
                <Label for="recycle-retention-days">回收站留存天数</Label>
                <Input id="recycle-retention-days" v-model.number="form.recycleRetentionDays" type="number" min="1" max="3650" />
                <p class="text-xs text-muted-foreground">逻辑删除的文档在回收站保留的天数，到期后由计划任务清理。</p>
              </div>
            </div>
          </section>
        </fieldset>
      </TabsContent>

      <TabsContent value="registration">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">注册策略</h2>
              <p class="text-xs text-muted-foreground">关闭注册后，注册页与注册接口会立即不可用。</p>
            </header>
            <div class="flex flex-col divide-y">
              <SettingSwitchRow
                title="开放注册"
                description="允许访客通过注册页创建账户。"
                :model-value="form.registrationEnabled"
                @update:model-value="value => (form.registrationEnabled = value)"
              />
              <SettingSwitchRow
                title="注册邮箱验证"
                description="开启后注册必须完成邮箱验证码校验。需要先启用并配置发信服务，否则无法开启。"
                :model-value="form.registrationEmailVerification"
                :disabled="!smtpUsable"
                @update:model-value="value => (form.registrationEmailVerification = value)"
              />
              <SettingSwitchRow
                title="验证码开关"
                description="控制发送注册邮箱验证码前是否要求 Cloudflare Turnstile 人机验证，防止邮件轰炸；需先配置 Turnstile 密钥。"
                :model-value="form.registerEmailCaptchaEnabled"
                :disabled="!turnstileReady"
                @update:model-value="value => (form.registerEmailCaptchaEnabled = value)"
              />
              <SettingSwitchRow
                title="登录人机验证"
                description="开启后登录必须完成 Cloudflare Turnstile 验证；需先配置 Turnstile 密钥。"
                :model-value="form.loginCaptchaEnabled"
                :disabled="!turnstileReady"
                @update:model-value="value => (form.loginCaptchaEnabled = value)"
              />
            </div>
            <p v-if="!smtpUsable" class="border-t px-4 py-3 text-xs text-amber-600">
              发信服务未启用或未填写 SMTP 主机，注册邮箱验证暂不可开启。
            </p>
            <p v-else-if="!turnstileReady" class="border-t px-4 py-3 text-xs text-amber-600">
              配置 Turnstile 站点密钥和密钥后，才能开启注册发信验证或登录人机验证。
            </p>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-sm font-semibold">注册邮箱过滤</h2>
                <Badge :variant="form.registrationEmailWhitelistEnabled ? 'default' : 'secondary'">
                  {{ form.registrationEmailWhitelistEnabled ? '白名单模式' : '无限制' }}
                </Badge>
              </div>
              <p class="text-xs text-muted-foreground">
                可限制仅允许指定邮箱或域名的用户注册；关闭白名单表示不限制。
              </p>
            </header>
            <div class="flex flex-col gap-3 p-4">
              <SettingSwitchRow
                title="启用邮箱白名单"
                description="开启后，发送验证码与注册接口都会校验邮箱是否在白名单内。"
                :model-value="form.registrationEmailWhitelistEnabled"
                class="!px-0 !py-1"
                @update:model-value="value => (form.registrationEmailWhitelistEnabled = value)"
              />
              <div v-if="form.registrationEmailWhitelistEnabled" class="grid gap-2">
                <Label for="registration-email-whitelist">允许的邮箱或域名</Label>
                <Textarea
                  id="registration-email-whitelist"
                  v-model="whitelistText"
                  :rows="5"
                  class="font-mono text-sm"
                  placeholder="user@example.com&#10;@example.com&#10;company.com"
                />
                <p class="text-xs text-muted-foreground">
                  每行或逗号分隔；`user@example.com` 匹配单个邮箱，`@example.com` / `example.com` 匹配整个域名及其子域名。
                </p>
              </div>
              <p v-else class="text-xs text-muted-foreground">当前不限制注册邮箱。</p>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-sm font-semibold">人机验证（Cloudflare Turnstile）</h2>
                <Badge :variant="turnstileReady ? 'default' : 'secondary'">
                  {{ turnstileReady ? '已配置' : '未配置' }}
                </Badge>
              </div>
              <p class="text-xs text-muted-foreground">
                配置 Cloudflare Turnstile 能力后，可在「注册策略」中开启发送验证码前的人机验证或登录人机验证。
              </p>
            </header>
            <div class="grid gap-4 p-4 md:grid-cols-2">
              <div class="grid gap-2">
                <Label for="turnstile-site-key">站点密钥 Site Key</Label>
                <Input
                  id="turnstile-site-key"
                  v-model="form.turnstileSiteKey"
                  class="font-mono"
                  placeholder="0x4AAAAAAA..."
                />
              </div>
              <div class="grid gap-2">
                <Label for="turnstile-secret-key">密钥 Secret Key</Label>
                <Input
                  id="turnstile-secret-key"
                  v-model="form.turnstileSecretKey"
                  type="password"
                  autocomplete="new-password"
                  :disabled="form.clearTurnstileSecretKey"
                  :placeholder="turnstileSecretConfigured ? '已配置，留空保持不变' : '请输入 Cloudflare Secret Key'"
                />
              </div>
              <div v-if="turnstileSecretConfigured" class="flex items-center gap-2 md:col-span-2">
                <Checkbox
                  id="clear-turnstile-secret"
                  :model-value="form.clearTurnstileSecretKey"
                  @update:model-value="value => handleClearTurnstileSecret(value === true)"
                />
                <Label for="clear-turnstile-secret" class="font-normal">清除已保存的 Turnstile 密钥</Label>
              </div>
              <p class="text-xs text-muted-foreground md:col-span-2">
                在
                <a
                  href="https://dash.cloudflare.com/?to=/:account/turnstile"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="text-primary underline-offset-4 hover:underline"
                >Cloudflare Turnstile</a>
                控制台创建 Widget 后获取密钥；密钥加密存储，清除后相关人机验证需重新配置。
              </p>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">第三方登录入口</h2>
              <p class="text-xs text-muted-foreground">仅控制登录页按钮的展示；OAuth 回调配置需在后续版本接入。</p>
            </header>
            <div class="flex flex-col divide-y">
              <SettingSwitchRow
                title="Apple ID 登录"
                description="在登录页展示「使用 Apple 登录」按钮。"
                :model-value="form.appleLoginEnabled"
                @update:model-value="value => (form.appleLoginEnabled = value)"
              >
                <template #icon>
                  <IconBrandAppleFilled class="size-4" />
                </template>
              </SettingSwitchRow>
              <SettingSwitchRow
                title="Google 登录"
                description="在登录页展示「使用 Google 登录」按钮。"
                :model-value="form.googleLoginEnabled"
                @update:model-value="value => (form.googleLoginEnabled = value)"
              >
                <template #icon>
                  <IconBrandGoogleFilled class="size-4" />
                </template>
              </SettingSwitchRow>
            </div>
          </section>
        </fieldset>
      </TabsContent>

      <TabsContent value="smtp">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-sm font-semibold">发信服务</h2>
                <Badge :variant="form.smtpEnabled ? 'default' : 'secondary'">
                  {{ form.smtpEnabled ? '已启用' : '未启用' }}
                </Badge>
              </div>
              <p class="text-xs text-muted-foreground">
                参数保存在数据库，修改后即时生效，无需重启后端；关闭时验证码会写入应用日志。
              </p>
            </header>
            <div class="flex flex-col divide-y">
              <SettingSwitchRow
                title="启用 SMTP 发信"
                description="启用后使用下方参数连接邮件服务器发送验证码与测试邮件。"
                :model-value="form.smtpEnabled"
                @update:model-value="handleSmtpToggle"
              />
            </div>
            <div class="grid gap-4 border-t p-4 md:grid-cols-2">
              <div class="grid gap-2">
                <Label for="smtp-host">SMTP 主机</Label>
                <Input id="smtp-host" v-model="form.smtpHost" placeholder="smtp.example.com" />
              </div>
              <div class="grid gap-2">
                <Label for="smtp-port">端口</Label>
                <Input
                  id="smtp-port"
                  :model-value="form.smtpPort"
                  type="number"
                  min="1"
                  max="65535"
                  @update:model-value="value => (form.smtpPort = Number(value) || 587)"
                />
              </div>
              <div class="grid gap-2">
                <Label for="smtp-username">用户名</Label>
                <Input id="smtp-username" v-model="form.smtpUsername" placeholder="no-reply@example.com" />
              </div>
              <div class="grid gap-2">
                <Label for="smtp-password">密码 / 授权码</Label>
                <Input
                  id="smtp-password"
                  v-model="form.smtpPassword"
                  type="password"
                  autocomplete="new-password"
                  :disabled="form.clearSmtpPassword"
                  :placeholder="passwordConfigured ? '已配置，留空保持不变' : '请输入 SMTP 密码或授权码'"
                />
              </div>
              <div class="grid gap-2 md:col-span-2">
                <Label for="smtp-from">发件人地址</Label>
                <Input id="smtp-from" v-model="form.smtpFrom" placeholder="no-reply@example.com" />
                <p class="text-xs text-muted-foreground">仅填写邮箱地址；留空时使用 SMTP 用户名作为发件人。</p>
              </div>
              <div v-if="passwordConfigured" class="flex items-center gap-2 md:col-span-2">
                <Checkbox
                  id="clear-smtp-password"
                  :model-value="form.clearSmtpPassword"
                  @update:model-value="value => (form.clearSmtpPassword = value === true)"
                />
                <Label for="clear-smtp-password" class="font-normal">清除已保存的 SMTP 密码</Label>
              </div>
              <div class="grid gap-4 md:col-span-2 md:grid-cols-3">
                <label class="flex items-center gap-2 text-sm">
                  <Switch v-model="form.smtpSsl" />
                  SSL / SMTPS
                </label>
                <label class="flex items-center gap-2 text-sm">
                  <Switch v-model="form.smtpStarttls" />
                  STARTTLS
                </label>
                <label class="flex items-center gap-2 text-sm">
                  <Switch v-model="form.smtpAuth" />
                  需要认证
                </label>
              </div>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">发送测试邮件</h2>
              <p class="text-xs text-muted-foreground">使用已保存的 SMTP 参数发送，建议先保存再测试。</p>
            </header>
            <div class="flex flex-col gap-3 p-4 md:flex-row md:items-end">
              <div class="grid flex-1 gap-2">
                <Label for="smtp-test-recipient">收件人邮箱</Label>
                <Input id="smtp-test-recipient" v-model="testRecipient" type="email" placeholder="admin@example.com" />
              </div>
              <Button variant="outline" :disabled="testing || !form.smtpEnabled" @click="sendTestMail">
                <Spinner v-if="testing" class="mr-2" />
                <IconSend v-else class="mr-1" />
                发送测试邮件
              </Button>
            </div>
            <p v-if="!form.smtpEnabled" class="border-t px-4 py-3 text-xs text-amber-600">
              请先启用 SMTP 并保存设置后再发送测试邮件。
            </p>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-sm font-semibold">邮件模板</h2>
                <Badge variant="outline">HTML · {{ mailTemplates.length }} 个模板</Badge>
              </div>
              <p class="text-xs text-muted-foreground">
                正文为 HTML 片段，保存后由系统套用适配主流邮箱的表格布局与内联样式；使用
                <code class="rounded bg-muted px-1 font-mono text-xs">&#123;&#123;变量&#125;&#125;</code>
                占位符。
              </p>
            </header>
            <p v-if="mailTemplateError" class="border-b px-4 py-3 text-xs text-destructive">{{ mailTemplateError }}</p>
            <div class="flex flex-col divide-y">
              <div v-for="template in mailTemplates" :key="template.code" class="flex flex-col gap-3 p-4">
                <div class="flex flex-wrap items-start justify-between gap-2">
                  <div class="flex flex-col gap-0.5">
                    <div class="flex items-center gap-2">
                      <h3 class="text-sm font-semibold">{{ template.name }}</h3>
                      <Badge variant="secondary" class="font-mono text-xs">{{ template.code }}</Badge>
                    </div>
                    <p class="text-xs text-muted-foreground">{{ template.description }}</p>
                  </div>
                  <span class="text-xs text-muted-foreground">
                    <template v-if="template.updatedAt">更新于 {{ template.updatedAt }}</template>
                  </span>
                </div>
                <div class="grid gap-2">
                  <Label :for="`mail-subject-${template.code}`">邮件主题</Label>
                  <Input :id="`mail-subject-${template.code}`" v-model="template.subject" maxlength="255" />
                </div>
                <div class="grid gap-2">
                  <div class="flex flex-wrap items-center justify-between gap-2">
                    <Label :for="`mail-content-${template.code}`">邮件正文</Label>
                    <div class="flex flex-wrap gap-1.5">
                      <button
                        v-for="placeholder in template.placeholders"
                        :key="placeholder"
                        type="button"
                        class="rounded-md border bg-muted/40 px-2 py-0.5 font-mono text-xs hover:bg-muted"
                        @click="insertTemplateVariable(template, placeholder)"
                      >&#123;&#123;{{ placeholder }}&#125;&#125;</button>
                    </div>
                  </div>
                  <HtmlCodeEditor
                    :id="`mail-content-${template.code}`"
                    v-model="template.content"
                    :rows="14"
                    placeholder="在此编写 HTML 邮件内容..."
                  />
                </div>
                <div class="flex flex-wrap justify-end gap-2">
                  <Button variant="outline" :disabled="templatePreviewLoading" @click="openTemplatePreview(template)">
                    <Spinner v-if="templatePreviewLoading" class="mr-2" />
                    <IconEye v-else class="mr-1" />
                    预览
                  </Button>
                  <Button variant="outline" :disabled="mailTemplateSaving === template.code" @click="askResetTemplate(template)">
                    恢复默认
                  </Button>
                  <Button :disabled="mailTemplateSaving === template.code" @click="saveMailTemplate(template)">
                    <Spinner v-if="mailTemplateSaving === template.code" class="mr-2" />
                    保存模板
                  </Button>
                </div>
              </div>
              <p v-if="mailTemplates.length === 0" class="p-4 text-xs text-muted-foreground">
                暂无邮件模板
              </p>
            </div>
          </section>
        </fieldset>
      </TabsContent>

      <TabsContent value="team">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">团队上限</h2>
              <p class="text-xs text-muted-foreground">
                控制每位用户可以创建和加入的团队数量。创建团队与加入团队分别计算。
              </p>
            </header>
            <div class="grid gap-4 p-4 md:grid-cols-2">
              <div class="grid gap-2">
                <Label for="team-creation-limit">每人创建团队上限</Label>
                <Input id="team-creation-limit" v-model.number="form.teamCreationLimit" type="number" min="1" max="100" />
                <p class="text-xs text-muted-foreground">默认 5 个，包含用户自己创建的团队。</p>
              </div>
              <div class="grid gap-2">
                <Label for="team-join-limit">每人加入团队上限</Label>
                <Input id="team-join-limit" v-model.number="form.teamJoinLimit" type="number" min="1" max="100" />
                <p class="text-xs text-muted-foreground">默认 10 个，不包含自己创建的团队。</p>
              </div>
            </div>
          </section>
        </fieldset>
      </TabsContent>

      <TabsContent value="downloads">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-sm font-semibold">客户端下载</h2>
                <Badge variant="outline">{{ downloadAssets.length }} 个构建</Badge>
              </div>
              <p class="text-xs text-muted-foreground">
                配置桌面客户端的版本号、下载地址、SHA256 与文件大小；公开下载页会实时读取并展示。
              </p>
            </header>

            <p v-if="downloadError" class="border-b px-4 py-3 text-xs text-destructive">{{ downloadError }}</p>

            <div class="grid gap-4 p-4 md:grid-cols-2">
              <div v-for="asset in downloadAssets" :key="asset.code" class="flex flex-col gap-3 rounded-xl border bg-muted/20 p-4">
                <div class="flex items-start justify-between gap-2">
                  <div class="min-w-0">
                    <div class="flex flex-wrap items-center gap-2">
                      <h3 class="truncate text-sm font-semibold">{{ asset.name }}</h3>
                      <Badge variant="secondary" class="font-mono text-[10px]">{{ asset.code }}</Badge>
                    </div>
                    <p class="mt-0.5 text-xs text-muted-foreground">{{ asset.platform }} / {{ asset.arch }} / {{ asset.packageType || '—' }}</p>
                  </div>
                  <Switch
                    :model-value="asset.enabled"
                    :aria-label="`${asset.name} 是否启用`"
                    @update:model-value="value => (asset.enabled = value === true)"
                  />
                </div>

                <div class="grid gap-3 sm:grid-cols-2">
                  <div class="grid gap-1.5">
                    <Label :for="`download-name-${asset.code}`">下载名称</Label>
                    <Input :id="`download-name-${asset.code}`" v-model="asset.name" maxlength="64" />
                  </div>
                  <div class="grid gap-1.5">
                    <Label :for="`download-version-${asset.code}`">版本号</Label>
                    <Input :id="`download-version-${asset.code}`" v-model="asset.version" maxlength="32" placeholder="1.0.0" />
                  </div>
                </div>

                <div class="grid gap-1.5">
                  <Label :for="`download-url-${asset.code}`">下载链接</Label>
                  <Input :id="`download-url-${asset.code}`" v-model="asset.downloadUrl" placeholder="https://download.example.com/cognia.dmg" />
                </div>

                <div class="grid gap-3 sm:grid-cols-[1fr_120px]">
                  <div class="grid gap-1.5">
                    <Label :for="`download-sha-${asset.code}`">SHA256</Label>
                    <Input
                      :id="`download-sha-${asset.code}`"
                      v-model="asset.sha256"
                      maxlength="64"
                      class="font-mono text-xs"
                      placeholder="64 位十六进制校验值"
                    />
                  </div>
                  <div class="grid gap-1.5">
                    <Label :for="`download-size-${asset.code}`">文件大小（MB）</Label>
                    <Input
                      :id="`download-size-${asset.code}`"
                      v-model="downloadSizeMb[asset.code]"
                      type="number"
                      min="0"
                      step="0.1"
                      placeholder="例如 128"
                    />
                  </div>
                </div>

                <div class="mt-auto flex items-center justify-between gap-2 pt-1">
                  <div class="flex items-center gap-2 text-xs text-muted-foreground">
                    <Badge :variant="asset.downloadUrl ? 'default' : 'secondary'">
                      {{ asset.downloadUrl ? '已配置链接' : '未配置链接' }}
                    </Badge>
                    <span v-if="asset.updatedAt">更新于 {{ asset.updatedAt }}</span>
                  </div>
                  <Button size="sm" :disabled="downloadSaving === asset.code" @click="saveDownloadAsset(asset)">
                    <Spinner v-if="downloadSaving === asset.code" class="mr-2" />
                    保存
                  </Button>
                </div>
              </div>
            </div>
          </section>
        </fieldset>
      </TabsContent>

      <TabsContent value="legal">
        <div class="flex flex-col gap-4">
          <Alert>
            <AlertDescription class="flex items-start gap-2">
              <IconAlertTriangle class="mt-0.5 size-4 shrink-0" />
              <span>
                隐私政策与用户协议保存内容后会自动发布新版本，所有用户需要重新阅读并同意；开源许可仅更新展示内容，不会触发重新同意。
              </span>
            </AlertDescription>
          </Alert>

          <section
            v-for="type in legalTypes"
            :key="type"
            class="rounded-xl border bg-card"
          >
            <header class="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
              <div class="flex items-center gap-2">
                <h2 class="text-sm font-semibold">{{ legalDocs[type].title || type }}</h2>
                <Badge variant="outline">v{{ legalDocs[type].version }}</Badge>
                <Badge v-if="type === 'OPEN_SOURCE'" variant="secondary">无需同意</Badge>
              </div>
              <span class="text-xs text-muted-foreground">
                <template v-if="legalDocs[type].updatedAt">更新于 {{ legalDocs[type].updatedAt }}</template>
              </span>
            </header>
            <div class="flex flex-col gap-3 p-4">
              <div class="grid gap-2">
                <Label :for="`legal-title-${type}`">文档标题</Label>
                <Input :id="`legal-title-${type}`" v-model="legalDocs[type].title" />
              </div>
              <div class="grid gap-2">
                <div class="flex items-center justify-between gap-2">
                  <Label :for="`legal-content-${type}`">文档内容（Markdown）</Label>
                  <span class="text-xs text-muted-foreground">支持标题、列表、粗体、链接等 Markdown 语法</span>
                </div>
                <Textarea
                  :id="`legal-content-${type}`"
                  v-model="legalDocs[type].content"
                  :rows="12"
                  class="font-mono text-sm leading-6"
                />
              </div>
              <div class="flex justify-end gap-2">
                <Button variant="outline" @click="openPreview(type)">
                  <IconEye class="mr-1" />
                  预览
                </Button>
                <Button
                  v-if="canUpdate"
                  variant="outline"
                  :disabled="legalSaving"
                  @click="askPublish(type)"
                >
                  <Spinner v-if="legalSaving && publishTarget === type" class="mr-2" />
                  {{ type === 'OPEN_SOURCE' ? '保存修订' : '保存并发布修订' }}
                </Button>
              </div>
            </div>
          </section>
          <p v-if="legalError" class="text-sm text-destructive">{{ legalError }}</p>
        </div>
      </TabsContent>
    </Tabs>

    <Dialog v-model:open="publishDialogOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{{ publishTarget === 'OPEN_SOURCE' ? '保存开源许可' : '发布法律条款修订' }}</DialogTitle>
          <DialogDescription v-if="publishTarget === 'OPEN_SOURCE'">
            即将更新《{{ publishTargetDoc?.title }}》。开源许可仅用于展示，不会要求用户重新同意。是否继续？
          </DialogDescription>
          <DialogDescription v-else>
            即将发布《{{ publishTargetDoc?.title }}》。所有在线用户（包括您）都需要重新同意，
            不同意的用户将被退出登录。是否继续？
          </DialogDescription>
        </DialogHeader>
        <Alert v-if="publishError" variant="destructive">
          <AlertDescription>{{ publishError }}</AlertDescription>
        </Alert>
        <DialogFooter>
          <Button variant="outline" :disabled="legalSaving" @click="publishDialogOpen = false">取消</Button>
          <Button variant="destructive" :disabled="legalSaving" @click="confirmPublish">
            <Spinner v-if="legalSaving" class="mr-2" />
            {{ publishTarget === 'OPEN_SOURCE' ? '确认保存' : '确认发布' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="previewDialogOpen">
      <DialogContent class="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{{ previewTargetDoc?.title || '文档预览' }}</DialogTitle>
          <DialogDescription>
            当前版本 v{{ previewTargetDoc?.version }} · Markdown 渲染预览，仅用于查看，不会发布。
          </DialogDescription>
        </DialogHeader>
        <div class="max-h-[60vh] overflow-y-auto rounded-lg border bg-background p-4 text-sm">
          <Markdown v-if="previewTargetDoc" :text="previewTargetDoc.content" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="previewDialogOpen = false">关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="templatePreviewOpen">
      <DialogContent class="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>HTML 邮件预览</DialogTitle>
          <DialogDescription>
            使用示例变量渲染并套用实际发信布局；预览在隔离的 iframe 中展示。
          </DialogDescription>
        </DialogHeader>
        <div class="flex flex-col gap-3">
          <div class="grid gap-1.5">
            <Label>主题</Label>
            <div class="rounded-md border bg-muted/20 p-3 text-sm">{{ templatePreviewSubject }}</div>
          </div>
          <div class="grid gap-1.5">
            <Label>邮件预览</Label>
            <div class="overflow-hidden rounded-md border bg-white">
              <iframe
                :srcdoc="templatePreviewHtml"
                sandbox=""
                title="HTML 邮件预览"
                class="h-[55vh] w-full"
              />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="templatePreviewOpen = false">关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="resetTemplateDialogOpen">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>恢复默认邮件模板</DialogTitle>
          <DialogDescription>
            将《{{ resetTemplateTarget?.name }}》恢复为系统默认内容，当前自定义内容会被覆盖。是否继续？
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="mailTemplateSaving !== null" @click="resetTemplateDialogOpen = false">
            取消
          </Button>
          <Button variant="destructive" :disabled="mailTemplateSaving !== null" @click="confirmResetTemplate">
            <Spinner v-if="mailTemplateSaving !== null" class="mr-2" />
            确认恢复
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { legalApi, settingsApi } from '@/api'
import type {
  AdminSiteSettings,
  DownloadAsset,
  LanguageCode,
  LegalDocType,
  MailTemplate,
  UpdateSiteSettingsPayload,
} from '@/types/api'
import { useUserStore } from '@/stores/user'
import SettingSwitchRow from '@/components/console/SettingSwitchRow.vue'
import HtmlCodeEditor from '@/components/console/HtmlCodeEditor.vue'
import Markdown from '@/components/Markdown.vue'
import { useSiteStore } from '@/stores/site'
import { useLegalStore } from '@/stores/legal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Checkbox } from '@/components/ui/checkbox'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs'
import {
  IconAlertTriangle,
  IconBrandAppleFilled,
  IconBrandGoogleFilled,
  IconDownload,
  IconEye,
  IconFileText,
  IconMail,
  IconRefresh,
  IconSend,
  IconSettings,
  IconUserPlus,
  IconUsers,
  IconWorld,
} from '@tabler/icons-vue'

const userStore = useUserStore()
const siteStore = useSiteStore()
const legalStore = useLegalStore()

const activeTab = ref('site')
const loading = ref(true)
const saving = ref(false)
const settingsError = ref('')
const updatedAt = ref('')
const passwordConfigured = ref(false)
const turnstileSecretConfigured = ref(false)
const testRecipient = ref(userStore.profile?.email ?? '')
const testing = ref(false)
const mailTemplates = ref<MailTemplate[]>([])
const mailTemplateSaving = ref<string | null>(null)
const mailTemplateError = ref('')
const templatePreviewOpen = ref(false)
const templatePreviewSubject = ref('')
const templatePreviewHtml = ref('')
const templatePreviewLoading = ref(false)
const resetTemplateDialogOpen = ref(false)
const resetTemplateCode = ref<string | null>(null)
const downloadAssets = ref<DownloadAsset[]>([])
const downloadSaving = ref<string | null>(null)
const downloadError = ref('')
const downloadSizeMb = reactive<Record<string, string | number>>({})

const canUpdate = computed(() => userStore.hasPermission('settings:update'))

interface SiteSettingsForm {
  logoUrl: string
  siteName: string
  siteDescription: string
  keywords: string[]
  routeSuffix: string
  defaultLanguage: LanguageCode
  timezone: string
  allowedUploadTypes: string[]
  registrationEnabled: boolean
  registrationEmailVerification: boolean
  registerEmailCaptchaEnabled: boolean
  loginCaptchaEnabled: boolean
  registrationEmailWhitelistEnabled: boolean
  registrationEmailWhitelist: string[]
  appleLoginEnabled: boolean
  googleLoginEnabled: boolean
  turnstileSiteKey: string
  turnstileSecretKey: string
  clearTurnstileSecretKey: boolean
  smtpEnabled: boolean
  smtpHost: string
  smtpPort: number
  smtpUsername: string
  smtpPassword: string
  smtpFrom: string
  smtpSsl: boolean
  smtpStarttls: boolean
  smtpAuth: boolean
  clearSmtpPassword: boolean
  teamCreationLimit: number
  teamJoinLimit: number
  maxFolderDepth: number
  recycleRetentionDays: number
}

const form = reactive<SiteSettingsForm>({
  logoUrl: '',
  siteName: '',
  siteDescription: '',
  keywords: [],
  routeSuffix: '',
  defaultLanguage: 'zhCn',
  timezone: 'Asia/Shanghai',
  allowedUploadTypes: [],
  registrationEnabled: true,
  registrationEmailVerification: false,
  registerEmailCaptchaEnabled: true,
  loginCaptchaEnabled: false,
  registrationEmailWhitelistEnabled: false,
  registrationEmailWhitelist: [],
  appleLoginEnabled: false,
  googleLoginEnabled: false,
  turnstileSiteKey: '',
  turnstileSecretKey: '',
  clearTurnstileSecretKey: false,
  smtpEnabled: false,
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  smtpFrom: '',
  smtpSsl: false,
  smtpStarttls: true,
  smtpAuth: true,
  clearSmtpPassword: false,
  teamCreationLimit: 5,
  teamJoinLimit: 10,
  maxFolderDepth: 5,
  recycleRetentionDays: 30,
})

const originalSnapshot = ref('')
const commonUploadTypes = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'html', 'csv']
const legalTypes: LegalDocType[] = ['PRIVACY', 'TERMS', 'OPEN_SOURCE']

const legalDocs = reactive<Record<LegalDocType, {
  title: string
  content: string
  version: number
  updatedAt?: string
}>>({
  PRIVACY: { title: '隐私政策', content: '', version: 1 },
  TERMS: { title: '用户协议', content: '', version: 1 },
  OPEN_SOURCE: { title: '开放源代码许可', content: '', version: 1 },
})

const timezoneOptions = computed(() => {
  const presets = [
    { value: 'Asia/Shanghai', label: 'Asia/Shanghai · 中国标准时间' },
    { value: 'Asia/Hong_Kong', label: 'Asia/Hong_Kong · 香港' },
    { value: 'Asia/Taipei', label: 'Asia/Taipei · 台北' },
    { value: 'Asia/Singapore', label: 'Asia/Singapore · 新加坡' },
    { value: 'Asia/Tokyo', label: 'Asia/Tokyo · 东京' },
    { value: 'Asia/Seoul', label: 'Asia/Seoul · 首尔' },
    { value: 'Asia/Bangkok', label: 'Asia/Bangkok · 曼谷' },
    { value: 'Asia/Kolkata', label: 'Asia/Kolkata · 印度' },
    { value: 'Asia/Dubai', label: 'Asia/Dubai · 迪拜' },
    { value: 'Europe/London', label: 'Europe/London · 伦敦' },
    { value: 'Europe/Paris', label: 'Europe/Paris · 巴黎' },
    { value: 'Europe/Berlin', label: 'Europe/Berlin · 柏林' },
    { value: 'Europe/Moscow', label: 'Europe/Moscow · 莫斯科' },
    { value: 'America/New_York', label: 'America/New_York · 纽约' },
    { value: 'America/Chicago', label: 'America/Chicago · 芝加哥' },
    { value: 'America/Denver', label: 'America/Denver · 丹佛' },
    { value: 'America/Los_Angeles', label: 'America/Los_Angeles · 洛杉矶' },
    { value: 'America/Sao_Paulo', label: 'America/Sao_Paulo · 圣保罗' },
    { value: 'Australia/Sydney', label: 'Australia/Sydney · 悉尼' },
    { value: 'Pacific/Auckland', label: 'Pacific/Auckland · 奥克兰' },
    { value: 'UTC', label: 'UTC · 协调世界时' },
  ]
  if (form.timezone && !presets.some(zone => zone.value === form.timezone)) {
    return [{ value: form.timezone, label: `${form.timezone}（当前设置）` }, ...presets]
  }
  return presets
})

const legalSaving = ref(false)
const legalError = ref('')
const publishDialogOpen = ref(false)
const publishTarget = ref<LegalDocType | null>(null)
const publishError = ref('')
const publishTargetDoc = computed(() => publishTarget.value ? legalDocs[publishTarget.value] : null)
const previewDialogOpen = ref(false)
const previewTarget = ref<LegalDocType | null>(null)
const previewTargetDoc = computed(() => previewTarget.value ? legalDocs[previewTarget.value] : null)

const resetTemplateTarget = computed(() =>
  mailTemplates.value.find(item => item.code === resetTemplateCode.value) ?? null)

function insertTemplateVariable(template: MailTemplate, placeholder: string): void {
  const token = `{{${placeholder}}}`
  template.content = `${template.content}${template.content.endsWith('\n') || template.content === '' ? '' : '\n'}${token}`
}

async function openTemplatePreview(template: MailTemplate): Promise<void> {
  templatePreviewLoading.value = true
  mailTemplateError.value = ''
  try {
    const preview = await settingsApi.previewMailTemplate(template.code, {
      subject: template.subject,
      content: template.content,
    })
    templatePreviewSubject.value = preview.subject
    templatePreviewHtml.value = preview.html
    templatePreviewOpen.value = true
  } catch (error) {
    const message = error instanceof Error ? error.message : '邮件预览生成失败'
    mailTemplateError.value = message
    toast.error(message)
  } finally {
    templatePreviewLoading.value = false
  }
}

async function saveMailTemplate(template: MailTemplate): Promise<void> {
  if (!template.subject.trim() || !template.content.trim()) {
    mailTemplateError.value = '邮件主题和内容不能为空'
    return
  }
  mailTemplateSaving.value = template.code
  mailTemplateError.value = ''
  try {
    const updated = await settingsApi.updateMailTemplate(template.code, {
      subject: template.subject.trim(),
      content: template.content,
    })
    const index = mailTemplates.value.findIndex(item => item.code === template.code)
    if (index >= 0) {
      mailTemplates.value[index] = updated
    }
    toast.success(`「${updated.name}」已保存`)
  } catch (error) {
    const message = error instanceof Error ? error.message : '邮件模板保存失败'
    mailTemplateError.value = message
    toast.error(message)
  } finally {
    mailTemplateSaving.value = null
  }
}

function askResetTemplate(template: MailTemplate): void {
  resetTemplateCode.value = template.code
  resetTemplateDialogOpen.value = true
}

async function confirmResetTemplate(): Promise<void> {
  if (!resetTemplateCode.value) return
  const code = resetTemplateCode.value
  mailTemplateSaving.value = code
  mailTemplateError.value = ''
  try {
    const updated = await settingsApi.resetMailTemplate(code)
    const index = mailTemplates.value.findIndex(item => item.code === code)
    if (index >= 0) {
      mailTemplates.value[index] = updated
    }
    resetTemplateDialogOpen.value = false
    toast.success(`「${updated.name}」已恢复默认模板`)
  } catch (error) {
    const message = error instanceof Error ? error.message : '恢复默认模板失败'
    mailTemplateError.value = message
    toast.error(message)
  } finally {
    mailTemplateSaving.value = null
  }
}

const keywordsText = computed<string>({
  get: () => form.keywords.join(', '),
  set: (value: string) => {
    form.keywords = value.split(/[,，]/).map(item => item.trim()).filter(Boolean).slice(0, 20)
  },
})

const uploadTypesText = computed<string>({
  get: () => form.allowedUploadTypes.join(', '),
  set: (value: string) => {
    form.allowedUploadTypes = value
      .split(/[,，\s]+/)
      .map(item => item.trim().toLowerCase().replace(/^\./, ''))
      .filter(Boolean)
      .slice(0, 30)
  },
})

function applySettings(settings: AdminSiteSettings): void {
  form.logoUrl = settings.logoUrl ?? ''
  form.siteName = settings.siteName ?? ''
  form.siteDescription = settings.siteDescription ?? ''
  form.keywords = [...(settings.keywords ?? [])]
  form.routeSuffix = settings.routeSuffix ?? ''
  form.defaultLanguage = settings.defaultLanguage
  form.timezone = settings.timezone ?? 'Asia/Shanghai'
  form.allowedUploadTypes = [...(settings.allowedUploadTypes ?? [])]
  form.registrationEnabled = settings.registrationEnabled
  form.registrationEmailVerification = settings.registrationEmailVerification
  form.registerEmailCaptchaEnabled = settings.registerEmailCaptchaEnabled
  form.loginCaptchaEnabled = settings.loginCaptchaEnabled
  form.registrationEmailWhitelistEnabled = settings.registrationEmailWhitelistEnabled
  form.registrationEmailWhitelist = [...(settings.registrationEmailWhitelist ?? [])]
  form.appleLoginEnabled = settings.appleLoginEnabled
  form.googleLoginEnabled = settings.googleLoginEnabled
  form.turnstileSiteKey = settings.turnstileSiteKey ?? ''
  form.turnstileSecretKey = ''
  form.clearTurnstileSecretKey = false
  form.smtpEnabled = settings.smtpEnabled
  form.smtpHost = settings.smtpHost ?? ''
  form.smtpPort = settings.smtpPort ?? 587
  form.smtpUsername = settings.smtpUsername ?? ''
  form.smtpPassword = ''
  form.smtpFrom = settings.smtpFrom ?? ''
  form.smtpSsl = settings.smtpSsl
  form.smtpStarttls = settings.smtpStarttls
  form.smtpAuth = settings.smtpAuth
  form.clearSmtpPassword = false
  form.teamCreationLimit = settings.teamCreationLimit ?? 5
  form.teamJoinLimit = settings.teamJoinLimit ?? 10
  form.maxFolderDepth = settings.maxFolderDepth ?? 5
  form.recycleRetentionDays = settings.recycleRetentionDays ?? 30
  passwordConfigured.value = settings.smtpPasswordConfigured
  turnstileSecretConfigured.value = settings.turnstileSecretKeyConfigured
  updatedAt.value = settings.updatedAt ?? ''
  originalSnapshot.value = JSON.stringify(buildPayload())
}

function buildPayload(): UpdateSiteSettingsPayload {
  return {
    logoUrl: form.logoUrl.trim(),
    siteName: form.siteName.trim(),
    siteDescription: form.siteDescription.trim(),
    keywords: form.keywords,
    routeSuffix: form.routeSuffix,
    defaultLanguage: form.defaultLanguage,
    timezone: form.timezone.trim(),
    allowedUploadTypes: form.allowedUploadTypes,
    registrationEnabled: form.registrationEnabled,
    registrationEmailVerification: form.registrationEmailVerification,
    registerEmailCaptchaEnabled: form.registerEmailCaptchaEnabled,
    loginCaptchaEnabled: form.loginCaptchaEnabled,
    registrationEmailWhitelistEnabled: form.registrationEmailWhitelistEnabled,
    registrationEmailWhitelist: form.registrationEmailWhitelist,
    appleLoginEnabled: form.appleLoginEnabled,
    googleLoginEnabled: form.googleLoginEnabled,
    turnstileSiteKey: form.turnstileSiteKey.trim(),
    turnstileSecretKey: form.turnstileSecretKey || undefined,
    clearTurnstileSecretKey: form.clearTurnstileSecretKey,
    smtpEnabled: form.smtpEnabled,
    smtpHost: form.smtpHost.trim(),
    smtpPort: form.smtpPort,
    smtpUsername: form.smtpUsername.trim(),
    smtpPassword: form.smtpPassword || undefined,
    smtpFrom: form.smtpFrom.trim(),
    smtpSsl: form.smtpSsl,
    smtpStarttls: form.smtpStarttls,
    smtpAuth: form.smtpAuth,
    clearSmtpPassword: form.clearSmtpPassword,
    teamCreationLimit: form.teamCreationLimit,
    teamJoinLimit: form.teamJoinLimit,
    maxFolderDepth: form.maxFolderDepth,
    recycleRetentionDays: form.recycleRetentionDays,
  }
}

const isDirty = computed(() => JSON.stringify(buildPayload()) !== originalSnapshot.value)

const smtpUsable = computed(() => form.smtpEnabled && Boolean(form.smtpHost.trim()))

const turnstileReady = computed(() => Boolean(form.turnstileSiteKey.trim())
  && (Boolean(form.turnstileSecretKey.trim()) || (turnstileSecretConfigured.value && !form.clearTurnstileSecretKey)))

const whitelistText = computed<string>({
  get: () => form.registrationEmailWhitelist.join('\n'),
  set: (value: string) => {
    form.registrationEmailWhitelist = value
      .split(/[\n,，;；]+/)
      .map(item => item.trim())
      .filter(Boolean)
      .slice(0, 50)
  },
})

watch(smtpUsable, usable => {
  if (!usable) {
    form.registrationEmailVerification = false
  }
})

watch(turnstileReady, ready => {
  if (!ready) {
    form.registerEmailCaptchaEnabled = false
    form.loginCaptchaEnabled = false
  }
})

function handleSmtpToggle(value: boolean): void {
  form.smtpEnabled = value
  if (!value) {
    form.registrationEmailVerification = false
  }
}

function handleClearTurnstileSecret(value: boolean): void {
  form.clearTurnstileSecretKey = value
  if (value) {
    form.registerEmailCaptchaEnabled = false
    form.loginCaptchaEnabled = false
  }
}

function validate(): string | null {
  if (!form.siteName.trim()) return '站点名称不能为空'
  if (!form.timezone.trim()) return '时区不能为空'
  if (form.allowedUploadTypes.length === 0) return '请至少保留一种允许上传的文档类型'
  if (form.smtpEnabled && !form.smtpHost.trim()) return '启用 SMTP 时必须填写 SMTP 主机'
  if (form.smtpPort < 1 || form.smtpPort > 65535) return 'SMTP 端口需在 1-65535 之间'
  if (form.teamCreationLimit < 1 || form.teamCreationLimit > 100) return '创建团队上限需在 1-100 之间'
  if (form.teamJoinLimit < 1 || form.teamJoinLimit > 100) return '加入团队上限需在 1-100 之间'
  if (form.maxFolderDepth < 1 || form.maxFolderDepth > 10) return '文件夹最大层级需在 1-10 之间'
  if (form.recycleRetentionDays < 1 || form.recycleRetentionDays > 3650) return '回收站留存天数需在 1-3650 之间'
  const hasTurnstileSiteKey = Boolean(form.turnstileSiteKey.trim())
  const hasTurnstileSecret = Boolean(form.turnstileSecretKey.trim())
    || (turnstileSecretConfigured.value && !form.clearTurnstileSecretKey)
  if (hasTurnstileSiteKey !== hasTurnstileSecret) {
    return 'Turnstile 站点密钥和密钥需要同时配置；如需停用，请同时清空站点密钥并清除密钥'
  }
  const turnstileConfigured = hasTurnstileSiteKey && hasTurnstileSecret
  if (form.registrationEmailVerification && !smtpUsable.value) {
    return '开启注册邮箱验证前，请先启用并配置发信服务'
  }
  if ((form.registerEmailCaptchaEnabled || form.loginCaptchaEnabled) && !turnstileConfigured) {
    return '开启注册发信人机验证或登录人机验证前，请先配置 Turnstile 密钥'
  }
  if (form.registrationEmailWhitelistEnabled && form.registrationEmailWhitelist.length === 0) {
    return '启用注册邮箱白名单时，至少配置一个邮箱或域名'
  }
  return null
}

function toggleUploadType(type: string): void {
  const index = form.allowedUploadTypes.indexOf(type)
  if (index >= 0) {
    form.allowedUploadTypes = form.allowedUploadTypes.filter(item => item !== type)
  } else {
    form.allowedUploadTypes = [...form.allowedUploadTypes, type]
  }
}

async function saveDownloadAsset(asset: DownloadAsset): Promise<void> {
  if (!asset.name.trim() || !asset.version.trim()) {
    downloadError.value = '下载名称和版本号不能为空'
    return
  }
  downloadSaving.value = asset.code
  downloadError.value = ''
  try {
    const rawSize = downloadSizeMb[asset.code]
    const sizeText = rawSize == null ? '' : String(rawSize).trim()
    const sizeMb = sizeText ? Number(sizeText) : null
    if (sizeMb != null && (!Number.isFinite(sizeMb) || sizeMb < 0)) {
      downloadError.value = '文件大小格式不正确'
      downloadSaving.value = null
      return
    }
    const updated = await settingsApi.updateDownloadAsset(asset.code, {
      name: asset.name.trim(),
      version: asset.version.trim(),
      downloadUrl: asset.downloadUrl?.trim() || undefined,
      sha256: asset.sha256?.trim() || undefined,
      fileSize: sizeMb == null ? undefined : Math.round(sizeMb * 1024 * 1024),
      enabled: asset.enabled,
    })
    const index = downloadAssets.value.findIndex(item => item.code === asset.code)
    if (index >= 0) {
      downloadAssets.value[index] = updated
    }
    downloadSizeMb[updated.code] = updated.fileSize ? (updated.fileSize / 1024 / 1024).toFixed(1) : ''
    toast.success(`「${updated.name}」下载配置已保存`)
  } catch (error) {
    const message = error instanceof Error ? error.message : '下载配置保存失败'
    downloadError.value = message
    toast.error(message)
  } finally {
    downloadSaving.value = null
  }
}

async function loadAll(): Promise<void> {
  loading.value = true
  settingsError.value = ''
  legalError.value = ''
  try {
    const [settings, documents, templates, downloads] = await Promise.all([
      settingsApi.getAdmin(),
      legalApi.documents(),
      settingsApi.mailTemplates(),
      settingsApi.downloadAssets(),
    ])
    applySettings(settings)
    mailTemplates.value = templates
    mailTemplateError.value = ''
    downloadAssets.value = downloads
    downloadError.value = ''
    for (const key of Object.keys(downloadSizeMb)) delete downloadSizeMb[key]
    for (const asset of downloads) {
      downloadSizeMb[asset.code] = asset.fileSize ? (asset.fileSize / 1024 / 1024).toFixed(1) : ''
    }

    for (const document of documents) {
      if (document.type === 'PRIVACY' || document.type === 'TERMS') {
        legalDocs[document.type] = {
          title: document.title,
          content: document.content,
          version: document.version,
          updatedAt: document.updatedAt,
        }
      }
    }
    testRecipient.value = testRecipient.value || userStore.profile?.email || ''
  } catch (error) {
    settingsError.value = error instanceof Error ? error.message : '系统设置加载失败'
  } finally {
    loading.value = false
  }
}

async function saveSettings(): Promise<void> {
  const validation = validate()
  if (validation) {
    settingsError.value = validation
    return
  }
  saving.value = true
  settingsError.value = ''
  try {
    const settings = await settingsApi.update(buildPayload())
    applySettings(settings)
    await siteStore.load(true)
    document.title = `系统设置${siteStore.routeSuffix}`
    toast.success('系统设置已保存并生效')
  } catch (error) {
    settingsError.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

async function sendTestMail(): Promise<void> {
  if (!testRecipient.value.trim()) {
    toast.warning('请输入测试邮件收件人')
    return
  }
  testing.value = true
  try {
    await settingsApi.testSmtp(testRecipient.value.trim())
    toast.success(`测试邮件已发送至 ${testRecipient.value.trim()}`)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '测试邮件发送失败')
  } finally {
    testing.value = false
  }
}

function openPreview(type: LegalDocType): void {
  previewTarget.value = type
  previewDialogOpen.value = true
}

function askPublish(type: LegalDocType): void {
  publishTarget.value = type
  publishError.value = ''
  publishDialogOpen.value = true
}

async function confirmPublish(): Promise<void> {
  if (!publishTarget.value) return
  const target = publishTarget.value
  const document = legalDocs[target]
  if (!document.title.trim() || !document.content.trim()) {
    publishError.value = '文档标题和内容不能为空'
    return
  }
  legalSaving.value = true
  publishError.value = ''
  try {
    const updated = await legalApi.publish(target, {
      title: document.title.trim(),
      content: document.content,
    })
    legalDocs[target] = {
      title: updated.title,
      content: updated.content,
      version: updated.version,
      updatedAt: updated.updatedAt,
    }
    publishDialogOpen.value = false
    toast.success(`《${updated.title}》已发布 v${updated.version}`)
    // Immediately notify the current administrator; other users see the
    // dialog on their next app load (or login).
    await legalStore.ensureStatus(true)
  } catch (error) {
    publishError.value = error instanceof Error ? error.message : '发布失败，请稍后重试'
  } finally {
    legalSaving.value = false
  }
}

onMounted(() => {
  void loadAll()
})
</script>
