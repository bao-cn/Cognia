<script setup lang="ts">
import { IconPhoto } from '@tabler/icons-vue'
import { DropdownMenuItem } from '@/components/ui/dropdown-menu'
import { usePromptInput } from './context'

type PromptInputActionAddAttachmentsProps = InstanceType<typeof DropdownMenuItem>['$props']

interface Props extends /* @vue-ignore */ PromptInputActionAddAttachmentsProps {
  label?: string
}

const props = defineProps<Props>()

const { openFileDialog } = usePromptInput()
</script>

<template>
  <DropdownMenuItem @select.prevent="openFileDialog">
    <IconPhoto class="mr-2 size-4" />
    {{ props.label || 'Add photos or files' }}
  </DropdownMenuItem>
</template>
