<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { settingsApi } from '@/api'
import type { AdminSiteSettings, MailTemplate } from '@/types/api'
import { useUserStore } from '@/stores/user'
import HtmlCodeEditor from '@/components/console/HtmlCodeEditor.vue'
import SettingSwitchRow from '@/components/console/SettingSwitchRow.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Switch } from '@/components/ui/switch'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { IconEye, IconMail, IconRefresh, IconSend } from '@tabler/icons-vue'

const userStore = useUserStore()
const canUpdate = computed(() => userStore.hasPermission('settings:update'))
const activeTab = ref('smtp')
const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const error = ref('')
const settings = ref<AdminSiteSettings | null>(null)
const passwordConfigured = ref(false)
const testRecipient = ref(userStore.profile?.email ?? '')
const templates = ref<MailTemplate[]>([])
const templateSaving = ref<string | null>(null)
const templateError = ref('')
const previewOpen = ref(false)
const previewSubject = ref('')
const previewHtml = ref('')
const previewLoading = ref(false)

const form = reactive({
  smtpEnabled: false,
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  clearSmtpPassword: false,
  smtpFrom: '',
  smtpSsl: false,
  smtpStarttls: true,
  smtpAuth: true,
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [data, mails] = await Promise.all([settingsApi.getAdmin(), settingsApi.mailTemplates()])
    settings.value = data
    passwordConfigured.value = data.smtpPasswordConfigured
    form.smtpEnabled = data.smtpEnabled
    form.smtpHost = data.smtpHost ?? ''
    form.smtpPort = data.smtpPort ?? 587
    form.smtpUsername = data.smtpUsername ?? ''
    form.smtpPassword = ''
    form.clearSmtpPassword = false
    form.smtpFrom = data.smtpFrom ?? ''
    form.smtpSsl = data.smtpSsl
    form.smtpStarttls = data.smtpStarttls
    form.smtpAuth = data.smtpAuth
    templates.value = mails
    testRecipient.value = testRecipient.value || userStore.profile?.email || ''
  } catch (e: any) {
    error.value = e?.message || '加载邮件服务配置失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!settings.value || !canUpdate.value) return
  if (form.smtpEnabled && (!form.smtpHost.trim() || !form.smtpPort)) {
    toast.error('启用 SMTP 前请填写主机和端口')
    return
  }
  saving.value = true
  try {
    const updated = await settingsApi.update({
      ...settings.value,
      smtpEnabled: form.smtpEnabled,
      smtpHost: form.smtpHost.trim(),
      smtpPort: Number(form.smtpPort) || 587,
      smtpUsername: form.smtpUsername.trim(),
      smtpPassword: form.smtpPassword.trim() || undefined,
      clearSmtpPassword: form.clearSmtpPassword,
      smtpFrom: form.smtpFrom.trim(),
      smtpSsl: form.smtpSsl,
      smtpStarttls: form.smtpStarttls,
      smtpAuth: form.smtpAuth,
    } as any)
    settings.value = updated
    passwordConfigured.value = updated.smtpPasswordConfigured
    form.smtpPassword = ''
    form.clearSmtpPassword = false
    toast.success('邮件服务配置已保存')
  } catch (e: any) {
    toast.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function sendTest() {
  const recipient = testRecipient.value.trim()
  if (!recipient) { toast.error('请填写测试收件人'); return }
  testing.value = true
  try {
    await settingsApi.testSmtp(recipient)
    toast.success('测试邮件已发送')
  } catch (e: any) {
    toast.error(e?.message || '测试邮件发送失败')
  } finally {
    testing.value = false
  }
}

async function saveTemplate(template: MailTemplate) {
  templateSaving.value = template.code
  try {
    const updated = await settingsApi.updateMailTemplate(template.code, {
      subject: template.subject,
      content: template.content,
    })
    const index = templates.value.findIndex(item => item.code === template.code)
    if (index >= 0) templates.value[index] = updated
    toast.success('邮件模板已保存')
  } catch (e: any) {
    toast.error(e?.message || '保存模板失败')
  } finally {
    templateSaving.value = null
  }
}

async function resetTemplate(template: MailTemplate) {
  templateSaving.value = template.code
  try {
    const updated = await settingsApi.resetMailTemplate(template.code)
    const index = templates.value.findIndex(item => item.code === template.code)
    if (index >= 0) templates.value[index] = updated
    toast.success('已恢复默认模板')
  } catch (e: any) {
    toast.error(e?.message || '恢复默认模板失败')
  } finally {
    templateSaving.value = null
  }
}

async function preview(template: MailTemplate) {
  previewLoading.value = true
  try {
    const result = await settingsApi.previewMailTemplate(template.code, {
      subject: template.subject,
      content: template.content,
    })
    previewSubject.value = result.subject
    previewHtml.value = result.html
    previewOpen.value = true
  } catch (e: any) {
    toast.error(e?.message || '预览失败')
  } finally {
    previewLoading.value = false
  }
}

function insertVariable(template: MailTemplate, name: string) {
  template.content += `{{${name}}}`
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconMail class="size-5 text-muted-foreground" />
          邮件服务
        </h1>
        <p class="text-sm text-muted-foreground">配置发信服务、发送测试邮件，并维护系统通知与频道通知的邮件模板。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <IconRefresh class="mr-1 size-4" />重新加载
      </Button>
    </div>

    <Alert v-if="!canUpdate">
      <AlertDescription>当前账户只有查看权限，无法修改邮件服务配置。</AlertDescription>
    </Alert>
    <Alert v-if="error" variant="destructive">
      <AlertDescription>{{ error }}</AlertDescription>
    </Alert>
    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <Tabs v-else v-model="activeTab" class="gap-4">
      <TabsList class="h-10 w-full justify-start overflow-x-auto bg-muted/60 p-1">
        <TabsTrigger value="smtp" class="flex-none px-3">发信配置</TabsTrigger>
        <TabsTrigger value="templates" class="flex-none px-3">邮件模板</TabsTrigger>
      </TabsList>

      <TabsContent value="smtp">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-sm font-semibold">发信服务</h2>
                <Badge :variant="form.smtpEnabled ? 'default' : 'secondary'">{{ form.smtpEnabled ? '已启用' : '未启用' }}</Badge>
              </div>
              <p class="text-xs text-muted-foreground">用于发送验证码、频道通知与系统提醒。修改后请保存。</p>
            </header>
            <div class="flex flex-col divide-y">
              <SettingSwitchRow
                title="启用 SMTP 发信"
                description="启用后使用下方参数连接邮件服务器。"
                :model-value="form.smtpEnabled"
                @update:model-value="value => (form.smtpEnabled = value)"
              />
            </div>
            <div class="grid gap-4 border-t p-4 md:grid-cols-2">
              <div class="grid gap-2">
                <Label for="mail-host">SMTP 主机</Label>
                <Input id="mail-host" v-model="form.smtpHost" placeholder="smtp.example.com" />
              </div>
              <div class="grid gap-2">
                <Label for="mail-port">端口</Label>
                <Input id="mail-port" :model-value="form.smtpPort" type="number" min="1" max="65535"
                  @update:model-value="value => (form.smtpPort = Number(value) || 587)" />
              </div>
              <div class="grid gap-2">
                <Label for="mail-username">用户名</Label>
                <Input id="mail-username" v-model="form.smtpUsername" placeholder="no-reply@example.com" />
              </div>
              <div class="grid gap-2">
                <Label for="mail-password">密码 / 授权码</Label>
                <Input id="mail-password" v-model="form.smtpPassword" type="password" autocomplete="new-password"
                  :disabled="form.clearSmtpPassword"
                  :placeholder="passwordConfigured ? '已配置，留空保持不变' : '请输入 SMTP 密码或授权码'" />
              </div>
              <div class="grid gap-2 md:col-span-2">
                <Label for="mail-from">发件人地址</Label>
                <Input id="mail-from" v-model="form.smtpFrom" placeholder="no-reply@example.com" />
                <p class="text-xs text-muted-foreground">留空时使用 SMTP 用户名作为发件人。</p>
              </div>
              <div v-if="passwordConfigured" class="flex items-center gap-2 md:col-span-2">
                <Checkbox id="clear-mail-password" :model-value="form.clearSmtpPassword"
                  @update:model-value="value => (form.clearSmtpPassword = value === true)" />
                <Label for="clear-mail-password" class="font-normal">清除已保存的 SMTP 密码</Label>
              </div>
              <div class="grid gap-4 md:col-span-2 md:grid-cols-3">
                <label class="flex items-center gap-2 text-sm"><Switch v-model="form.smtpSsl" />SSL / SMTPS</label>
                <label class="flex items-center gap-2 text-sm"><Switch v-model="form.smtpStarttls" />STARTTLS</label>
                <label class="flex items-center gap-2 text-sm"><Switch v-model="form.smtpAuth" />需要认证</label>
              </div>
            </div>
            <div class="flex justify-end border-t px-4 py-3">
              <Button :disabled="saving" @click="save"><Spinner v-if="saving" class="mr-2" />保存配置</Button>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">发送测试邮件</h2>
              <p class="text-xs text-muted-foreground">使用已保存的 SMTP 参数发送。</p>
            </header>
            <div class="flex flex-col gap-3 p-4 md:flex-row md:items-end">
              <div class="grid flex-1 gap-2">
                <Label for="mail-test">收件人邮箱</Label>
                <Input id="mail-test" v-model="testRecipient" type="email" placeholder="admin@example.com" />
              </div>
              <Button variant="outline" :disabled="testing || !form.smtpEnabled" @click="sendTest">
                <Spinner v-if="testing" class="mr-2" /><IconSend v-else class="mr-1" />发送测试邮件
              </Button>
            </div>
          </section>
        </fieldset>
      </TabsContent>

      <TabsContent value="templates">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex items-center justify-between border-b px-4 py-3">
              <div>
                <h2 class="text-sm font-semibold">邮件模板</h2>
                <p class="mt-1 text-xs text-muted-foreground">支持验证码、SMTP 测试、频道管理通知与频道订阅通知；正文为 HTML 片段。</p>
              </div>
              <Badge variant="outline">{{ templates.length }} 个模板</Badge>
            </header>
            <p v-if="templateError" class="border-b px-4 py-3 text-xs text-destructive">{{ templateError }}</p>
            <div v-if="templates.length === 0" class="p-4 text-sm text-muted-foreground">暂无邮件模板</div>
            <div v-else class="flex flex-col divide-y">
              <div v-for="template in templates" :key="template.code" class="flex flex-col gap-3 p-4">
                <div class="flex flex-wrap items-start justify-between gap-2">
                  <div class="flex flex-col gap-0.5">
                    <div class="flex items-center gap-2">
                      <h3 class="text-sm font-semibold">{{ template.name }}</h3>
                      <Badge variant="secondary" class="font-mono text-xs">{{ template.code }}</Badge>
                    </div>
                    <p class="text-xs text-muted-foreground">{{ template.description }}</p>
                  </div>
                  <span class="text-xs text-muted-foreground">{{ template.updatedAt ? `更新于 ${template.updatedAt}` : '' }}</span>
                </div>
                <div class="grid gap-2">
                  <Label :for="`subject-${template.code}`">邮件主题</Label>
                  <Input :id="`subject-${template.code}`" v-model="template.subject" maxlength="255" />
                </div>
                <div class="grid gap-2">
                  <div class="flex flex-wrap items-center justify-between gap-2">
                    <Label :for="`content-${template.code}`">邮件正文</Label>
                    <div class="flex flex-wrap gap-1.5">
                      <button v-for="placeholder in template.placeholders" :key="placeholder" type="button"
                        class="rounded-md border bg-muted/40 px-2 py-0.5 font-mono text-xs hover:bg-muted"
                        @click="insertVariable(template, placeholder)">&#123;&#123;{{ placeholder }}&#125;&#125;</button>
                    </div>
                  </div>
                  <HtmlCodeEditor :id="`content-${template.code}`" v-model="template.content" :rows="14"
                    placeholder="在此编写 HTML 邮件内容..." />
                </div>
                <div class="flex flex-wrap justify-end gap-2">
                  <Button variant="outline" :disabled="previewLoading" @click="preview(template)">
                    <Spinner v-if="previewLoading" class="mr-2" /><IconEye v-else class="mr-1" />预览
                  </Button>
                  <Button variant="outline" :disabled="templateSaving === template.code" @click="resetTemplate(template)">恢复默认</Button>
                  <Button :disabled="templateSaving === template.code" @click="saveTemplate(template)">
                    <Spinner v-if="templateSaving === template.code" class="mr-2" />保存模板
                  </Button>
                </div>
              </div>
            </div>
          </section>
        </fieldset>
      </TabsContent>
    </Tabs>

    <Dialog v-model:open="previewOpen">
      <DialogContent class="max-w-3xl">
        <DialogHeader><DialogTitle>{{ previewSubject || '邮件预览' }}</DialogTitle></DialogHeader>
        <iframe :srcdoc="previewHtml" class="h-[60vh] w-full rounded-md border" sandbox="" />
      </DialogContent>
    </Dialog>
  </div>
</template>
