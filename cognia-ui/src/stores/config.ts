import { defineStore } from 'pinia'
import i18n from '@/locales/index'

export enum THEMES {
  SYSTEM = 'system',
  LIGHT = 'light',
  DARK = 'dark',
}

export type LocaleType = 'zhCn' | 'enUs'

type ConfigInfo = {
  theme: THEMES
  language: LocaleType
}

const STORAGE_KEY = 'app_config'

export const useConfigStore = defineStore('config', {
  state: () => {
    const saved = localStorage.getItem(STORAGE_KEY)
    let defaultConfig: ConfigInfo = {
      theme: THEMES.SYSTEM,
      language: 'zhCn',
    }
    if (saved) {
      try {
        const parsed = JSON.parse(saved)
        if (parsed.language === 'zhCn' || parsed.language === 'enUs') {
          defaultConfig = parsed
        }
      } catch {
        defaultConfig = {
          theme: THEMES.SYSTEM,
          language: 'zhCn',
        }
      }
    }
    return {
      configInfo: defaultConfig
    }
  },
  getters: {
    actualTheme(state): 'light' | 'dark' {
      if (state.configInfo.theme === THEMES.SYSTEM) {
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
      }
      return state.configInfo.theme as 'light' | 'dark'
    },
    actualLanguage(state): LocaleType {
      return state.configInfo.language
    },
  },
  actions: {
    setConfigInfo(configInfo: ConfigInfo) {
      this.configInfo = configInfo
      this.saveToLocal()
      this.applyTheme()
    },
    setTheme(theme: THEMES) {
      this.configInfo.theme = theme
      this.saveToLocal()
      this.applyTheme()
    },
    setLanguage(language: LocaleType) {
      this.configInfo.language = language
      this.saveToLocal()
      this.applyLanguage()
    },
    saveToLocal() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.configInfo))
    },
    applyTheme() {
      const root = document.documentElement
      const theme = this.actualTheme
      root.classList.remove('light', 'dark')
      root.classList.add(theme)
    },

    applyLanguage() {
      const language = this.actualLanguage
      i18n.global.locale.value = language
      document.documentElement.lang = language
    },
  },
})
