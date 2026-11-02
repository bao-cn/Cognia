import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { toast } from 'vue-sonner'
import type { ApiResponse, LoginResult } from '@/types/api'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export class ApiError extends Error {
  readonly code: number
  readonly status: number

  constructor(message: string, code = -1, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export const httpClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

httpClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshPromise: Promise<string> | null = null
let loggingOut = false

async function applyRefreshedTokens(data: LoginResult): Promise<string> {
  setTokens(data.accessToken, data.refreshToken)
  // Dynamic import avoids a circular dependency between this module,
  // the user store and the router.
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  userStore.applyTokens(data.accessToken, data.refreshToken)
  if (data.user) {
    userStore.setProfile(data.user)
  }
  return data.accessToken
}

async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new ApiError('登录已过期，请重新登录', 40101, 401)
  }
  const response = await axios.post<ApiResponse<LoginResult>>(
    `${BASE_URL}/auth/refresh`,
    { refreshToken },
    { timeout: 15000 },
  )
  const payload = response.data?.data
  if (!payload?.accessToken) {
    throw new ApiError('登录已过期，请重新登录', 40101, 401)
  }
  return applyRefreshedTokens(payload)
}

function refreshOnce(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function forceLogout(message = '登录已过期，请重新登录'): Promise<void> {
  if (loggingOut) return
  loggingOut = true
  clearTokens()
  try {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().clearAuth()
    const { default: router } = await import('@/router')
    const current = router.currentRoute.value
    if (current.name !== 'SignIn') {
      await router.push({
        name: 'SignIn',
        query: current.fullPath && current.fullPath !== '/'
          ? { redirect: current.fullPath }
          : undefined,
      })
    }
    toast.warning(message)
  } finally {
    loggingOut = false
  }
}

function normalizeError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiResponse<unknown>>
    const payload = axiosError.response?.data
    const status = axiosError.response?.status ?? 0
    if (!axiosError.response) {
      return new ApiError(
        axiosError.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '无法连接服务器，请检查网络',
        -1,
        0,
      )
    }
    return new ApiError(payload?.message || `请求失败（HTTP ${status}）`, payload?.code ?? -1, status)
  }
  if (error instanceof Error) return new ApiError(error.message)
  return new ApiError('操作失败，请稍后重试')
}

type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const AUTH_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/register/code']

function isAuthEndpoint(url?: string): boolean {
  return !!url && AUTH_ENDPOINTS.some(endpoint => url.includes(endpoint))
}

httpClient.interceptors.response.use(
  response => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(normalizeError(error))
    }
    const axiosError = error as AxiosError<ApiResponse<unknown>>
    const status = axiosError.response?.status
    const payload = axiosError.response?.data
    const config = axiosError.config as RetryableConfig | undefined
    const authEndpoint = isAuthEndpoint(config?.url)

    if (status === 401 && config && !authEndpoint) {
      const hasRefreshToken = Boolean(getRefreshToken())
      if (!config._retry && hasRefreshToken) {
        config._retry = true
        try {
          const token = await refreshOnce()
          config.headers.Authorization = `Bearer ${token}`
          return httpClient(config)
        } catch {
          await forceLogout()
          return Promise.reject(new ApiError('登录已过期，请重新登录', 40101, 401))
        }
      }
      await forceLogout()
    }

    if (status === 401 && authEndpoint && payload?.code === 40104) {
      // ACCOUNT_DISABLED keeps its own message; no redirect is needed.
      return Promise.reject(normalizeError(error))
    }

    return Promise.reject(normalizeError(error))
  },
)

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await httpClient.request<ApiResponse<T>>(config)
  if (response.status === 204 || (response.data as unknown) === '' || response.data == null) {
    return undefined as T
  }
  return response.data.data
}

export const http = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'GET', url })
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'POST', url, data })
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'PUT', url, data })
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'PATCH', url, data })
  },
  delete<T = void>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'DELETE', url })
  },
}
