<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from 'vue-sonner'
import { billingApi, modelApi } from '@/api'
import type {
  BillingOrder,
  BillingPlan,
  BillingRedeemCodeBatch,
  BillingSettings,
  BillingTokenPack,
  BillingUsageByModel,
  BillingUsageSummary,
  BillingTokenLedger,
  BillingTokenUsage,
  BillingUserDetails,
  BillingWallet,
  BillingWalletLedger,
  SaveBillingPlan,
  SaveBillingTokenPack,
} from '@/api/billing'
import type { ModelConfig } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Spinner } from '@/components/ui/spinner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  IconChartBar,
  IconCreditCard,
  IconDeviceFloppy,
  IconGift,
  IconPackage,
  IconRefresh,
  IconSettings,
  IconShoppingCart,
  IconTicket,
} from '@tabler/icons-vue'

type TabKey = 'settings' | 'plans' | 'packs' | 'codes' | 'orders' | 'usage' | 'user'

const userStore = useUserStore()
const siteStore = useSiteStore()
const canRead = computed(() => userStore.hasPermission('billing:read'))
const canUpdate = computed(() => userStore.hasPermission('billing:update'))
const canReadOrders = computed(() => userStore.hasPermission('billing:order-read'))
const canReadUsage = computed(() => userStore.hasPermission('billing:usage-read'))
const canGrant = computed(() => userStore.hasPermission('billing:grant'))
const canAdjust = computed(() => userStore.hasPermission('billing:adjust'))
const canReadWallet = computed(() => userStore.hasPermission('billing:wallet-read'))
const canAdjustWallet = computed(() => userStore.hasPermission('billing:wallet-adjust'))
const canReadRedeemCodes = computed(() => userStore.hasPermission('billing:redeem-read'))
const canManageRedeemCodes = computed(() => userStore.hasPermission('billing:redeem-manage'))

const tabs = computed(() => [
  ...(canRead.value ? [{ key: 'settings' as const, label: '计费设置', icon: IconSettings }] : []),
  ...(canRead.value ? [{ key: 'plans' as const, label: '订阅方案', icon: IconCreditCard }] : []),
  ...(canRead.value ? [{ key: 'packs' as const, label: 'Token 补充包', icon: IconPackage }] : []),
  ...((canReadRedeemCodes.value || canManageRedeemCodes.value)
    ? [{ key: 'codes' as const, label: '兑换码', icon: IconTicket }] : []),
  ...(canReadOrders.value ? [{ key: 'orders' as const, label: '订单', icon: IconShoppingCart }] : []),
  ...(canReadUsage.value ? [{ key: 'usage' as const, label: '用量统计', icon: IconChartBar }] : []),
  ...(canReadUsage.value ? [{ key: 'user' as const, label: '用户权益', icon: IconGift }] : []),
])

const activeTab = ref<TabKey>('settings')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const settings = reactive<BillingSettings>({
  billingEnabled: true,
  defaultPlanId: '',
  registrationTokens: 0,
  registrationTokenExpireDays: undefined,
  currency: 'CNY',
  walletCurrency: 'CNY',
  walletFractionalDigits: 2,
  walletUnitLabel: '分',
})
const plans = ref<BillingPlan[]>([])
const packs = ref<BillingTokenPack[]>([])
const models = ref<ModelConfig[]>([])
const orders = ref<BillingOrder[]>([])
const usageSummary = ref<BillingUsageSummary | null>(null)
const usageByModel = ref<BillingUsageByModel[]>([])
const usageTrend = ref<Record<string, number | string>[]>([])
const userIdQuery = ref('')
const userDetails = ref<BillingUserDetails | null>(null)
const userUsage = ref<BillingTokenUsage[]>([])
const userLedger = ref<BillingTokenLedger[]>([])
const userWallet = ref<BillingWallet | null>(null)
const userWalletLedger = ref<BillingWalletLedger[]>([])
const userLoading = ref(false)
const redeemBatches = ref<BillingRedeemCodeBatch[]>([])
const createdRedeemBatch = ref<{ batchNo: string, codes: string[] } | null>(null)

const planFormOpen = ref(false)
const editingPlanId = ref<string | null>(null)
const planForm = reactive<SaveBillingPlan>({
  code: '',
  name: '',
  description: '',
  priceMinor: 0,
  currency: 'CNY',
  durationDays: 30,
  quotaTokens: 0,
  quotaCycleDays: 30,
  modelIds: [],
  sort: 0,
})

const packFormOpen = ref(false)
const editingPackId = ref<string | null>(null)
const packForm = reactive<SaveBillingTokenPack>({
  code: '',
  name: '',
  description: '',
  priceMinor: 0,
  currency: 'CNY',
  tokens: 1000,
  expireDays: undefined,
  sort: 0,
})

const orderStatus = ref('')
const orderUserId = ref('')
const from = ref(daysAgo(30))
const to = ref(today())
const interval = ref('DAY')
const grantForm = reactive({ userId: '', tokenPackId: '', reason: '' })
const adjustmentForm = reactive({ userId: '', deltaTokens: 0, reason: '' })
const walletAdjustmentForm = reactive({ userId: '', deltaUnits: 0, reason: '' })
const redeemBatchForm = reactive({
  benefitType: 'SUBSCRIPTION',
  productId: '',
  quantity: 1,
  amountUnits: 100,
  validFrom: '',
  expireTime: '',
})

const activePlans = computed(() => plans.value.filter(plan => plan.status === 'PUBLISHED'))
const activePacks = computed(() => packs.value.filter(pack => pack.status === 'PUBLISHED'))
const chatModels = computed(() => models.value.filter(model => model.modelType === 'CHAT' && model.enabled))

function today(): string {
  return new Date().toLocaleDateString('en-CA')
}

function daysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return date.toLocaleDateString('en-CA')
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : '操作失败，请稍后重试'
}

function formatPrice(priceMinor: number, currency: string): string {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currency || 'CNY' })
    .format(priceMinor / 100)
}

function formatWalletBalance(wallet: BillingWallet): string {
  const amount = wallet.balanceUnits / (10 ** wallet.fractionalDigits)
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: wallet.currency,
    minimumFractionDigits: wallet.fractionalDigits,
    maximumFractionDigits: wallet.fractionalDigits,
  }).format(amount)
}

function resetPlanForm(): void {
  editingPlanId.value = null
  Object.assign(planForm, {
    code: '', name: '', description: '', priceMinor: 0, currency: settings.currency || 'CNY',
    durationDays: 30, quotaTokens: 0, quotaCycleDays: 30, modelIds: [], sort: 0,
  })
  planFormOpen.value = true
}

async function editPlan(plan: BillingPlan): Promise<void> {
  try {
    editingPlanId.value = String(plan.id)
    Object.assign(planForm, {
      code: plan.code,
      name: plan.name,
      description: plan.description || '',
      priceMinor: plan.priceMinor,
      currency: plan.currency,
      durationDays: plan.durationDays || 30,
      quotaTokens: plan.quotaTokens,
      quotaCycleDays: plan.quotaCycleDays || 30,
      sort: plan.sort || 0,
      modelIds: await billingApi.planModels(plan.id),
    })
    planFormOpen.value = true
  } catch (cause) {
    toast.error(messageOf(cause))
  }
}

function resetPackForm(): void {
  editingPackId.value = null
  Object.assign(packForm, {
    code: '', name: '', description: '', priceMinor: 0, currency: settings.currency || 'CNY',
    tokens: 1000, expireDays: undefined, sort: 0,
  })
  packFormOpen.value = true
}

function editPack(pack: BillingTokenPack): void {
  editingPackId.value = String(pack.id)
  Object.assign(packForm, {
    code: pack.code,
    name: pack.name,
    description: pack.description || '',
    priceMinor: pack.priceMinor,
    currency: pack.currency,
    tokens: pack.tokens,
    expireDays: pack.expireDays,
    sort: pack.sort || 0,
  })
  packFormOpen.value = true
}

async function loadSettingsAndProducts(): Promise<void> {
  const results = await Promise.all([
    billingApi.adminSettings(),
    billingApi.plans(),
    billingApi.tokenPacks(),
    modelApi.configs(),
  ])
  Object.assign(settings, results[0])
  plans.value = results[1].records
  packs.value = results[2].records
  models.value = results[3]
}

async function loadOrders(): Promise<void> {
  const result = await billingApi.orders({
    page: 1, size: 50, status: orderStatus.value || undefined,
    userId: orderUserId.value.trim() || undefined,
  })
  orders.value = result.records
}

async function loadUsage(): Promise<void> {
  const [summary, byModel, trend] = await Promise.all([
    billingApi.usageSummary(from.value, to.value),
    billingApi.usageByModel(1, 50, from.value, to.value),
    billingApi.usageTrend(from.value, to.value, interval.value),
  ])
  usageSummary.value = summary
  usageByModel.value = byModel.records
  usageTrend.value = trend
}

async function loadRedeemBatches(): Promise<void> {
  const result = await billingApi.redeemCodeBatches()
  redeemBatches.value = result.records
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    if (canRead.value) await loadSettingsAndProducts()
    if (canReadOrders.value) await loadOrders()
    if (canReadUsage.value) await loadUsage()
    if (canReadRedeemCodes.value) await loadRedeemBatches()
  } catch (cause) {
    error.value = messageOf(cause)
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

async function saveSettings(): Promise<void> {
  if (!canUpdate.value) return
  saving.value = true
  try {
    Object.assign(settings, await billingApi.updateAdminSettings({
      ...settings,
      defaultPlanId: settings.defaultPlanId || undefined,
    }))
    siteStore.config.billingEnabled = settings.billingEnabled
    toast.success('计费设置已保存')
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function savePlan(): Promise<void> {
  if (!canUpdate.value) return
  if (!planForm.code.trim() || !planForm.name.trim()) {
    toast.error('请填写方案编码和名称')
    return
  }
  saving.value = true
  try {
    if (editingPlanId.value) await billingApi.updatePlan(editingPlanId.value, { ...planForm })
    else await billingApi.createPlan({ ...planForm })
    toast.success(editingPlanId.value ? '方案已更新' : '方案已创建')
    planFormOpen.value = false
    await loadSettingsAndProducts()
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function changePlanStatus(plan: BillingPlan): Promise<void> {
  saving.value = true
  try {
    if (plan.status === 'PUBLISHED') await billingApi.disablePlan(plan.id)
    else await billingApi.publishPlan(plan.id)
    await loadSettingsAndProducts()
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function savePack(): Promise<void> {
  if (!canUpdate.value) return
  if (!packForm.code.trim() || !packForm.name.trim()) {
    toast.error('请填写补充包编码和名称')
    return
  }
  saving.value = true
  try {
    if (editingPackId.value) await billingApi.updateTokenPack(editingPackId.value, { ...packForm })
    else await billingApi.createTokenPack({ ...packForm })
    toast.success(editingPackId.value ? '补充包已更新' : '补充包已创建')
    packFormOpen.value = false
    await loadSettingsAndProducts()
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function changePackStatus(pack: BillingTokenPack): Promise<void> {
  saving.value = true
  try {
    if (pack.status === 'PUBLISHED') await billingApi.disableTokenPack(pack.id)
    else await billingApi.publishTokenPack(pack.id)
    await loadSettingsAndProducts()
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function grantTokens(): Promise<void> {
  if (!grantForm.userId.trim() || !grantForm.tokenPackId || !grantForm.reason.trim()) {
    toast.error('请填写用户 ID、Token 包和发放原因')
    return
  }
  saving.value = true
  try {
    await billingApi.grantTokens(grantForm.userId, grantForm.tokenPackId, grantForm.reason, crypto.randomUUID())
    toast.success('Token 已发放')
    grantForm.reason = ''
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function adjustTokens(): Promise<void> {
  if (!adjustmentForm.userId.trim() || !adjustmentForm.reason.trim() || !adjustmentForm.deltaTokens) {
    toast.error('请填写用户 ID、调整额度和原因')
    return
  }
  saving.value = true
  try {
    await billingApi.adjustTokens(adjustmentForm.userId, adjustmentForm.deltaTokens, adjustmentForm.reason)
    toast.success('额度已调整')
    adjustmentForm.reason = ''
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function adjustWallet(): Promise<void> {
  if (!walletAdjustmentForm.userId.trim() || !walletAdjustmentForm.reason.trim()
    || !walletAdjustmentForm.deltaUnits) {
    toast.error('请填写用户 ID、调整金额和原因')
    return
  }
  saving.value = true
  try {
    await billingApi.adjustWallet(
      walletAdjustmentForm.userId,
      walletAdjustmentForm.deltaUnits,
      walletAdjustmentForm.reason,
      crypto.randomUUID(),
    )
    toast.success('钱包余额已调整')
    walletAdjustmentForm.reason = ''
    await lookupUserBilling()
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function createRedeemBatch(): Promise<void> {
  const walletCredit = redeemBatchForm.benefitType === 'WALLET_CREDIT'
  if ((!walletCredit && !redeemBatchForm.productId) || !Number.isInteger(redeemBatchForm.quantity)
    || redeemBatchForm.quantity < 1 || redeemBatchForm.quantity > 1000
    || (walletCredit && redeemBatchForm.amountUnits < 1)) {
    toast.error('请检查权益、数量和金额')
    return
  }
  saving.value = true
  try {
    const result = await billingApi.createRedeemCodeBatch({
      benefitType: redeemBatchForm.benefitType,
      productId: walletCredit ? undefined : redeemBatchForm.productId,
      quantity: redeemBatchForm.quantity,
      amountUnits: walletCredit ? redeemBatchForm.amountUnits : undefined,
      validFrom: redeemBatchForm.validFrom || undefined,
      expireTime: redeemBatchForm.expireTime || undefined,
    })
    createdRedeemBatch.value = { batchNo: result.batchNo, codes: result.codes }
    toast.success(`已创建兑换码批次 ${result.batchNo}`)
    if (canReadRedeemCodes.value) await loadRedeemBatches()
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

async function revokeRedeemBatch(batch: BillingRedeemCodeBatch): Promise<void> {
  if (!window.confirm(`确定作废批次 ${batch.batchNo} 中尚未兑换的码吗？`)) return
  saving.value = true
  try {
    await billingApi.revokeRedeemCodeBatch(batch.id)
    toast.success('批次已作废')
    if (canReadRedeemCodes.value) await loadRedeemBatches()
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    saving.value = false
  }
}

function downloadCreatedCodes(): void {
  if (!createdRedeemBatch.value) return
  const blob = new Blob([createdRedeemBatch.value.codes.join('\n')], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${createdRedeemBatch.value.batchNo}.txt`
  link.click()
  URL.revokeObjectURL(url)
}

async function copyCreatedCodes(): Promise<void> {
  if (!createdRedeemBatch.value) return
  try {
    await navigator.clipboard.writeText(createdRedeemBatch.value.codes.join('\n'))
    toast.success('兑换码已复制')
  } catch (cause) {
    toast.error(messageOf(cause))
  }
}

async function refreshOrders(): Promise<void> {
  try { await loadOrders() } catch (cause) { toast.error(messageOf(cause)) }
}

async function refreshUsage(): Promise<void> {
  try { await loadUsage() } catch (cause) { toast.error(messageOf(cause)) }
}

async function lookupUserBilling(): Promise<void> {
  const userId = userIdQuery.value.trim()
  if (!userId) {
    toast.error('请输入用户 ID')
    return
  }
  userLoading.value = true
  try {
    const [details, usage, ledger] = await Promise.all([
      billingApi.userBilling(userId, from.value, to.value),
      billingApi.userUsage(userId, 1, 50),
      billingApi.userLedger(userId, 1, 50),
    ])
    userDetails.value = details
    userUsage.value = usage.records
    userLedger.value = ledger.records
    walletAdjustmentForm.userId = userId
    if (canReadWallet.value) {
      const [wallet, walletLedger] = await Promise.all([
        billingApi.adminWallet(userId),
        billingApi.adminWalletLedger(userId, 1, 50),
      ])
      userWallet.value = wallet
      userWalletLedger.value = walletLedger.records
    } else {
      userWallet.value = null
      userWalletLedger.value = []
    }
  } catch (cause) {
    toast.error(messageOf(cause))
  } finally {
    userLoading.value = false
  }
}

onMounted(() => {
  const first = tabs.value[0]
  if (first) activeTab.value = first.key
  void load()
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 md:p-6">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <div class="flex items-center gap-2">
          <h1 class="text-xl font-semibold">订阅与计费</h1>
          <Badge variant="secondary" class="px-1.5 text-[10px] font-semibold tracking-wider">BETA</Badge>
        </div>
        <p class="mt-1 text-sm text-muted-foreground">管理方案、钱包单位、兑换码、订单与用量。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <IconRefresh class="mr-1 size-4" />刷新
      </Button>
    </header>

    <Alert v-if="error" variant="destructive"><AlertDescription>{{ error }}</AlertDescription></Alert>
    <div v-if="loading" class="flex h-48 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <template v-else>
      <nav class="flex flex-wrap gap-2 border-b pb-3" aria-label="计费管理栏目">
        <Button
          v-for="tab in tabs"
          :key="tab.key"
          size="sm"
          :variant="activeTab === tab.key ? 'default' : 'outline'"
          @click="activeTab = tab.key"
        >
          <component :is="tab.icon" class="mr-1 size-4" />{{ tab.label }}
        </Button>
      </nav>

      <section v-if="activeTab === 'settings' && canRead" class="max-w-3xl rounded-xl border bg-card p-4">
        <div class="mb-4">
          <h2 class="font-semibold">新用户默认计费设置</h2>
          <p class="text-sm text-muted-foreground">注册时应用默认方案并一次性发放初始 Token。</p>
        </div>
        <fieldset :disabled="!canUpdate" class="grid gap-4 border-0 p-0 sm:grid-cols-2">
          <label class="flex items-start gap-3 rounded-lg border p-3 sm:col-span-2">
            <input v-model="settings.billingEnabled" type="checkbox" class="mt-1 size-4 accent-primary" />
            <span>
              <span class="block text-sm font-medium">启用计费功能</span>
              <span class="mt-1 block text-xs text-muted-foreground">
                关闭后隐藏 Platform + 页面，并允许用户不受 Token 额度和方案模型权限限制使用已启用的模型。
              </span>
            </span>
          </label>
          <div class="grid gap-2 sm:col-span-2">
            <Label for="default-plan">默认方案</Label>
            <select id="default-plan" v-model="settings.defaultPlanId" class="h-9 rounded-md border bg-background px-3 text-sm">
              <option value="">未设置</option>
              <option v-for="plan in activePlans" :key="String(plan.id)" :value="String(plan.id)">
                {{ plan.name }}（{{ plan.code }}）
              </option>
            </select>
          </div>
          <div class="grid gap-2">
            <Label for="registration-tokens">注册赠送 Token</Label>
            <Input id="registration-tokens" v-model.number="settings.registrationTokens" type="number" min="0" />
          </div>
          <div class="grid gap-2">
            <Label for="registration-expiry">有效期（天）</Label>
            <Input id="registration-expiry" v-model.number="settings.registrationTokenExpireDays" type="number" min="1" placeholder="留空表示永久有效" />
          </div>
          <div class="grid gap-2">
            <Label for="billing-currency">默认币种</Label>
            <Input id="billing-currency" v-model="settings.currency" maxlength="16" />
          </div>
          <div class="grid gap-2">
            <Label for="wallet-currency">钱包币种</Label>
            <Input id="wallet-currency" v-model="settings.walletCurrency" maxlength="3" />
          </div>
          <div class="grid gap-2">
            <Label for="wallet-fractional-digits">钱包小数位数</Label>
            <Input id="wallet-fractional-digits" v-model.number="settings.walletFractionalDigits" type="number" min="0" max="9" />
          </div>
          <div class="grid gap-2">
            <Label for="wallet-unit-label">最小单位名称</Label>
            <Input id="wallet-unit-label" v-model="settings.walletUnitLabel" maxlength="16" />
          </div>
          <p class="text-xs text-muted-foreground sm:col-span-2">
            钱包余额、流水或余额兑换码生成后，不能直接修改钱包币种与精度。
          </p>
          <div class="flex items-end sm:col-span-2">
            <Button :disabled="saving || !canUpdate" @click="saveSettings">
              <IconDeviceFloppy class="mr-1 size-4" />保存设置
            </Button>
          </div>
        </fieldset>
      </section>

      <section v-if="activeTab === 'plans' && canRead" class="flex flex-col gap-4">
        <header class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 class="font-semibold">订阅方案</h2>
            <p class="text-sm text-muted-foreground">方案仅在发布后展示给用户；模型权限由服务端校验。</p>
          </div>
          <Button v-if="canUpdate" size="sm" @click="resetPlanForm">新增方案</Button>
        </header>

        <section v-if="planFormOpen" class="rounded-xl border bg-card p-4">
          <div class="mb-4 flex items-center justify-between gap-3">
            <h3 class="font-semibold">{{ editingPlanId ? '编辑订阅方案' : '创建订阅方案' }}</h3>
            <Button variant="ghost" size="sm" @click="planFormOpen = false">关闭</Button>
          </div>
          <fieldset :disabled="!canUpdate" class="grid gap-3 border-0 p-0 sm:grid-cols-2 lg:grid-cols-3">
            <div class="grid gap-1.5"><Label>方案编码</Label><Input v-model="planForm.code" /></div>
            <div class="grid gap-1.5"><Label>方案名称</Label><Input v-model="planForm.name" /></div>
            <div class="grid gap-1.5"><Label>描述</Label><Input v-model="planForm.description" /></div>
            <div class="grid gap-1.5"><Label>价格（最小货币单位）</Label><Input v-model.number="planForm.priceMinor" type="number" min="0" /></div>
            <div class="grid gap-1.5"><Label>币种</Label><Input v-model="planForm.currency" /></div>
            <div class="grid gap-1.5"><Label>有效期（天）</Label><Input v-model.number="planForm.durationDays" type="number" min="1" /></div>
            <div class="grid gap-1.5"><Label>每周期 Token</Label><Input v-model.number="planForm.quotaTokens" type="number" min="0" /></div>
            <div class="grid gap-1.5"><Label>配额周期（天）</Label><Input v-model.number="planForm.quotaCycleDays" type="number" min="1" /></div>
            <div class="grid gap-1.5"><Label>排序</Label><Input v-model.number="planForm.sort" type="number" /></div>
            <fieldset class="grid gap-2 border-0 p-0 sm:col-span-2 lg:col-span-3">
              <Label>允许使用的聊天模型</Label>
              <div v-if="chatModels.length" class="grid gap-2 rounded-md border p-3 sm:grid-cols-2 lg:grid-cols-3">
                <label v-for="model in chatModels" :key="model.id" class="flex items-center gap-2 text-sm">
                  <input v-model="planForm.modelIds" type="checkbox" :value="String(model.id)" class="size-4 accent-primary" />
                  <span class="truncate">{{ model.displayName || model.modelId }}</span>
                  <span class="shrink-0 text-xs text-muted-foreground">{{ model.providerName }}</span>
                </label>
              </div>
              <p v-else class="text-sm text-muted-foreground">没有可用的聊天模型，请先在模型配置中启用 CHAT 模型。</p>
            </fieldset>
            <div class="sm:col-span-2 lg:col-span-3">
              <Button :disabled="saving" @click="savePlan"><Spinner v-if="saving" class="mr-1 size-4" />保存方案</Button>
            </div>
          </fieldset>
        </section>

        <div class="overflow-x-auto rounded-xl border">
          <table class="w-full min-w-[760px] text-left text-sm">
            <thead class="bg-muted/50 text-xs text-muted-foreground">
              <tr><th class="p-3">方案</th><th class="p-3">价格</th><th class="p-3">周期 Token</th><th class="p-3">状态</th><th class="p-3">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="plan in plans" :key="String(plan.id)" class="border-t">
                <td class="p-3"><p class="font-medium">{{ plan.name }}</p><p class="text-xs text-muted-foreground">{{ plan.code }}</p></td>
                <td class="p-3">{{ formatPrice(plan.priceMinor, plan.currency) }}</td>
                <td class="p-3">{{ plan.quotaTokens.toLocaleString() }} / {{ plan.quotaCycleDays || '—' }} 天</td>
                <td class="p-3"><Badge :variant="plan.status === 'PUBLISHED' ? 'default' : 'secondary'">{{ plan.status }}</Badge></td>
                <td class="p-3">
                  <div class="flex gap-1">
                    <Button v-if="canUpdate" size="sm" variant="outline" @click="editPlan(plan)">编辑</Button>
                    <Button v-if="canUpdate" size="sm" variant="outline" :disabled="saving" @click="changePlanStatus(plan)">
                      {{ plan.status === 'PUBLISHED' ? '下架' : '发布' }}
                    </Button>
                  </div>
                </td>
              </tr>
              <tr v-if="!plans.length"><td colspan="5" class="p-8 text-center text-muted-foreground">暂无方案</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="activeTab === 'packs' && canRead" class="flex flex-col gap-4">
        <header class="flex flex-wrap items-center justify-between gap-2">
          <div><h2 class="font-semibold">Token 补充包</h2><p class="text-sm text-muted-foreground">管理用户可购买或管理员手动发放的 Token 包。</p></div>
          <Button v-if="canUpdate" size="sm" @click="resetPackForm">新增补充包</Button>
        </header>

        <section v-if="packFormOpen" class="rounded-xl border bg-card p-4">
          <div class="mb-4 flex items-center justify-between gap-3">
            <h3 class="font-semibold">{{ editingPackId ? '编辑补充包' : '创建补充包' }}</h3>
            <Button variant="ghost" size="sm" @click="packFormOpen = false">关闭</Button>
          </div>
          <fieldset :disabled="!canUpdate" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <div class="grid gap-1.5"><Label>补充包编码</Label><Input v-model="packForm.code" /></div>
            <div class="grid gap-1.5"><Label>名称</Label><Input v-model="packForm.name" /></div>
            <div class="grid gap-1.5"><Label>描述</Label><Input v-model="packForm.description" /></div>
            <div class="grid gap-1.5"><Label>价格（最小货币单位）</Label><Input v-model.number="packForm.priceMinor" type="number" min="0" /></div>
            <div class="grid gap-1.5"><Label>币种</Label><Input v-model="packForm.currency" /></div>
            <div class="grid gap-1.5"><Label>Token 数</Label><Input v-model.number="packForm.tokens" type="number" min="1" /></div>
            <div class="grid gap-1.5"><Label>有效期（天）</Label><Input v-model.number="packForm.expireDays" type="number" min="1" placeholder="留空表示永久有效" /></div>
            <div class="grid gap-1.5"><Label>排序</Label><Input v-model.number="packForm.sort" type="number" /></div>
            <div class="flex items-end"><Button :disabled="saving" @click="savePack"><Spinner v-if="saving" class="mr-1 size-4" />保存补充包</Button></div>
          </fieldset>
        </section>

        <div class="overflow-x-auto rounded-xl border">
          <table class="w-full min-w-[760px] text-left text-sm">
            <thead class="bg-muted/50 text-xs text-muted-foreground">
              <tr><th class="p-3">补充包</th><th class="p-3">Token</th><th class="p-3">价格</th><th class="p-3">有效期</th><th class="p-3">状态</th><th class="p-3">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="pack in packs" :key="String(pack.id)" class="border-t">
                <td class="p-3"><p class="font-medium">{{ pack.name }}</p><p class="text-xs text-muted-foreground">{{ pack.code }}</p></td>
                <td class="p-3">{{ pack.tokens.toLocaleString() }}</td>
                <td class="p-3">{{ formatPrice(pack.priceMinor, pack.currency) }}</td>
                <td class="p-3">{{ pack.expireDays ? `${pack.expireDays} 天` : '永久' }}</td>
                <td class="p-3"><Badge :variant="pack.status === 'PUBLISHED' ? 'default' : 'secondary'">{{ pack.status }}</Badge></td>
                <td class="p-3"><div class="flex gap-1">
                  <Button v-if="canUpdate" size="sm" variant="outline" @click="editPack(pack)">编辑</Button>
                  <Button v-if="canUpdate" size="sm" variant="outline" @click="changePackStatus(pack)">
                    {{ pack.status === 'PUBLISHED' ? '下架' : '发布' }}
                  </Button>
                </div></td>
              </tr>
              <tr v-if="!packs.length"><td colspan="6" class="p-8 text-center text-muted-foreground">暂无补充包</td></tr>
            </tbody>
          </table>
        </div>

        <div class="grid gap-4 xl:grid-cols-2">
          <section v-if="canGrant" class="grid gap-3 rounded-xl border bg-card p-4">
            <div class="flex items-center gap-2"><IconGift class="size-4" /><h3 class="font-semibold">手动发放 Token 包</h3></div>
            <div class="grid gap-3 sm:grid-cols-2">
              <div class="grid gap-1.5"><Label>用户 ID</Label><Input v-model="grantForm.userId" /></div>
              <div class="grid gap-1.5"><Label>Token 补充包</Label>
                <select v-model="grantForm.tokenPackId" class="h-9 rounded-md border bg-background px-3 text-sm">
                  <option value="">选择补充包</option>
                  <option v-for="pack in packs" :key="String(pack.id)" :value="String(pack.id)">{{ pack.name }} · {{ pack.tokens }}</option>
                </select>
              </div>
              <div class="grid gap-1.5 sm:col-span-2"><Label>发放原因</Label><Input v-model="grantForm.reason" /></div>
            </div>
            <Button class="w-fit" :disabled="saving" @click="grantTokens">发放</Button>
          </section>
          <section v-if="canAdjust" class="grid gap-3 rounded-xl border bg-card p-4">
            <h3 class="font-semibold">额度调整</h3>
            <div class="grid gap-3 sm:grid-cols-2">
              <div class="grid gap-1.5"><Label>用户 ID</Label><Input v-model="adjustmentForm.userId" /></div>
              <div class="grid gap-1.5"><Label>调整 Token（可正可负）</Label><Input v-model.number="adjustmentForm.deltaTokens" type="number" /></div>
              <div class="grid gap-1.5 sm:col-span-2"><Label>调整原因</Label><Input v-model="adjustmentForm.reason" /></div>
            </div>
            <Button class="w-fit" variant="outline" :disabled="saving" @click="adjustTokens">提交调整</Button>
          </section>
        </div>
      </section>

      <section v-if="activeTab === 'codes' && (canReadRedeemCodes || canManageRedeemCodes)" class="flex flex-col gap-4">
        <header>
          <h2 class="font-semibold">兑换码批次</h2>
          <p class="text-sm text-muted-foreground">兑换码明文仅在创建响应中显示一次，请立即下载并安全保管。</p>
        </header>

        <form v-if="canManageRedeemCodes" class="grid gap-3 rounded-xl border bg-card p-4 sm:grid-cols-2 lg:grid-cols-3" @submit.prevent="createRedeemBatch">
          <div class="grid gap-1.5">
            <Label>权益类型</Label>
            <select v-model="redeemBatchForm.benefitType" class="h-9 rounded-md border bg-background px-3 text-sm">
              <option value="SUBSCRIPTION">订阅方案</option>
              <option value="TOKEN_PACK">Token 补充包</option>
              <option value="WALLET_CREDIT">钱包余额</option>
            </select>
          </div>
          <div v-if="redeemBatchForm.benefitType !== 'WALLET_CREDIT'" class="grid gap-1.5">
            <Label>{{ redeemBatchForm.benefitType === 'SUBSCRIPTION' ? '订阅方案' : 'Token 补充包' }}</Label>
            <select v-model="redeemBatchForm.productId" class="h-9 rounded-md border bg-background px-3 text-sm">
              <option value="">选择已发布商品</option>
              <template v-if="redeemBatchForm.benefitType === 'SUBSCRIPTION'">
                <option v-for="plan in activePlans" :key="String(plan.id)" :value="String(plan.id)">{{ plan.name }}</option>
              </template>
              <template v-else>
                <option v-for="pack in activePacks" :key="String(pack.id)" :value="String(pack.id)">{{ pack.name }}</option>
              </template>
            </select>
          </div>
          <div v-else class="grid gap-1.5">
            <Label>金额（{{ settings.walletUnitLabel || '钱包单位' }}）</Label>
            <Input v-model.number="redeemBatchForm.amountUnits" type="number" min="1" />
          </div>
          <div class="grid gap-1.5">
            <Label>数量（最多 1000）</Label>
            <Input v-model.number="redeemBatchForm.quantity" type="number" min="1" max="1000" />
          </div>
          <div class="grid gap-1.5">
            <Label>生效时间</Label>
            <Input v-model="redeemBatchForm.validFrom" type="datetime-local" />
          </div>
          <div class="grid gap-1.5">
            <Label>失效时间</Label>
            <Input v-model="redeemBatchForm.expireTime" type="datetime-local" />
          </div>
          <div class="sm:col-span-2 lg:col-span-3">
            <Button type="submit" :disabled="saving"><Spinner v-if="saving" class="mr-1 size-4" />创建批次</Button>
          </div>
        </form>

        <section v-if="createdRedeemBatch" class="grid gap-3 rounded-xl border border-amber-500/40 bg-amber-500/5 p-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 class="font-semibold">新建批次：{{ createdRedeemBatch.batchNo }}</h3>
              <p class="text-sm text-muted-foreground">关闭或离开此页面后无法再次查询明文。</p>
            </div>
            <div class="flex gap-2">
              <Button size="sm" variant="outline" @click="copyCreatedCodes">复制</Button>
              <Button size="sm" @click="downloadCreatedCodes">下载 TXT</Button>
            </div>
          </div>
          <textarea :value="createdRedeemBatch.codes.join('\n')" readonly rows="6" class="w-full rounded-md border bg-background p-3 font-mono text-xs" />
        </section>

        <div v-if="canReadRedeemCodes" class="overflow-x-auto rounded-xl border">
          <table class="w-full min-w-[720px] text-left text-sm">
            <thead class="bg-muted/50 text-xs text-muted-foreground">
              <tr><th class="p-3">批次号</th><th class="p-3">权益</th><th class="p-3">已兑换</th><th class="p-3">失效时间</th><th class="p-3">状态</th><th class="p-3">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="batch in redeemBatches" :key="String(batch.id)" class="border-t">
                <td class="p-3">{{ batch.batchNo }}</td>
                <td class="p-3">{{ batch.benefitType }}<span v-if="batch.productId"> · {{ batch.productId }}</span></td>
                <td class="p-3">{{ batch.redeemedCount }} / {{ batch.quantity }}</td>
                <td class="p-3">{{ batch.expireTime || '永久' }}</td>
                <td class="p-3"><Badge :variant="batch.status === 'ACTIVE' ? 'default' : 'secondary'">{{ batch.status }}</Badge></td>
                <td class="p-3">
                  <Button v-if="canManageRedeemCodes && batch.status === 'ACTIVE'" size="sm" variant="outline" :disabled="saving" @click="revokeRedeemBatch(batch)">作废未兑换码</Button>
                </td>
              </tr>
              <tr v-if="!redeemBatches.length"><td colspan="6" class="p-8 text-center text-muted-foreground">暂无兑换码批次</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="activeTab === 'orders' && canReadOrders" class="flex flex-col gap-4">
        <header class="flex flex-wrap items-end justify-between gap-3">
          <div><h2 class="font-semibold">订单</h2><p class="text-sm text-muted-foreground">查看用户订单与支付状态。</p></div>
          <div class="flex flex-wrap items-end gap-2">
            <div class="grid gap-1"><Label>状态</Label><select v-model="orderStatus" class="h-9 rounded-md border bg-background px-3 text-sm">
              <option value="">全部</option><option value="PENDING_PAYMENT">待支付</option><option value="PAID">已支付</option>
              <option value="CANCELED">已取消</option><option value="EXPIRED">已过期</option>
            </select></div>
            <div class="grid gap-1"><Label>用户 ID</Label><Input v-model="orderUserId" class="w-40" /></div>
            <Button variant="outline" @click="refreshOrders">查询</Button>
          </div>
        </header>
        <div class="overflow-x-auto rounded-xl border">
          <table class="w-full min-w-[900px] text-left text-sm">
            <thead class="bg-muted/50 text-xs text-muted-foreground">
              <tr><th class="p-3">订单号</th><th class="p-3">用户</th><th class="p-3">商品</th><th class="p-3">金额</th><th class="p-3">状态</th><th class="p-3">支付渠道</th><th class="p-3">创建时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="order in orders" :key="String(order.id)" class="border-t">
                <td class="p-3 font-mono text-xs">{{ order.orderNo }}</td><td class="p-3">{{ order.userId }}</td>
                <td class="p-3">{{ order.productType }} · {{ order.productId }}</td>
                <td class="p-3">{{ formatPrice(order.amountMinor, order.currency) }}</td>
                <td class="p-3"><Badge variant="outline">{{ order.status }}</Badge></td>
                <td class="p-3">{{ order.paymentProvider || '—' }}</td><td class="p-3">{{ order.createTime || '—' }}</td>
              </tr>
              <tr v-if="!orders.length"><td colspan="7" class="p-8 text-center text-muted-foreground">暂无订单</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="activeTab === 'usage' && canReadUsage" class="flex flex-col gap-4">
        <header class="flex flex-wrap items-end justify-between gap-3">
          <div><h2 class="font-semibold">模型用量</h2><p class="text-sm text-muted-foreground">按所选日期范围汇总计费账本数据。</p></div>
          <div class="flex flex-wrap items-end gap-2">
            <div class="grid gap-1"><Label>开始日期</Label><Input v-model="from" type="date" /></div>
            <div class="grid gap-1"><Label>结束日期</Label><Input v-model="to" type="date" /></div>
            <div class="grid gap-1"><Label>趋势间隔</Label><select v-model="interval" class="h-9 rounded-md border bg-background px-3 text-sm">
              <option value="HOUR">小时</option><option value="DAY">日</option><option value="MONTH">月</option>
            </select></div>
            <Button variant="outline" @click="refreshUsage">查询</Button>
          </div>
        </header>

        <div v-if="usageSummary" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <article v-for="metric in [
            { label: '请求次数', value: usageSummary.requestCount },
            { label: '活跃用户', value: usageSummary.activeUserCount },
            { label: '输入 Token', value: usageSummary.inputTokens },
            { label: '输出 Token', value: usageSummary.outputTokens },
            { label: '扣除额度', value: usageSummary.chargedTokens },
            { label: '成功 / 失败', value: `${usageSummary.successCount} / ${usageSummary.failedCount}` },
            { label: '平均耗时', value: `${usageSummary.averageDurationMs} ms` },
          ]" :key="metric.label" class="rounded-xl border bg-card p-4">
            <p class="text-sm text-muted-foreground">{{ metric.label }}</p><p class="mt-1 text-xl font-semibold">{{ metric.value }}</p>
          </article>
        </div>

        <div class="grid gap-4 xl:grid-cols-2">
          <section class="overflow-x-auto rounded-xl border">
            <h3 class="border-b p-3 text-sm font-semibold">按模型统计</h3>
            <table class="w-full min-w-[560px] text-left text-sm">
              <thead class="bg-muted/50 text-xs text-muted-foreground"><tr><th class="p-3">模型</th><th class="p-3">请求</th><th class="p-3">输入</th><th class="p-3">输出</th><th class="p-3">扣除额度</th></tr></thead>
              <tbody>
                <tr v-for="item in usageByModel" :key="item.modelId" class="border-t">
                  <td class="p-3">{{ item.modelName || item.modelId || '未知模型' }}</td><td class="p-3">{{ item.requestCount }}</td>
                  <td class="p-3">{{ item.inputTokens }}</td><td class="p-3">{{ item.outputTokens }}</td><td class="p-3">{{ item.chargedTokens }}</td>
                </tr>
                <tr v-if="!usageByModel.length"><td colspan="5" class="p-6 text-center text-muted-foreground">暂无用量数据</td></tr>
              </tbody>
            </table>
          </section>

          <section class="overflow-x-auto rounded-xl border">
            <h3 class="border-b p-3 text-sm font-semibold">趋势</h3>
            <table class="w-full min-w-[480px] text-left text-sm">
              <thead class="bg-muted/50 text-xs text-muted-foreground"><tr><th class="p-3">时间</th><th class="p-3">请求</th><th class="p-3">输入 Token</th><th class="p-3">输出 Token</th><th class="p-3">扣除额度</th></tr></thead>
              <tbody>
                <tr v-for="(bucket, index) in usageTrend" :key="String(bucket.time || index)" class="border-t">
                  <td class="p-3">{{ bucket.time }}</td><td class="p-3">{{ bucket.requestCount }}</td><td class="p-3">{{ bucket.inputTokens }}</td>
                  <td class="p-3">{{ bucket.outputTokens }}</td><td class="p-3">{{ bucket.chargedTokens }}</td>
                </tr>
                <tr v-if="!usageTrend.length"><td colspan="5" class="p-6 text-center text-muted-foreground">暂无趋势数据</td></tr>
              </tbody>
            </table>
          </section>
        </div>
      </section>

      <section v-if="activeTab === 'user' && canReadUsage" class="flex flex-col gap-4">
        <header class="flex flex-wrap items-end justify-between gap-3">
          <div><h2 class="font-semibold">用户权益与账本</h2><p class="text-sm text-muted-foreground">查看用户订阅、额度分桶、模型用量及额度流水。</p></div>
          <div class="flex items-end gap-2">
            <div class="grid gap-1"><Label for="billing-user-id">用户 ID</Label><Input id="billing-user-id" v-model="userIdQuery" class="w-64" /></div>
            <Button variant="outline" :disabled="userLoading" @click="lookupUserBilling">
              <Spinner v-if="userLoading" class="mr-1 size-4" />查询
            </Button>
          </div>
        </header>

        <template v-if="userDetails">
          <div class="grid gap-4 xl:grid-cols-2">
            <section class="overflow-x-auto rounded-xl border">
              <h3 class="border-b p-3 text-sm font-semibold">订阅记录</h3>
              <table class="w-full min-w-[480px] text-left text-sm">
                <thead class="bg-muted/50 text-xs text-muted-foreground"><tr><th class="p-3">方案 ID</th><th class="p-3">状态</th><th class="p-3">开始时间</th><th class="p-3">结束时间</th></tr></thead>
                <tbody>
                  <tr v-for="(subscription, index) in userDetails.subscription" :key="String(subscription.id || index)" class="border-t">
                    <td class="p-3">{{ subscription.planId }}</td><td class="p-3">{{ subscription.status }}</td>
                    <td class="p-3">{{ subscription.startTime }}</td><td class="p-3">{{ subscription.endTime }}</td>
                  </tr>
                  <tr v-if="!userDetails.subscription.length"><td colspan="4" class="p-6 text-center text-muted-foreground">暂无订阅记录</td></tr>
                </tbody>
              </table>
            </section>
            <section class="overflow-x-auto rounded-xl border">
              <h3 class="border-b p-3 text-sm font-semibold">额度分桶</h3>
              <table class="w-full min-w-[480px] text-left text-sm">
                <thead class="bg-muted/50 text-xs text-muted-foreground"><tr><th class="p-3">来源</th><th class="p-3">剩余</th><th class="p-3">冻结</th><th class="p-3">到期时间</th></tr></thead>
                <tbody>
                  <tr v-for="lot in userDetails.lots" :key="String(lot.id)" class="border-t">
                    <td class="p-3">{{ lot.sourceType }}</td><td class="p-3">{{ lot.remainingTokens }}</td>
                    <td class="p-3">{{ lot.heldTokens }}</td><td class="p-3">{{ lot.expireTime || '永久' }}</td>
                  </tr>
                  <tr v-if="!userDetails.lots.length"><td colspan="4" class="p-6 text-center text-muted-foreground">暂无额度分桶</td></tr>
                </tbody>
              </table>
            </section>
          </div>

          <section v-if="canReadWallet && userWallet" class="grid gap-4 rounded-xl border bg-card p-4 xl:grid-cols-[1fr_2fr]">
            <div class="grid content-start gap-2">
              <h3 class="font-semibold">用户钱包</h3>
              <p class="text-2xl font-semibold">{{ formatWalletBalance(userWallet) }}</p>
              <p class="text-xs text-muted-foreground">
                {{ userWallet.balanceUnits.toLocaleString() }} {{ userWallet.unitLabel }}
              </p>
            </div>
            <div class="overflow-x-auto">
              <table class="w-full min-w-[560px] text-left text-sm">
                <thead class="text-xs text-muted-foreground"><tr><th class="p-2">时间</th><th class="p-2">类型</th><th class="p-2">变更</th><th class="p-2">变更后余额</th><th class="p-2">原因</th></tr></thead>
                <tbody>
                  <tr v-for="entry in userWalletLedger" :key="String(entry.id)" class="border-t">
                    <td class="p-2">{{ entry.createdAt }}</td><td class="p-2">{{ entry.eventType }}</td>
                    <td class="p-2">{{ entry.deltaUnits.toLocaleString() }} {{ entry.unitLabel }}</td>
                    <td class="p-2">{{ entry.balanceAfterUnits.toLocaleString() }} {{ entry.unitLabel }}</td>
                    <td class="p-2">{{ entry.reason || '—' }}</td>
                  </tr>
                  <tr v-if="!userWalletLedger.length"><td colspan="5" class="p-5 text-center text-muted-foreground">暂无钱包流水</td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <section v-if="canAdjustWallet" class="grid gap-3 rounded-xl border bg-card p-4">
            <h3 class="font-semibold">钱包余额调整</h3>
            <div class="grid gap-3 sm:grid-cols-3">
              <div class="grid gap-1.5"><Label>用户 ID</Label><Input v-model="walletAdjustmentForm.userId" /></div>
              <div class="grid gap-1.5"><Label>调整整数单位（可正可负）</Label><Input v-model.number="walletAdjustmentForm.deltaUnits" type="number" /></div>
              <div class="grid gap-1.5"><Label>原因</Label><Input v-model="walletAdjustmentForm.reason" /></div>
            </div>
            <Button class="w-fit" variant="outline" :disabled="saving" @click="adjustWallet">提交调整</Button>
          </section>

          <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <article v-for="metric in [
              { label: '请求次数', value: userDetails.usage.requestCount },
              { label: '输入 Token', value: userDetails.usage.inputTokens },
              { label: '输出 Token', value: userDetails.usage.outputTokens },
              { label: '扣除额度', value: userDetails.usage.chargedTokens },
            ]" :key="metric.label" class="rounded-xl border bg-card p-4">
              <p class="text-sm text-muted-foreground">{{ metric.label }}</p><p class="mt-1 text-xl font-semibold">{{ metric.value }}</p>
            </article>
          </div>

          <div class="grid gap-4 xl:grid-cols-2">
            <section class="overflow-x-auto rounded-xl border">
              <h3 class="border-b p-3 text-sm font-semibold">最近用量</h3>
              <table class="w-full min-w-[600px] text-left text-sm">
                <thead class="bg-muted/50 text-xs text-muted-foreground"><tr><th class="p-3">时间</th><th class="p-3">模型</th><th class="p-3">输入</th><th class="p-3">输出</th><th class="p-3">扣除</th><th class="p-3">状态</th></tr></thead>
                <tbody>
                  <tr v-for="usage in userUsage" :key="usage.requestId" class="border-t">
                    <td class="p-3">{{ usage.createdAt }}</td><td class="p-3">{{ usage.modelNameSnapshot || usage.modelConfigId }}</td>
                    <td class="p-3">{{ usage.inputTokens }}</td><td class="p-3">{{ usage.outputTokens }}</td>
                    <td class="p-3">{{ usage.chargedTokens }}</td><td class="p-3">{{ usage.status }}</td>
                  </tr>
                  <tr v-if="!userUsage.length"><td colspan="6" class="p-6 text-center text-muted-foreground">暂无用量记录</td></tr>
                </tbody>
              </table>
            </section>
            <section class="overflow-x-auto rounded-xl border">
              <h3 class="border-b p-3 text-sm font-semibold">最近额度流水</h3>
              <table class="w-full min-w-[480px] text-left text-sm">
                <thead class="bg-muted/50 text-xs text-muted-foreground"><tr><th class="p-3">时间</th><th class="p-3">类型</th><th class="p-3">变更</th><th class="p-3">变更后余额</th></tr></thead>
                <tbody>
                  <tr v-for="entry in userLedger" :key="String(entry.id)" class="border-t">
                    <td class="p-3">{{ entry.createdAt }}</td><td class="p-3">{{ entry.eventType }}</td>
                    <td class="p-3">{{ entry.deltaTokens }}</td><td class="p-3">{{ entry.balanceAfter }}</td>
                  </tr>
                  <tr v-if="!userLedger.length"><td colspan="4" class="p-6 text-center text-muted-foreground">暂无额度流水</td></tr>
                </tbody>
              </table>
            </section>
          </div>
        </template>
      </section>
    </template>
  </div>
</template>
