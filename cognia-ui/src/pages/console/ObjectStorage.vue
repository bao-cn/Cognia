<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex flex-col gap-1">
        <h1 class="flex items-center gap-2 text-xl font-semibold">
          <IconCloud class="size-5 text-muted-foreground" />
          对象存储
        </h1>
        <p class="text-sm text-muted-foreground">
          配置符合 S3 标准的对象存储，用于知识库文档、附件与下载资源的上传和分发。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Badge v-if="settingsLoaded" :variant="form.enabled ? 'default' : 'secondary'" class="h-8 px-3">
          {{ form.enabled ? '已启用' : '未启用' }}
        </Badge>
        <Badge v-if="settingsLoaded" :variant="configured ? 'outline' : 'destructive'" class="h-8 px-3">
          {{ configured ? '配置完整' : '待完善' }}
        </Badge>
        <Badge v-if="updatedAt" variant="outline" class="h-8 px-3">更新于 {{ updatedAt }}</Badge>
        <Button variant="outline" size="sm" :disabled="loading" @click="loadSettings">
          <IconRefresh class="mr-1" />
          重新加载
        </Button>
        <Button v-if="canUpdate" :disabled="loading || saving || !isDirty" @click="saveSettings">
          <Spinner v-if="saving" class="mr-2" />
          <IconDeviceFloppy v-else class="mr-1" />
          保存设置
        </Button>
      </div>
    </div>

    <Alert v-if="!canUpdate">
      <AlertDescription>
        当前账户只有查看对象存储设置的权限，无法修改配置。如需调整，请联系管理员分配「修改对象存储设置」权限。
      </AlertDescription>
    </Alert>

    <Alert v-if="settingsError" variant="destructive">
      <AlertDescription>{{ settingsError }}</AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <Tabs v-else v-model="activeTab" class="gap-4">
      <TabsList class="h-10 w-full justify-start overflow-x-auto bg-muted/60 p-1">
        <TabsTrigger value="connection" class="flex-none px-3">
          <IconPlugConnected />
          连接配置
        </TabsTrigger>
        <TabsTrigger value="upload" class="flex-none px-3">
          <IconUpload />
          上传设置
        </TabsTrigger>
        <TabsTrigger value="test" class="flex-none px-3">
          <IconActivity />
          连接测试
        </TabsTrigger>
        <TabsTrigger value="objects" class="flex-none px-3">
          <IconCloudUpload />
          对象操作
        </TabsTrigger>
      </TabsList>

      <TabsContent value="connection">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">启用状态</h2>
              <p class="text-xs text-muted-foreground">
                关闭后，上传功能将不可用并提示「未启用」；已保存的文件不会被删除。
              </p>
            </header>
            <div class="flex flex-col divide-y">
              <SettingSwitchRow
                title="启用对象存储"
                description="启用后，知识库文档与附件会保存到下方配置的存储空间。"
                :model-value="form.enabled"
                @update:model-value="value => (form.enabled = value)"
              >
                <template #icon>
                  <IconCloud class="size-4" />
                </template>
              </SettingSwitchRow>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">S3 连接参数</h2>
              <p class="text-xs text-muted-foreground">
                兼容 AWS S3、MinIO、Cloudflare R2、阿里云 OSS 等遵循 S3 协议的服务。
              </p>
            </header>
            <div class="grid gap-4 p-4 md:grid-cols-2">
              <div class="grid gap-2 md:col-span-2">
                <Label for="storage-endpoint">S3 Endpoint</Label>
                <Input
                  id="storage-endpoint"
                  v-model="form.endpoint"
                  placeholder="https://s3.us-east-1.amazonaws.com 或 https://minio.example.com"
                />
                <p class="text-xs text-muted-foreground">
                  使用 AWS S3 时可留空，由 SDK 按区域拼接地址；自建服务需填写完整地址，包含 http(s) 协议。
                </p>
              </div>
              <div class="grid gap-2">
                <Label for="storage-region">区域（Region）</Label>
                <Input id="storage-region" v-model="form.region" placeholder="us-east-1" />
                <p class="text-xs text-muted-foreground">例如 us-east-1、ap-southeast-1，或服务商要求的区域标识。</p>
              </div>
              <div class="grid gap-2">
                <Label for="storage-bucket">Bucket</Label>
                <Input id="storage-bucket" v-model="form.bucket" placeholder="cognia-files" />
                <p class="text-xs text-muted-foreground">需提前创建，且 AccessKey 具备该 Bucket 的读写与删除权限。</p>
              </div>
              <div class="grid gap-2">
                <Label for="storage-access-key">AccessKey</Label>
                <Input
                  id="storage-access-key"
                  v-model="form.accessKey"
                  autocomplete="off"
                  placeholder="AKIA..."
                />
              </div>
              <div class="grid gap-2">
                <Label for="storage-secret-key">SecretKey</Label>
                <Input
                  id="storage-secret-key"
                  v-model="form.secretKey"
                  type="password"
                  autocomplete="new-password"
                  :placeholder="secretKeyConfigured ? '已配置，留空保持不变' : '输入 SecretKey'"
                />
                <p class="text-xs text-muted-foreground">
                  密钥会加密保存，保存后不会再次显示明文，仅显示是否已配置。
                </p>
              </div>
              <div v-if="secretKeyConfigured" class="flex items-center gap-2 md:col-span-2">
                <Checkbox
                  id="clear-storage-secret"
                  :model-value="form.clearSecretKey"
                  @update:model-value="value => (form.clearSecretKey = value === true)"
                />
                <Label for="clear-storage-secret" class="text-sm font-normal text-muted-foreground">
                  清除已保存的 SecretKey（保存后需重新填写才能启用）
                </Label>
              </div>
              <div class="grid gap-2 md:col-span-2">
                <Label for="storage-public-base">公共访问地址（可选）</Label>
                <Input
                  id="storage-public-base"
                  v-model="form.publicBaseUrl"
                  placeholder="https://files.example.com"
                />
                <p class="text-xs text-muted-foreground">
                  配置 CDN 或公开域名后，对象链接使用「公共访问地址 / 对象 Key」拼接；留空则返回带签名的临时链接。
                </p>
              </div>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">访问方式</h2>
              <p class="text-xs text-muted-foreground">根据服务商要求选择 Bucket 的寻址方式。</p>
            </header>
            <div class="flex flex-col divide-y">
              <SettingSwitchRow
                title="Path-Style 访问"
                description="MinIO、Cloudflare R2 等自建服务通常需要开启；AWS S3 默认使用虚拟主机风格，保持关闭即可。"
                :model-value="form.pathStyleAccess"
                @update:model-value="value => (form.pathStyleAccess = value)"
              >
                <template #icon>
                  <IconWorld class="size-4" />
                </template>
              </SettingSwitchRow>
            </div>
          </section>
        </fieldset>
      </TabsContent>

      <TabsContent value="upload">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">对象路径与限制</h2>
              <p class="text-xs text-muted-foreground">
                控制上传对象的存放位置、单文件大小以及临时链接的默认有效期。
              </p>
            </header>
            <div class="grid gap-4 p-4 md:grid-cols-2">
              <div class="grid gap-2 md:col-span-2">
                <Label for="storage-prefix">对象键前缀</Label>
                <Input id="storage-prefix" v-model="form.objectPrefix" placeholder="cognia" />
                <p class="text-xs text-muted-foreground">
                  上传文件会自动生成不重复的保存路径，避免同名覆盖；留空表示存放在存储空间根目录。
                </p>
              </div>
              <div class="grid gap-2">
                <Label for="storage-max-size">单文件大小上限（MB）</Label>
                <Input
                  id="storage-max-size"
                  v-model.number="form.maxFileSizeMb"
                  type="number"
                  min="1"
                  max="512"
                />
                <p class="text-xs text-muted-foreground">范围 1 - 512 MB，与后端 Servlet 上传限制保持一致。</p>
              </div>
              <div class="grid gap-2">
                <Label for="storage-expiry">临时链接默认有效期（秒）</Label>
                <Input
                  id="storage-expiry"
                  v-model.number="form.presignedUrlExpirySeconds"
                  type="number"
                  min="60"
                  max="604800"
                />
                <p class="text-xs text-muted-foreground">范围 60 秒 - 7 天，用于未配置公共访问地址时的预签名链接。</p>
              </div>
              <div class="grid gap-2">
                <Label for="storage-temp-retention">临时文件保留天数</Label>
                <div class="flex gap-2">
                  <Input
                    id="storage-temp-retention"
                    v-model.number="form.tempRetentionDays"
                    type="number"
                    min="1"
                    max="365"
                  />
                  <Button
                    variant="outline"
                    :disabled="syncingLifecycle || !canUpdate"
                    @click="syncLifecycle"
                  >
                    <Spinner v-if="syncingLifecycle" class="mr-1" />
                    <IconRefresh v-else class="mr-1 size-4" />立即同步
                  </Button>
                </div>
                <p class="text-xs text-muted-foreground">
                  保存后会立即应用临时文件自动清理规则；范围 1 - 365 天。
                </p>
              </div>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">允许上传的文件类型</h2>
              <p class="text-xs text-muted-foreground">文件类型以系统设置为准，上传时会自动校验。</p>
            </header>
            <div class="flex items-start gap-3 p-4 text-sm text-muted-foreground">
              <IconInfoCircle class="mt-0.5 size-4 shrink-0" />
              <p>
                前往
                <RouterLink to="/console/setting" class="font-medium text-foreground underline underline-offset-4">
                  系统设置 → 上传与文档
                </RouterLink>
                调整允许上传的文件类型；其他上传限制不受影响。
              </p>
            </div>
          </section>
        </fieldset>
      </TabsContent>

      <TabsContent value="test">
        <section class="rounded-xl border bg-card">
          <header class="flex flex-col gap-1 border-b px-4 py-3">
            <h2 class="text-sm font-semibold">连接测试</h2>
            <p class="text-xs text-muted-foreground">
              验证存储空间是否可访问，以及文件能否正常上传、读取和删除；测试文件会自动删除。
            </p>
          </header>
          <div class="flex flex-col gap-4 p-4">
            <div class="grid gap-3 md:grid-cols-3">
              <div class="rounded-lg border bg-muted/30 px-3 py-2">
                <p class="text-xs text-muted-foreground">Endpoint</p>
                <p class="truncate text-sm font-medium">{{ form.endpoint || 'AWS 默认地址' }}</p>
              </div>
              <div class="rounded-lg border bg-muted/30 px-3 py-2">
                <p class="text-xs text-muted-foreground">Region</p>
                <p class="truncate text-sm font-medium">{{ form.region || '未填写' }}</p>
              </div>
              <div class="rounded-lg border bg-muted/30 px-3 py-2">
                <p class="text-xs text-muted-foreground">Bucket</p>
                <p class="truncate text-sm font-medium">{{ form.bucket || '未填写' }}</p>
              </div>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <Button :disabled="testing || !canUpdate" @click="runConnectionTest">
                <Spinner v-if="testing" class="mr-2" />
                <IconPlugConnected v-else class="mr-1 size-4" />
                测试连接
              </Button>
              <p class="text-xs text-muted-foreground">
                测试会使用当前已保存的配置；修改参数后请先保存，再执行测试。
              </p>
            </div>

            <Alert v-if="testResult" :variant="testResult.success ? 'default' : 'destructive'">
              <IconShieldCheck v-if="testResult.success" />
              <IconAlertTriangle v-else />
              <AlertTitle>{{ testResult.success ? '连接正常' : '连接失败' }}</AlertTitle>
              <AlertDescription>{{ testResult.message }}</AlertDescription>
            </Alert>

            <template v-if="testResult">
              <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <div
                  v-for="check in testChecks"
                  :key="check.label"
                  class="flex items-center justify-between rounded-lg border px-3 py-2 text-sm"
                  :class="check.ok
                    ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                    : 'border-destructive/30 bg-destructive/10 text-destructive'"
                >
                  <span>{{ check.label }}</span>
                  <IconCheck v-if="check.ok" class="size-4" />
                  <IconX v-else class="size-4" />
                </div>
              </div>
              <div class="grid gap-2 text-sm sm:grid-cols-2">
                <div class="flex items-center justify-between rounded-lg border px-3 py-2">
                  <span class="text-muted-foreground">耗时</span>
                  <span class="font-medium">{{ testResult.latencyMs }} ms</span>
                </div>
                <div class="flex items-center justify-between rounded-lg border px-3 py-2">
                  <span class="text-muted-foreground">测试时间</span>
                  <span class="font-medium">{{ testResult.testedAt }}</span>
                </div>
              </div>
            </template>

            <div
              v-else
              class="flex h-40 flex-col items-center justify-center gap-2 rounded-lg border border-dashed text-sm text-muted-foreground"
            >
              <IconPlugConnected class="size-6" />
              尚未执行连接测试
            </div>
          </div>
        </section>
      </TabsContent>

      <TabsContent value="objects">
        <fieldset :disabled="!canUpdate" class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">上传对象</h2>
              <p class="text-xs text-muted-foreground">
                使用当前保存的配置上传一个文件，验证存储链路是否可用。
              </p>
            </header>
            <div class="flex flex-col gap-4 p-4">
              <div class="flex flex-col gap-3 md:flex-row md:items-end">
                <div class="grid flex-1 gap-2">
                  <Label>选择文件</Label>
                  <div class="flex items-center gap-2">
                    <input ref="fileInput" type="file" class="hidden" @change="handleFileChange" />
                    <Button variant="outline" :disabled="uploading" @click="triggerFilePicker">
                      <IconFileUpload class="mr-1 size-4" />
                      选择文件
                    </Button>
                    <span class="truncate text-sm text-muted-foreground">
                      {{ selectedFile ? `${selectedFile.name} · ${formatBytes(selectedFile.size)}` : '未选择文件' }}
                    </span>
                  </div>
                </div>
                <div class="grid gap-2 md:w-56">
                  <Label for="upload-directory">上传目录（可选）</Label>
                  <Input id="upload-directory" v-model="uploadDirectory" placeholder="documents" />
                </div>
                <Button :disabled="uploading || !selectedFile" @click="uploadObject">
                  <Spinner v-if="uploading" class="mr-2" />
                  <IconCloudUpload v-else class="mr-1 size-4" />
                  上传
                </Button>
              </div>

              <Alert v-if="uploadError" variant="destructive">
                <AlertDescription>{{ uploadError }}</AlertDescription>
              </Alert>

              <div v-if="uploadResult" class="rounded-lg border bg-muted/30 p-3">
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <div class="flex items-center gap-2">
                    <Badge>上传成功</Badge>
                    <span class="text-xs text-muted-foreground">{{ uploadResult.uploadedAt }}</span>
                  </div>
                  <div class="flex flex-wrap gap-2">
                    <Button variant="outline" size="sm" @click="copyText(uploadResult.key)">
                      <IconCopy class="mr-1 size-4" />
                      复制 Key
                    </Button>
                    <Button
                      v-if="uploadResult.url"
                      variant="outline"
                      size="sm"
                      @click="copyText(uploadResult.url)"
                    >
                      <IconLink class="mr-1 size-4" />
                      复制链接
                    </Button>
                    <Button
                      v-if="uploadResult.url"
                      variant="outline"
                      size="sm"
                      @click="openUrl(uploadResult.url)"
                    >
                      <IconExternalLink class="mr-1 size-4" />
                      打开
                    </Button>
                  </div>
                </div>
                <dl class="mt-3 grid gap-3 text-sm sm:grid-cols-2">
                  <div class="min-w-0">
                    <dt class="text-xs text-muted-foreground">对象 Key</dt>
                    <dd class="break-all font-mono text-xs">{{ uploadResult.key }}</dd>
                  </div>
                  <div class="min-w-0">
                    <dt class="text-xs text-muted-foreground">对象地址</dt>
                    <dd class="break-all font-mono text-xs">{{ uploadResult.url || '未生成' }}</dd>
                  </div>
                  <div>
                    <dt class="text-xs text-muted-foreground">大小</dt>
                    <dd>{{ formatBytes(uploadResult.size) }}</dd>
                  </div>
                  <div>
                    <dt class="text-xs text-muted-foreground">Content-Type</dt>
                    <dd class="font-mono text-xs">{{ uploadResult.contentType }}</dd>
                  </div>
                </dl>
              </div>
            </div>
          </section>

          <section class="rounded-xl border bg-card">
            <header class="flex flex-col gap-1 border-b px-4 py-3">
              <h2 class="text-sm font-semibold">对象维护</h2>
              <p class="text-xs text-muted-foreground">为已有对象生成临时访问链接，或删除不再需要的对象。</p>
            </header>
            <div class="flex flex-col gap-4 p-4">
              <div class="flex flex-col gap-2">
                <Label for="object-key">对象 Key</Label>
                <div class="flex flex-col gap-2 sm:flex-row">
                  <Input
                    id="object-key"
                    v-model="objectKey"
                    class="font-mono text-xs sm:flex-1"
                    placeholder="documents/2026/10/29/xxxxxxxx.pdf"
                  />
                  <Select v-model="linkExpiry">
                    <SelectTrigger class="w-full sm:w-36">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="3600">1 小时</SelectItem>
                      <SelectItem value="21600">6 小时</SelectItem>
                      <SelectItem value="86400">24 小时</SelectItem>
                      <SelectItem value="604800">7 天</SelectItem>
                    </SelectContent>
                  </Select>
                  <Button variant="outline" :disabled="generatingLink" @click="generateLink(objectKey)">
                    <Spinner v-if="generatingLink" class="mr-2" />
                    <IconLink v-else class="mr-1 size-4" />
                    生成链接
                  </Button>
                  <Button variant="destructive" @click="askDelete(objectKey)">
                    <IconTrash class="mr-1 size-4" />
                    删除
                  </Button>
                </div>
              </div>

              <div v-if="objectLink" class="rounded-lg border bg-muted/30 p-3">
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <p class="text-xs text-muted-foreground">
                    临时访问链接 · 有效期至 {{ objectLink.expiresAt }}
                  </p>
                  <div class="flex gap-2">
                    <Button variant="outline" size="sm" @click="copyText(objectLink.url)">
                      <IconCopy class="mr-1 size-4" />
                      复制
                    </Button>
                    <Button variant="outline" size="sm" @click="openUrl(objectLink.url)">
                      <IconExternalLink class="mr-1 size-4" />
                      打开
                    </Button>
                  </div>
                </div>
                <code class="mt-2 block break-all text-xs">{{ objectLink.url }}</code>
              </div>

              <p class="text-xs text-muted-foreground">
                提示：删除操作不可撤销；如果配置了公共访问地址，删除后对应链接也会立即失效。
              </p>
            </div>
          </section>
        </fieldset>
      </TabsContent>
    </Tabs>

    <Dialog v-model:open="deleteDialogOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>删除对象</DialogTitle>
          <DialogDescription>
            确定要删除对象「{{ deleteTarget }}」吗？该操作不可撤销。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="deleting" @click="deleteDialogOpen = false">
            取消
          </Button>
          <Button variant="destructive" :disabled="deleting" @click="confirmDelete">
            <Spinner v-if="deleting" class="mr-2" />
            确认删除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { storageApi } from '@/api'
import type {
  AdminStorageSettings,
  StorageConnectionTestResult,
  StoragePresignedUrl,
  StoredObject,
  UpdateStorageSettingsPayload,
} from '@/types/api'
import { useUserStore } from '@/stores/user'
import SettingSwitchRow from '@/components/console/SettingSwitchRow.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
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
  IconActivity,
  IconAlertTriangle,
  IconCheck,
  IconCloud,
  IconCloudUpload,
  IconCopy,
  IconDeviceFloppy,
  IconExternalLink,
  IconFileUpload,
  IconInfoCircle,
  IconLink,
  IconPlugConnected,
  IconRefresh,
  IconShieldCheck,
  IconTrash,
  IconUpload,
  IconWorld,
  IconX,
} from '@tabler/icons-vue'

interface StorageSettingsForm {
  enabled: boolean
  endpoint: string
  region: string
  bucket: string
  accessKey: string
  secretKey: string
  clearSecretKey: boolean
  pathStyleAccess: boolean
  publicBaseUrl: string
  objectPrefix: string
  maxFileSizeMb: number
  presignedUrlExpirySeconds: number
  tempRetentionDays: number
}

const userStore = useUserStore()

const activeTab = ref('connection')
const loading = ref(true)
const saving = ref(false)
const syncingLifecycle = ref(false)
const settingsLoaded = ref(false)
const settingsError = ref('')
const updatedAt = ref('')
const secretKeyConfigured = ref(false)
const originalSnapshot = ref('')

const form = reactive<StorageSettingsForm>({
  enabled: false,
  endpoint: '',
  region: 'us-east-1',
  bucket: '',
  accessKey: '',
  secretKey: '',
  clearSecretKey: false,
  pathStyleAccess: false,
  publicBaseUrl: '',
  objectPrefix: '',
  maxFileSizeMb: 100,
  presignedUrlExpirySeconds: 3600,
  tempRetentionDays: 7,
})

const canUpdate = computed(() =>
  userStore.hasPermission('storage:update') || userStore.hasPermission('settings:update'),
)

const configured = computed(() => Boolean(
  form.bucket.trim()
  && form.accessKey.trim()
  && form.region.trim()
  && (secretKeyConfigured.value || form.secretKey.trim()),
))

const isDirty = computed(() => JSON.stringify(buildPayload()) !== originalSnapshot.value)

const testing = ref(false)
const testResult = ref<StorageConnectionTestResult | null>(null)

const testChecks = computed(() => {
  if (!testResult.value) return []
  return [
    { label: '存储空间可访问', ok: testResult.value.bucketExists },
    { label: '文件可上传', ok: testResult.value.canWrite },
    { label: '文件可读取', ok: testResult.value.canRead },
    { label: '文件可删除', ok: testResult.value.canDelete },
  ]
})

const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const uploadDirectory = ref('')
const uploading = ref(false)
const uploadError = ref('')
const uploadResult = ref<StoredObject | null>(null)

const objectKey = ref('')
const linkExpiry = ref('3600')
const generatingLink = ref(false)
const objectLink = ref<StoragePresignedUrl | null>(null)

const deleteDialogOpen = ref(false)
const deleteTarget = ref('')
const deleting = ref(false)

function buildPayload(): UpdateStorageSettingsPayload {
  return {
    enabled: form.enabled,
    endpoint: form.endpoint.trim(),
    region: form.region.trim(),
    bucket: form.bucket.trim(),
    accessKey: form.accessKey.trim(),
    secretKey: form.secretKey.trim() || undefined,
    clearSecretKey: form.clearSecretKey,
    pathStyleAccess: form.pathStyleAccess,
    publicBaseUrl: form.publicBaseUrl.trim(),
    objectPrefix: form.objectPrefix.trim(),
    maxFileSizeMb: form.maxFileSizeMb,
    presignedUrlExpirySeconds: form.presignedUrlExpirySeconds,
    tempRetentionDays: form.tempRetentionDays,
  }
}

function applySettings(settings: AdminStorageSettings): void {
  form.enabled = settings.enabled
  form.endpoint = settings.endpoint ?? ''
  form.region = settings.region ?? 'us-east-1'
  form.bucket = settings.bucket ?? ''
  form.accessKey = settings.accessKey ?? ''
  form.secretKey = ''
  form.clearSecretKey = false
  form.pathStyleAccess = settings.pathStyleAccess
  form.publicBaseUrl = settings.publicBaseUrl ?? ''
  form.objectPrefix = settings.objectPrefix ?? ''
  form.maxFileSizeMb = settings.maxFileSizeMb ?? 100
  form.presignedUrlExpirySeconds = settings.presignedUrlExpirySeconds ?? 3600
  form.tempRetentionDays = settings.tempRetentionDays ?? 7
  secretKeyConfigured.value = settings.secretKeyConfigured
  updatedAt.value = settings.updatedAt ?? ''
  settingsLoaded.value = true
  originalSnapshot.value = JSON.stringify(buildPayload())
}

function validate(): string | null {
  if (form.enabled) {
    if (!form.bucket.trim()) return '启用对象存储时必须填写 Bucket'
    if (!form.accessKey.trim()) return '启用对象存储时必须填写 AccessKey'
    if (!form.region.trim()) return '启用对象存储时必须填写区域'
    if (!form.secretKey.trim() && (!secretKeyConfigured.value || form.clearSecretKey)) {
      return '启用对象存储时必须填写 SecretKey'
    }
  }
  if (form.endpoint.trim() && !/^https?:\/\/\S+$/i.test(form.endpoint.trim())) {
    return 'S3 Endpoint 必须以 http:// 或 https:// 开头'
  }
  if (form.publicBaseUrl.trim() && !/^https?:\/\/\S+$/i.test(form.publicBaseUrl.trim())) {
    return '公共访问地址必须以 http:// 或 https:// 开头'
  }
  if (form.objectPrefix.trim() && !/^[A-Za-z0-9._\-/]+$/.test(form.objectPrefix.trim())) {
    return '对象键前缀只能包含字母、数字、点、下划线、短横线和斜杠'
  }
  if (form.objectPrefix.split('/').some(segment => segment === '..')) {
    return '对象键前缀不能包含 ..'
  }
  if (!Number.isFinite(form.maxFileSizeMb) || form.maxFileSizeMb < 1 || form.maxFileSizeMb > 512) {
    return '单文件大小上限需在 1 - 512 MB 之间'
  }
  if (!Number.isFinite(form.presignedUrlExpirySeconds)
    || form.presignedUrlExpirySeconds < 60
    || form.presignedUrlExpirySeconds > 604800) {
    return '临时链接有效期需在 60 - 604800 秒之间'
  }
  if (!Number.isFinite(form.tempRetentionDays) || form.tempRetentionDays < 1 || form.tempRetentionDays > 365) {
    return '临时文件保留天数需在 1 - 365 天之间'
  }
  return null
}

async function loadSettings(): Promise<void> {
  loading.value = true
  settingsError.value = ''
  try {
    applySettings(await storageApi.getSettings())
  } catch (error) {
    settingsError.value = error instanceof Error ? error.message : '对象存储设置加载失败'
  } finally {
    loading.value = false
  }
}

async function syncLifecycle() {
  syncingLifecycle.value = true
  try {
    const result = await storageApi.syncLifecycle()
    toast.success(result.message || '临时文件自动清理规则已更新')
  } catch (error) {
    const message = error instanceof Error ? error.message : '更新自动清理规则失败'
    toast.error(message)
  } finally {
    syncingLifecycle.value = false
  }
}

async function saveSettings(): Promise<void> {
  const message = validate()
  if (message) {
    settingsError.value = message
    toast.error(message)
    return
  }
  saving.value = true
  settingsError.value = ''
  try {
    const updated = await storageApi.updateSettings(buildPayload())
    applySettings(updated)
    testResult.value = null
    toast.success('对象存储设置已保存')
  } catch (error) {
    const message = error instanceof Error ? error.message : '对象存储设置保存失败'
    settingsError.value = message
    toast.error(message)
  } finally {
    saving.value = false
  }
}

async function runConnectionTest(): Promise<void> {
  testing.value = true
  settingsError.value = ''
  try {
    const result = await storageApi.testConnection()
    testResult.value = result
    if (result.success) {
      toast.success(result.message)
    } else {
      toast.warning(result.message)
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '连接测试失败'
    settingsError.value = message
    toast.error(message)
  } finally {
    testing.value = false
  }
}

function triggerFilePicker(): void {
  fileInput.value?.click()
}

function handleFileChange(event: Event): void {
  const target = event.target as HTMLInputElement
  selectedFile.value = target.files?.[0] ?? null
  uploadError.value = ''
  uploadResult.value = null
}

async function uploadObject(): Promise<void> {
  if (!selectedFile.value) return
  if (!form.enabled) {
    toast.error('请先启用对象存储并保存配置')
    return
  }
  if (selectedFile.value.size > form.maxFileSizeMb * 1024 * 1024) {
    toast.error(`文件大小不能超过 ${form.maxFileSizeMb} MB`)
    return
  }
  uploading.value = true
  uploadError.value = ''
  uploadResult.value = null
  objectLink.value = null
  try {
    const result = await storageApi.uploadObject(
      selectedFile.value,
      uploadDirectory.value.trim() || undefined,
    )
    uploadResult.value = result
    objectKey.value = result.key
    selectedFile.value = null
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    toast.success('对象上传成功')
  } catch (error) {
    uploadError.value = error instanceof Error ? error.message : '对象上传失败'
    toast.error(uploadError.value)
  } finally {
    uploading.value = false
  }
}

async function generateLink(key: string): Promise<void> {
  const target = key.trim()
  if (!target) {
    toast.error('请先填写对象 Key')
    return
  }
  generatingLink.value = true
  try {
    const result = await storageApi.presignedUrl(target, Number(linkExpiry.value))
    objectLink.value = result
    await copyText(result.url)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '生成临时链接失败')
  } finally {
    generatingLink.value = false
  }
}

function askDelete(key: string): void {
  const target = key.trim()
  if (!target) {
    toast.error('请先填写对象 Key')
    return
  }
  deleteTarget.value = target
  deleteDialogOpen.value = true
}

async function confirmDelete(): Promise<void> {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await storageApi.deleteObject(deleteTarget.value)
    if (uploadResult.value?.key === deleteTarget.value) {
      uploadResult.value = null
    }
    if (objectLink.value?.key === deleteTarget.value) {
      objectLink.value = null
    }
    toast.success('对象已删除')
    deleteDialogOpen.value = false
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '删除对象失败')
  } finally {
    deleting.value = false
  }
}

async function copyText(value?: string | null): Promise<void> {
  if (!value) {
    toast.error('暂无可复制的内容')
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    toast.success('已复制到剪贴板')
  } catch {
    toast.error('复制失败，请手动选择文本复制')
  }
}

function openUrl(url?: string | null): void {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

function formatBytes(size: number): string {
  if (!Number.isFinite(size) || size <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = size
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
}

onMounted(() => void loadSettings())
</script>
