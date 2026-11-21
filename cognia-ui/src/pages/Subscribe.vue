<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { toast } from 'vue-sonner'
import {
  billingApi,
  type BillingMe,
  type BillingPlan,
  type BillingRedemption,
  type BillingTokenPack,
  type BillingWallet,
  type BillingWalletLedger,
} from '@/api'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Spinner } from '@/components/ui/spinner'
import { IconCheck, IconCreditCard, IconRefresh, IconTicket } from '@tabler/icons-vue'

const plans = ref<BillingPlan[]>([])
const tokenPacks = ref<BillingTokenPack[]>([])
const currentPlanId = ref<string | null>(null)
const wallet = ref<BillingWallet | null>(null)
const walletLedger = ref<BillingWalletLedger[]>([])
const redemptions = ref<BillingRedemption[]>([])
const loading = ref(true)
const error = ref('')
const busyProduct = ref('')
const redeemCode = ref('')
const redeeming = ref(false)
const redeemDialogOpen = ref(false)

const publishedPlans = computed(() => plans.value.filter(plan => plan.status === 'PUBLISHED'))
const publishedTokenPacks = computed(() => tokenPacks.value.filter(pack => pack.status === 'PUBLISHED'))

function formatPrice(priceMinor: number, currency: string): string {
  const fractionalDigits = currencyFractionalDigits(currency) ?? 2
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: currency || 'CNY',
    minimumFractionDigits: fractionalDigits,
    maximumFractionDigits: fractionalDigits,
  }).format(priceMinor / (10 ** fractionalDigits))
}

function currencyFractionalDigits(currency: string): number | null {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions().maximumFractionDigits
}

function formatWalletBalance(value: BillingWallet): string {
  const scale = 10 ** value.fractionalDigits
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: value.currency,
    minimumFractionDigits: value.fractionalDigits,
    maximumFractionDigits: value.fractionalDigits,
  }).format(value.balanceUnits / scale)
}

function priceInWalletUnits(priceMinor: number, currency: string): number | null {
  if (!wallet.value || wallet.value.currency.toUpperCase() !== currency.toUpperCase()
    || !Number.isSafeInteger(priceMinor) || priceMinor < 0) return null
  const currencyDigits = currencyFractionalDigits(currency)
  if (currencyDigits === null) return null
  const difference = wallet.value.fractionalDigits - currencyDigits
  const amount = BigInt(priceMinor)
  const units = difference >= 0
    ? amount * (10n ** BigInt(difference))
    : amount % (10n ** BigInt(-difference)) === 0n
      ? amount / (10n ** BigInt(-difference))
      : null
  if (units === null || units > BigInt(Number.MAX_SAFE_INTEGER)) return null
  return Number(units)
}

function walletCanPay(priceMinor: number, currency: string): boolean {
  const priceUnits = priceInWalletUnits(priceMinor, currency)
  return priceUnits !== null && wallet.value !== null && wallet.value.balanceUnits >= priceUnits
}

function walletPriceLabel(priceMinor: number, currency: string): string {
  const units = priceInWalletUnits(priceMinor, currency)
  if (units === null || !wallet.value) return ''
  return `${units.toLocaleString()} ${wallet.value.unitLabel}`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [catalog, me, redemptionPage, walletLedgerPage]: [
      Awaited<ReturnType<typeof billingApi.catalog>>,
      BillingMe,
      Awaited<ReturnType<typeof billingApi.redemptions>>,
      Awaited<ReturnType<typeof billingApi.walletLedger>>,
    ] = await Promise.all([
      billingApi.catalog(),
      billingApi.me(),
      billingApi.redemptions(1, 10),
      billingApi.walletLedger(1, 10),
    ])
    plans.value = catalog.plans
    tokenPacks.value = catalog.tokenPacks
    wallet.value = me.wallet ?? await billingApi.wallet()
    walletLedger.value = walletLedgerPage.records
    redemptions.value = redemptionPage.records
    const planId = me.activeSubscription?.planId ?? me.defaultPlan?.id
    currentPlanId.value = planId == null ? null : String(planId)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载订阅信息失败'
  } finally {
    loading.value = false
  }
}

async function purchase(productType: string, productId: string | number, key: string): Promise<void> {
  if (busyProduct.value) return
  busyProduct.value = key
  try {
    const order = await billingApi.createOrder(productType, productId, crypto.randomUUID())
    if (order.status !== 'PAID') throw new Error('钱包支付未完成，请刷新订单状态后重试')
    toast.success('支付成功，权益已开通')
    await load()
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '购买失败')
  } finally {
    busyProduct.value = ''
  }
}

async function redeem(): Promise<void> {
  const code = redeemCode.value.trim()
  if (!code || redeeming.value) return
  redeeming.value = true
  try {
    const result = await billingApi.redeemCode(code, crypto.randomUUID())
    const benefitType = String(result.benefitType || '')
    toast.success(benefitType === 'WALLET_CREDIT' ? '兑换成功，余额已到账' : '兑换成功，权益已发放')
    redeemCode.value = ''
    redeemDialogOpen.value = false
    await load()
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '兑换失败')
  } finally {
    redeeming.value = false
  }
}

function isCurrentPlan(plan: BillingPlan): boolean {
  return currentPlanId.value === String(plan.id)
}

onMounted(() => void load())
</script>

<template>
  <main class="mx-auto flex w-full max-w-[1320px] flex-col gap-10 px-4 py-8 sm:px-6 lg:px-8">
    <header class="relative overflow-hidden rounded-[2rem] border bg-card px-6 py-10 sm:px-10 sm:py-14">
      <div class="pointer-events-none absolute -right-16 -top-32 size-80 rounded-full bg-primary/10 blur-3xl" />
      <div class="relative flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
        <div class="max-w-2xl">
          <p class="mb-3 text-xs font-semibold uppercase tracking-[0.24em] text-primary">Cognia membership</p>
          <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">Platform +</h1>
          <p class="mt-4 max-w-xl text-base leading-7 text-muted-foreground">
            为你的工作流选择合适的访问额度。方案权益清晰透明，可随时查看和管理。
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-3">
          <div class="min-w-44 rounded-2xl border bg-background/80 px-4 py-3">
            <p class="text-xs text-muted-foreground">钱包余额</p>
            <p class="mt-1 text-lg font-semibold">{{ wallet ? formatWalletBalance(wallet) : '—' }}</p>
          </div>
          <Button variant="outline" class="h-12 rounded-xl px-5" @click="redeemDialogOpen = true">
            <IconTicket class="mr-2 size-4" />兑换码
          </Button>
        </div>
      </div>
    </header>

    <Alert v-if="error" variant="destructive">
      <AlertDescription class="flex flex-wrap items-center justify-between gap-3">
        <span>{{ error }}</span>
        <Button size="sm" variant="outline" @click="load">
          <IconRefresh class="mr-1 size-4" />重试
        </Button>
      </AlertDescription>
    </Alert>

    <div v-if="loading" class="flex h-64 items-center justify-center rounded-xl border">
      <Spinner class="size-6" />
    </div>

    <template v-else>
      <section class="flex flex-col gap-6" aria-labelledby="subscription-plans-title">
        <div class="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Plans</p>
            <h2 id="subscription-plans-title" class="text-2xl font-semibold tracking-tight">选择你的方案</h2>
          </div>
          <p class="text-sm text-muted-foreground">所有价格均以商品标示币种计价，可使用兼容的钱包余额支付。</p>
        </div>

        <div v-if="publishedPlans.length" class="grid grid-cols-1 items-stretch gap-5 md:grid-cols-2 xl:grid-cols-3">
          <article
            v-for="plan in publishedPlans"
            :key="String(plan.id)"
            class="group flex min-w-0 flex-col rounded-3xl border bg-card p-6 shadow-sm transition duration-200 hover:-translate-y-1 hover:shadow-lg"
          >
            <div class="flex min-h-8 items-center justify-between gap-2">
              <h3 class="truncate text-lg font-semibold">{{ plan.name }}</h3>
              <Badge v-if="isCurrentPlan(plan)" variant="secondary">当前方案</Badge>
            </div>
            <p v-if="plan.description" class="mt-2 min-h-12 text-sm leading-6 text-muted-foreground">
              {{ plan.description }}
            </p>
            <p v-else class="mt-2 min-h-12 text-sm text-muted-foreground">适合持续使用 Cognia 的日常工作流。</p>

            <div class="mt-5 flex min-h-12 items-baseline gap-2">
              <span class="text-4xl font-semibold tracking-tight">
                {{ formatPrice(plan.priceMinor, plan.currency) }}
              </span>
              <span class="text-sm text-muted-foreground">/ {{ plan.durationDays ? `${plan.durationDays} 天` : '永久' }}</span>
            </div>

            <Button
              class="mt-5 h-11 w-full rounded-xl"
              :disabled="isCurrentPlan(plan) || busyProduct !== '' || !walletCanPay(plan.priceMinor, plan.currency)"
              @click="purchase('SUBSCRIPTION', plan.id, `plan-${plan.id}`)"
            >
              <Spinner v-if="busyProduct === `plan-${plan.id}`" class="mr-2 size-4" />
              {{ isCurrentPlan(plan) ? '当前方案' : walletCanPay(plan.priceMinor, plan.currency) ? '使用钱包余额' : '余额不足或币种不匹配' }}
            </Button>

            <p v-if="walletPriceLabel(plan.priceMinor, plan.currency)" class="mt-2 text-center text-xs text-muted-foreground">
              本次扣除 {{ walletPriceLabel(plan.priceMinor, plan.currency) }}
            </p>

            <div class="my-6 border-t" />
            <ul class="flex flex-1 flex-col gap-4 text-sm">
              <li class="flex items-start gap-3">
                <span class="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <IconCheck class="size-3.5" />
                </span>
                <span><strong class="font-semibold">{{ plan.quotaTokens.toLocaleString() }}</strong> Token / 周期</span>
              </li>
              <li class="flex items-start gap-3">
                <span class="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <IconCheck class="size-3.5" />
                </span>
                <span>{{ plan.quotaCycleDays ? `每 ${plan.quotaCycleDays} 天发放额度` : '包含方案额度' }}</span>
              </li>
              <li class="flex items-start gap-3">
                <span class="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <IconCheck class="size-3.5" />
                </span>
                <span>方案有效期 {{ plan.durationDays ? `${plan.durationDays} 天` : '永久' }}</span>
              </li>
            </ul>
          </article>
        </div>
        <div v-else-if="!error" class="rounded-xl border border-dashed py-12 text-center text-sm text-muted-foreground">
          暂无已发布的订阅方案
        </div>
      </section>

      <section class="flex flex-col gap-5" aria-labelledby="token-packs-title">
        <div class="flex items-end justify-between gap-3">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Add-ons</p>
            <h2 id="token-packs-title" class="mt-1 text-2xl font-semibold tracking-tight">Token 补充包</h2>
            <p class="mt-1 text-sm text-muted-foreground">按需补充额度，不改变当前订阅方案和模型权限。</p>
          </div>
          <IconCreditCard class="hidden size-6 text-muted-foreground sm:block" />
        </div>

        <div v-if="publishedTokenPacks.length" class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <article
            v-for="pack in publishedTokenPacks"
            :key="String(pack.id)"
            class="flex flex-col rounded-2xl border bg-card p-5 transition-colors hover:bg-muted/20"
          >
            <div class="flex items-start justify-between gap-3">
              <div>
                <h3 class="font-semibold">{{ pack.name }}</h3>
                <p class="mt-1 text-2xl font-semibold">{{ pack.tokens.toLocaleString() }} <span class="text-sm font-normal text-muted-foreground">Token</span></p>
              </div>
              <Badge variant="outline">{{ pack.expireDays ? `${pack.expireDays} 天有效` : '永久有效' }}</Badge>
            </div>
            <p v-if="pack.description" class="mt-3 min-h-10 text-sm text-muted-foreground">{{ pack.description }}</p>
            <div v-else class="mt-3 min-h-10" />
            <p class="text-sm text-muted-foreground">{{ formatPrice(pack.priceMinor, pack.currency) }}</p>
            <Button
              class="mt-4 h-10 w-full rounded-xl"
              variant="outline"
              :disabled="busyProduct !== '' || !walletCanPay(pack.priceMinor, pack.currency)"
              @click="purchase('TOKEN_PACK', pack.id, `pack-${pack.id}`)"
            >
              <Spinner v-if="busyProduct === `pack-${pack.id}`" class="mr-2 size-4" />
              {{ walletCanPay(pack.priceMinor, pack.currency) ? '使用钱包余额' : '余额不足或币种不匹配' }}
            </Button>
            <p v-if="walletPriceLabel(pack.priceMinor, pack.currency)" class="mt-2 text-center text-xs text-muted-foreground">
              本次扣除 {{ walletPriceLabel(pack.priceMinor, pack.currency) }}
            </p>
          </article>
        </div>
        <div v-else-if="!error" class="rounded-xl border border-dashed py-10 text-center text-sm text-muted-foreground">
          暂无已发布的 Token 补充包
        </div>
      </section>

      <section v-if="walletLedger.length || redemptions.length" class="grid gap-5 lg:grid-cols-2">
        <article v-if="walletLedger.length" class="rounded-2xl border bg-card p-5">
          <h2 class="font-semibold">最近钱包流水</h2>
          <ul class="mt-3 divide-y text-sm">
            <li v-for="entry in walletLedger" :key="String(entry.id)" class="flex justify-between gap-3 py-2.5">
              <span class="text-muted-foreground">{{ entry.eventType }}</span>
              <span class="font-medium">{{ entry.deltaUnits > 0 ? '+' : '' }}{{ entry.deltaUnits.toLocaleString() }} {{ entry.unitLabel }}</span>
            </li>
          </ul>
        </article>
        <article v-if="redemptions.length" class="rounded-2xl border bg-card p-5">
          <h2 class="font-semibold">最近兑换记录</h2>
          <ul class="mt-3 divide-y text-sm">
            <li v-for="redemption in redemptions" :key="String(redemption.id)" class="flex justify-between gap-3 py-2.5">
              <span>{{ redemption.benefitType }}</span>
              <span class="text-muted-foreground">{{ redemption.redeemedAt }}</span>
            </li>
          </ul>
        </article>
      </section>
    </template>

    <Dialog v-model:open="redeemDialogOpen">
      <DialogContent class="sm:max-w-md">
        <form @submit.prevent="redeem">
          <DialogHeader>
            <DialogTitle>兑换权益</DialogTitle>
            <DialogDescription>输入兑换码以领取订阅方案、Token 补充包或钱包余额。</DialogDescription>
          </DialogHeader>
          <div class="grid gap-2 py-5">
            <label for="billing-redeem-code" class="text-sm font-medium">兑换码</label>
            <input
              id="billing-redeem-code"
              v-model="redeemCode"
              autocomplete="off"
              autofocus
              class="h-11 rounded-xl border bg-background px-3 font-mono text-sm uppercase tracking-wide outline-none focus-visible:ring-2 focus-visible:ring-ring"
              placeholder="XXXX-XXXX-XXXX-XXXX"
            >
            <p v-if="wallet" class="text-xs text-muted-foreground">
              当前钱包余额：{{ formatWalletBalance(wallet) }}
            </p>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" :disabled="redeeming" @click="redeemDialogOpen = false">取消</Button>
            <Button type="submit" :disabled="redeeming || !redeemCode.trim()">
              <Spinner v-if="redeeming" class="mr-2 size-4" />
              {{ redeeming ? '兑换中…' : '确认兑换' }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </main>
</template>
