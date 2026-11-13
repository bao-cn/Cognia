<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { IconBrandGithub, IconKey, IconLogin2, IconRefresh } from '@tabler/icons-vue'
import { oauthApi, type AdminOAuthProvider } from '@/api/oauth'

const providers = ref<AdminOAuthProvider[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editorOpen = ref(false)
const editing = ref<AdminOAuthProvider | null>(null)
const form = reactive({ enabled: false, clientId: '', clientSecret: '', issuer: '', authorizationUri: '', tokenUri: '', userinfoUri: '', scopes: '', redirectUri: '', usernameClaim: '', emailClaim: '', nameClaim: '', autoCreate: true })
const clearSecret = ref(false)
const origin = window.location.origin

async function load() {
  loading.value = true; error.value = ''
  try { providers.value = await oauthApi.adminProviders() } catch (e) { error.value = e instanceof Error ? e.message : '加载失败' } finally { loading.value = false }
}
function openEdit(p: AdminOAuthProvider) {
  editing.value = p
  Object.assign(form, { enabled: p.enabled, clientId: p.clientId || '', clientSecret: '', issuer: p.issuer || '', authorizationUri: p.authorizationUri || '', tokenUri: p.tokenUri || '', userinfoUri: p.userinfoUri || '', scopes: p.scopes || '', redirectUri: p.redirectUri || '', usernameClaim: p.usernameClaim || '', emailClaim: p.emailClaim || '', nameClaim: p.nameClaim || '', autoCreate: p.autoCreate })
  clearSecret.value = false
  editorOpen.value = true
}
async function save() {
  if (!editing.value) return
  if (form.redirectUri.trim() && !/^https?:\/\/[^\s/]+/i.test(form.redirectUri.trim())) {
    toast.error('固定回调地址必须是完整的 http(s) 地址')
    return
  }
  saving.value = true
  try {
    const p = await oauthApi.updateProvider(editing.value.code, { ...form, clientSecret: form.clientSecret || undefined, clearClientSecret: clearSecret.value || undefined })
    providers.value = providers.value.map(item => item.code === p.code ? p : item)
    editorOpen.value = false
    toast.success('登录方式已更新')
  } catch (e) { toast.error(e instanceof Error ? e.message : '保存失败') } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold"><IconLogin2 class="size-5 text-muted-foreground" />登录方式</h1>
        <p class="text-sm text-muted-foreground">配置 OIDC 授权码模式与 Apple / Google / GitHub 第三方登录。密钥仅保存在服务端。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load"><IconRefresh class="mr-1" />刷新</Button>
    </div>
    <Alert><AlertDescription>回调地址格式：<code>{{ origin }}/oauth/callback?provider=CODE</code>，请同步配置到第三方平台。OIDC 可只填 Issuer，系统会自动发现端点。</AlertDescription></Alert>
    <Alert v-if="error" variant="destructive"><AlertDescription>{{ error }}</AlertDescription></Alert>
    <div v-if="loading" class="flex h-40 items-center justify-center"><Spinner /></div>
    <div v-else class="grid gap-4 md:grid-cols-2">
      <section v-for="p in providers" :key="p.code" class="rounded-xl border bg-card">
        <header class="flex items-center justify-between border-b px-4 py-3">
          <div class="flex items-center gap-2"><IconBrandGithub v-if="p.code === 'GITHUB'" class="size-4" /><IconKey v-else class="size-4" /><h2 class="text-sm font-semibold">{{ p.name }}</h2><Badge variant="outline">{{ p.providerType }}</Badge></div>
          <Badge :variant="p.enabled ? 'default' : 'secondary'">{{ p.enabled ? '已启用' : '未启用' }}</Badge>
        </header>
        <div class="flex flex-col gap-2 p-4 text-xs text-muted-foreground">
          <p>Client ID：{{ p.clientId || '未配置' }}</p>
          <p>密钥：{{ p.clientSecretConfigured ? '已配置' : '未配置' }}</p>
          <p>Issuer：{{ p.issuer || '未配置' }}</p>
          <p>Scopes：{{ p.scopes }}</p>
          <Button class="mt-2 self-start" variant="outline" size="sm" @click="openEdit(p)">配置</Button>
        </div>
      </section>
    </div>

    <Dialog v-model:open="editorOpen">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader><DialogTitle>配置 {{ editing?.name }}</DialogTitle><DialogDescription>留空表示保持原值；密钥留空不会覆盖。</DialogDescription></DialogHeader>
        <div class="grid max-h-[60vh] gap-4 overflow-y-auto py-2 md:grid-cols-2">
          <div class="flex items-center justify-between rounded-xl border px-3 py-2 md:col-span-2"><div><Label>启用</Label><p class="text-xs text-muted-foreground">启用后会出现在登录页</p></div><Switch v-model="form.enabled" /></div>
          <div class="grid gap-2 md:col-span-2"><Label>Client ID</Label><Input v-model="form.clientId" /></div>
          <div class="grid gap-2 md:col-span-2"><Label>Client Secret</Label><Input v-model="form.clientSecret" type="password" :placeholder="editing?.clientSecretConfigured ? '已配置，留空保持不变' : '未配置'" /></div>
          <label v-if="editing?.clientSecretConfigured" class="flex items-center gap-2 text-xs md:col-span-2"><input v-model="clearSecret" type="checkbox" />清除已保存的密钥</label>
          <div class="grid gap-2 md:col-span-2"><Label>Issuer</Label><Input v-model="form.issuer" placeholder="https://accounts.google.com" /></div>
          <div class="grid gap-2"><Label>Authorization URI</Label><Input v-model="form.authorizationUri" /></div>
          <div class="grid gap-2"><Label>Token URI</Label><Input v-model="form.tokenUri" /></div>
          <div class="grid gap-2 md:col-span-2"><Label>UserInfo URI</Label><Input v-model="form.userinfoUri" /></div>
          <div class="grid gap-2"><Label>Scopes</Label><Input v-model="form.scopes" /></div>
          <div class="grid gap-2"><Label>固定回调地址（可选）</Label><Input v-model="form.redirectUri" placeholder="默认使用前端回调地址" /><p class="text-xs text-muted-foreground">填写后优先使用，必须与第三方平台登记的 Redirect URI 完全一致，包括协议、域名、端口、路径、查询参数和大小写。</p></div>
          <div class="grid gap-2"><Label>用户名 Claim</Label><Input v-model="form.usernameClaim" /></div>
          <div class="grid gap-2"><Label>邮箱 Claim</Label><Input v-model="form.emailClaim" /></div>
          <div class="grid gap-2"><Label>昵称 Claim</Label><Input v-model="form.nameClaim" /></div>
          <div class="flex items-center justify-between rounded-xl border px-3 py-2"><Label>自动创建账户</Label><Switch v-model="form.autoCreate" /></div>
        </div>
        <DialogFooter><Button variant="outline" @click="editorOpen = false">取消</Button><Button :disabled="saving" @click="save"><Spinner v-if="saving" class="mr-2" />保存</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
