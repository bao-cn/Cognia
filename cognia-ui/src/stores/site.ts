import { defineStore } from 'pinia'
import { settingsApi } from '@/api'
import type { LanguageCode, PublicSiteSettings } from '@/types/api'
import config from '@/config'
import { useConfigStore } from '@/stores/config'

const DEFAULT_SETTINGS: PublicSiteSettings = {
  siteName: 'Cognia',
  logoUrl: '/logo.png',
  siteDescription: 'Cognia 开源的企业知识库平台',
  keywords: ['Cognia', '企业知识库', 'AI 搜索', '知识管理'],
  routeSuffix: config.TITLE_SUFFIX,
  defaultLanguage: 'zhCn',
  timezone: 'Asia/Shanghai',
  allowedUploadTypes: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'html', 'csv'],
  registrationEnabled: true,
  registrationEmailVerification: false,
  registrationEmailWhitelistEnabled: false,
  registerEmailCaptchaEnabled: true,
  loginCaptchaEnabled: false,
  appleLoginEnabled: false,
  googleLoginEnabled: false,
  turnstileConfigured: false,
  turnstileSiteKey: '',
  teamCreationLimit: 5,
  teamJoinLimit: 10,
}

let loadPromise: Promise<void> | null = null

export const useSiteStore = defineStore('site', {
  state: () => ({
    config: { ...DEFAULT_SETTINGS, keywords: [...DEFAULT_SETTINGS.keywords], allowedUploadTypes: [...DEFAULT_SETTINGS.allowedUploadTypes] } as PublicSiteSettings,
    loaded: false,
    loading: false,
  }),

  getters: {
    siteName: state => state.config.siteName || 'Cognia',
    logo: state => state.config.logoUrl || '/logo.png',
    description: state => state.config.siteDescription || '',
    keywords: state => state.config.keywords ?? [],
    routeSuffix: state => state.config.routeSuffix ?? config.TITLE_SUFFIX,
    defaultLanguage: state => state.config.defaultLanguage,
    timezone: state => state.config.timezone,
    registrationEnabled: state => Boolean(state.config.registrationEnabled),
    registrationEmailVerification: state => Boolean(state.config.registrationEmailVerification),
    registrationEmailWhitelistEnabled: state => Boolean(state.config.registrationEmailWhitelistEnabled),
    emailVerificationRequired: state => Boolean(state.config.registrationEmailVerification),
    registerEmailCaptchaEnabled: state => Boolean(state.config.registerEmailCaptchaEnabled),
    loginCaptchaEnabled: state => Boolean(state.config.loginCaptchaEnabled),
    appleLoginEnabled: state => Boolean(state.config.appleLoginEnabled),
    googleLoginEnabled: state => Boolean(state.config.googleLoginEnabled),
    turnstileConfigured: state => Boolean(state.config.turnstileConfigured),
    turnstileSiteKey: state => state.config.turnstileSiteKey ?? '',
    allowedUploadTypes: state => state.config.allowedUploadTypes ?? [],
  },

  actions: {
    async load(force = false): Promise<void> {
      if (this.loading && loadPromise) {
        return loadPromise
      }
      if (this.loaded && !force) {
        return
      }
      this.loading = true
      loadPromise = settingsApi.public()
        .then((data) => {
          if (data) {
            this.config = data
          }
          this.loaded = true
          this.applyBranding()
          this.applySiteDefaultLanguage()
        })
        .catch((error) => {
          console.warn('Failed to load public site settings, using defaults', error)
          this.loaded = true
        })
        .finally(() => {
          this.loading = false
          loadPromise = null
        })
      return loadPromise
    },

    applyBranding(): void {
      this.setMeta('description', this.description)
      this.setMeta('keywords', this.keywords.join(','))
      const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
      if (icon) {
        icon.href = this.logo
      }
    },

    applySiteDefaultLanguage(): void {
      // A visitor's explicit choice wins; the site default only seeds first use.
      if (localStorage.getItem('app_config')) {
        return
      }
      const configStore = useConfigStore()
      configStore.setLanguage(this.defaultLanguage as LanguageCode)
    },

    setMeta(name: string, content: string): void {
      if (!content) return
      let element = document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)
      if (!element) {
        element = document.createElement('meta')
        element.name = name
        document.head.appendChild(element)
      }
      element.content = content
    },
  },
})
