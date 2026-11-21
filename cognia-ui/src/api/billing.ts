import { http } from './http'
import type { PageResult } from '@/types/api'

export interface BillingPlan {
  id: number | string
  code: string
  name: string
  description?: string
  planType: string
  priceMinor: number
  currency: string
  durationDays?: number
  quotaTokens: number
  quotaCycleDays?: number
  status: string
  isDefault: boolean
  version: number
  sort: number
}

export interface BillingTokenPack {
  id: number | string
  code: string
  name: string
  description?: string
  priceMinor: number
  currency: string
  tokens: number
  expireDays?: number
  status: string
  sort: number
}

export interface BillingOrder {
  id: number | string
  orderNo: string
  userId: number | string
  productType: string
  productId: number | string
  status: string
  amountMinor: number
  currency: string
  paymentProvider?: string
  expireTime?: string
  paidTime?: string
  createTime?: string
}

export interface BillingSettings {
  defaultPlanId?: number | string
  registrationTokens: number
  registrationTokenExpireDays?: number
  currency: string
}

export interface BillingMe {
  defaultPlan?: { id: string | number, name: string }
  activeSubscription?: { id: string | number, planId: string | number }
  balance?: {
    availableTokens: number
    heldTokens: number
    subscriptionTokens: number
    topUpTokens: number
  }
}

export interface BillingUsageSummary {
  range: { from: string, to: string }
  requestCount: number
  successCount: number
  failedCount: number
  activeUserCount: number
  inputTokens: number
  outputTokens: number
  chargedTokens: number
  averageDurationMs: number
}

export interface BillingUsageByModel {
  modelId?: string
  modelName?: string
  requestCount: number
  inputTokens: number
  outputTokens: number
  chargedTokens: number
  failedCount: number
}

export interface BillingTokenUsage {
  requestId: string
  userId: string | number
  modelConfigId?: string | number
  modelNameSnapshot?: string
  inputTokens: number
  outputTokens: number
  chargedTokens: number
  durationMs: number
  status: string
  createdAt: string
}

export interface BillingTokenLedger {
  id: string | number
  eventType: string
  deltaTokens: number
  balanceAfter: number
  sourceType?: string
  createdAt: string
}

export interface BillingUserDetails {
  subscription: Record<string, unknown>[]
  lots: Record<string, unknown>[]
  usage: {
    requestCount: number
    inputTokens: number
    outputTokens: number
    chargedTokens: number
  }
}

export interface SaveBillingPlan {
  code: string
  name: string
  description?: string
  priceMinor: number
  currency: string
  durationDays: number
  quotaTokens: number
  quotaCycleDays: number
  modelIds: string[]
  sort: number
}

export interface SaveBillingTokenPack {
  code: string
  name: string
  description?: string
  priceMinor: number
  currency: string
  tokens: number
  expireDays?: number
  sort: number
}

export const billingApi = {
  catalog(): Promise<{ plans: BillingPlan[], tokenPacks: BillingTokenPack[] }> {
    return http.get('/billing/catalog')
  },
  me(): Promise<BillingMe> {
    return http.get('/billing/me')
  },
  adminSettings(): Promise<BillingSettings> {
    return http.get('/admin/billing/settings')
  },
  updateAdminSettings(payload: BillingSettings): Promise<BillingSettings> {
    return http.put('/admin/billing/settings', payload)
  },
  plans(page = 1, size = 100): Promise<PageResult<BillingPlan>> {
    return http.get('/admin/billing/plans', { params: { page, size } })
  },
  createPlan(payload: SaveBillingPlan): Promise<BillingPlan> {
    return http.post('/admin/billing/plans', payload)
  },
  updatePlan(id: number | string, payload: SaveBillingPlan): Promise<BillingPlan> {
    return http.put(`/admin/billing/plans/${id}`, payload)
  },
  replacePlanModels(id: number | string, modelIds: string[]): Promise<void> {
    return http.put(`/admin/billing/plans/${id}/models`, { modelIds })
  },
  planModels(id: number | string): Promise<string[]> {
    return http.get(`/admin/billing/plans/${id}/models`)
  },
  publishPlan(id: number | string): Promise<void> {
    return http.post(`/admin/billing/plans/${id}/publish`)
  },
  disablePlan(id: number | string): Promise<void> {
    return http.post(`/admin/billing/plans/${id}/disable`)
  },
  tokenPacks(page = 1, size = 100): Promise<PageResult<BillingTokenPack>> {
    return http.get('/admin/billing/token-packs', { params: { page, size } })
  },
  createTokenPack(payload: SaveBillingTokenPack): Promise<BillingTokenPack> {
    return http.post('/admin/billing/token-packs', payload)
  },
  updateTokenPack(id: number | string, payload: SaveBillingTokenPack): Promise<BillingTokenPack> {
    return http.put(`/admin/billing/token-packs/${id}`, payload)
  },
  disableTokenPack(id: number | string): Promise<void> {
    return http.post(`/admin/billing/token-packs/${id}/disable`)
  },
  publishTokenPack(id: number | string): Promise<void> {
    return http.post(`/admin/billing/token-packs/${id}/publish`)
  },
  orders(params: { page?: number, size?: number, status?: string, userId?: string }): Promise<PageResult<BillingOrder>> {
    return http.get('/admin/billing/orders', { params })
  },
  usageSummary(from?: string, to?: string): Promise<BillingUsageSummary> {
    return http.get('/admin/billing/usage/summary', { params: { from, to } })
  },
  usageByModel(page = 1, size = 100, from?: string, to?: string): Promise<PageResult<BillingUsageByModel>> {
    return http.get('/admin/billing/usage/by-model', { params: { page, size, from, to } })
  },
  usageTrend(from?: string, to?: string, interval = 'DAY'): Promise<Record<string, number | string>[]> {
    return http.get('/admin/billing/usage/trend', { params: { from, to, interval } })
  },
  userBilling(userId: string, from?: string, to?: string): Promise<BillingUserDetails> {
    return http.get(`/admin/billing/users/${userId}`, { params: { from, to } })
  },
  userUsage(userId: string, page = 1, size = 20): Promise<PageResult<BillingTokenUsage>> {
    return http.get(`/admin/billing/users/${userId}/usage`, { params: { page, size } })
  },
  userLedger(userId: string, page = 1, size = 20): Promise<PageResult<BillingTokenLedger>> {
    return http.get(`/admin/billing/users/${userId}/ledger`, { params: { page, size } })
  },
  grantTokens(userId: string, tokenPackId: string, reason: string, idempotencyKey: string): Promise<Record<string, unknown>> {
    return http.post(`/admin/billing/users/${userId}/token-grants`, { tokenPackId, reason }, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
  },
  adjustTokens(userId: string, deltaTokens: number, reason: string): Promise<Record<string, unknown>> {
    return http.post(`/admin/billing/users/${userId}/adjustments`, { deltaTokens, reason })
  },
}
