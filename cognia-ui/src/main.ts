import { createApp } from 'vue'
import App from './App.vue'
import router from './router/index.ts'
import { createPinia } from 'pinia'
import i18n from './locales/index.ts'
import { setupDomTranslator } from './locales/dom-translator.ts'
import { permissionDirective } from './directives/permission.ts'
import { getDefaultBrandIcon, setFavicon } from './lib/branding.ts'

import './assets/index.css'

setFavicon(getDefaultBrandIcon())

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(i18n)
setupDomTranslator(i18n)
app.directive('permission', permissionDirective)

app.mount('#app')
