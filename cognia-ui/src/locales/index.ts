
import { createI18n } from 'vue-i18n'

import zhCn from './zh-cn.ts'
import enUs from './en-us.ts'

const i18n = createI18n({
    locale: 'zhCn',
    legacy: false,
    messages: {
        zhCn: zhCn,
        enUs: enUs,
    },
})

export default i18n