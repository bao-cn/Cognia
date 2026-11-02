import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * Removes the element when the current user lacks the required permission.
 *
 * Usage: v-permission="'user:create'" or v-permission="['user:update', 'user:delete']".
 */
export const permissionDirective: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    const userStore = useUserStore()
    const allowed = required.some(permission => userStore.hasPermission(permission))
    if (!allowed) {
      el.parentNode?.removeChild(el)
    }
  },
}
