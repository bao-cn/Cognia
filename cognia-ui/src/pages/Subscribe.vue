<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { billingApi, type BillingMe, type BillingPlan, type BillingTokenPack } from '@/api'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { IconCheck, IconCreditCard, IconRefresh } from '@tabler/icons-vue'

const plans = ref<BillingPlan[]>([])
const tokenPacks = ref<BillingTokenPack[]>([])
const currentPlanId = ref<string | null>(null)
const loading = ref(true)
const error = ref('')
const purchaseReady = false

const publishedPlans = computed(() => plans.value.filter(plan => plan.status === 'PUBLISHED'))
const publishedTokenPacks = computed(() => tokenPacks.value.filter(pack => pack.status === 'PUBLISHED'))

function formatPrice(priceMinor: number, currency: string): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: currency || 'CNY',
    maximumFractionDigits: 2,
  }).format(priceMinor / 100)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [catalog, me]: [Awaited<ReturnType<typeof billingApi.catalog>>, BillingMe] = await Promise.all([
      billingApi.catalog(),
      billingApi.me(),
    ])
    plans.value = catalog.plans
    tokenPacks.value = catalog.tokenPacks
    const planId = me.activeSubscription?.planId ?? me.defaultPlan?.id
    currentPlanId.value = planId == null ? null : String(planId)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载订阅信息失败'
  } finally {
    loading.value = false
  }
}

function isCurrentPlan(plan: BillingPlan): boolean {
  return currentPlanId.value === String(plan.id)
}

onMounted(() => void load())
</script>

<template>
  <main class="mx-auto flex w-full max-w-[1440px] flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
    <header class="flex flex-col items-center gap-2 text-center">
      <h1 class="text-2xl font-semibold tracking-tight sm:text-3xl">订阅方案</h1>
      <p class="text-sm text-muted-foreground">查看可用方案与 Token 补充包。</p>
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
      <section class="flex flex-col gap-4" aria-labelledby="subscription-plans-title">
        <div class="flex items-center justify-between gap-3">
          <div>
            <h2 id="subscription-plans-title" class="text-lg font-semibold">订阅方案</h2>
            <p class="text-sm text-muted-foreground">方案价格、周期额度和可用权益。</p>
          </div>
        </div>

        <div v-if="publishedPlans.length" class="grid grid-cols-1 items-stretch gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <article
            v-for="plan in publishedPlans"
            :key="String(plan.id)"
            class="flex min-w-0 flex-col rounded-3xl border bg-card p-4 shadow-sm sm:p-5"
          >
            <div class="flex min-h-7 items-center justify-between gap-2">
              <h3 class="truncate text-base font-semibold">{{ plan.name }}</h3>
              <Badge v-if="isCurrentPlan(plan)" variant="secondary">当前方案</Badge>
            </div>
            <p v-if="plan.description" class="mt-2 min-h-10 text-sm text-muted-foreground">
              {{ plan.description }}
            </p>
            <p v-else class="mt-2 min-h-10 text-sm text-muted-foreground">—</p>

            <div class="mt-2 flex items-baseline gap-1">
              <span class="text-3xl font-semibold tracking-tight">
                {{ formatPrice(plan.priceMinor, plan.currency) }}
              </span>
              <span class="text-sm text-muted-foreground">
                / {{ plan.durationDays ? `${plan.durationDays} 天` : '永久' }}
              </span>
            </div>

            <Button class="mt-4 w-full" :disabled="!purchaseReady || isCurrentPlan(plan)">
              {{ isCurrentPlan(plan) ? '当前方案' : '选择方案' }}
            </Button>

            <ul class="mt-5 flex flex-col gap-3 text-sm">
              <li class="flex items-start gap-2">
                <IconCheck class="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <span>{{ plan.quotaTokens.toLocaleString() }} Token / 周期</span>
              </li>
              <li v-if="plan.quotaCycleDays" class="flex items-start gap-2">
                <IconCheck class="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <span>每 {{ plan.quotaCycleDays }} 天发放</span>
              </li>
            </ul>
          </article>
        </div>
        <div v-else-if="!error" class="rounded-xl border border-dashed py-12 text-center text-sm text-muted-foreground">
          暂无已发布的订阅方案
        </div>
      </section>

      <section class="flex flex-col gap-4" aria-labelledby="token-packs-title">
        <div class="flex items-center gap-2">
          <IconCreditCard class="size-5 text-muted-foreground" />
          <div>
            <h2 id="token-packs-title" class="text-lg font-semibold">Token 补充包</h2>
            <p class="text-sm text-muted-foreground">补充额度不改变当前订阅方案和模型权限。</p>
          </div>
        </div>

        <div v-if="publishedTokenPacks.length" class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <article
            v-for="pack in publishedTokenPacks"
            :key="String(pack.id)"
            class="flex flex-col rounded-2xl border bg-card p-4"
          >
            <h3 class="font-semibold">{{ pack.name }}</h3>
            <p v-if="pack.description" class="mt-1 min-h-10 text-sm text-muted-foreground">{{ pack.description }}</p>
            <p v-else class="mt-1 min-h-10 text-sm text-muted-foreground">—</p>
            <p class="mt-3 text-2xl font-semibold">{{ pack.tokens.toLocaleString() }} <span class="text-sm font-normal">Token</span></p>
            <p class="mt-1 text-sm text-muted-foreground">
              {{ formatPrice(pack.priceMinor, pack.currency) }}
              <span v-if="pack.expireDays"> · {{ pack.expireDays }} 天有效</span>
              <span v-else> · 永久有效</span>
            </p>
            <Button class="mt-4 w-full" variant="outline" :disabled="!purchaseReady">选择补充包</Button>
          </article>
        </div>
        <div v-else-if="!error" class="rounded-xl border border-dashed py-10 text-center text-sm text-muted-foreground">
          暂无已发布的 Token 补充包
        </div>
      </section>
    </template>
  </main>
</template>
