// GeeTest CAPTCHA v4 (gt4.js) globals. Loaded on demand from
// https://static.geetest.com/v4/gt4.js by GeeTestWidget.vue.

interface GeeTestValidateResult {
  lot_number: string
  captcha_output: string
  pass_token: string
  gen_time: string
  [field: string]: string
}

interface GeeTestCaptchaInstance {
  appendTo(position: string | HTMLElement): void
  onReady(callback: () => void): GeeTestCaptchaInstance
  onNextReady(callback: () => void): GeeTestCaptchaInstance
  onSuccess(callback: () => void): GeeTestCaptchaInstance
  onFail(callback: (failure: unknown) => void): GeeTestCaptchaInstance
  onError(callback: (error: { code?: string; msg?: string }) => void): GeeTestCaptchaInstance
  onClose(callback: () => void): GeeTestCaptchaInstance
  getValidate(): GeeTestValidateResult | false
  showCaptcha(): void
  reset(): void
  destroy(): void
}

interface GeeTestInitConfig {
  captchaId: string
  product?: 'float' | 'popup' | 'bind'
  nativeButton?: { width?: string; height?: string }
  language?: string
  protocol?: string
  timeout?: number
  hideBar?: Array<'close' | 'refresh'>
  mask?: { outside?: boolean; bgColor?: string }
  onError?: (error: unknown) => void
  userInfo?: string
}

interface Window {
  initGeetest4?: (config: GeeTestInitConfig, callback: (captcha: GeeTestCaptchaInstance) => void) => void
}
