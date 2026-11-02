<script setup lang="ts">
import type { FlowEmits, FlowProps, FlowSlots } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { VueFlow } from '@vue-flow/core'
import { useForwardPropsEmits } from 'reka-ui'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const props = withDefaults(defineProps<FlowProps>(), {
  deleteKeyCode: () => ['Backspace', 'Delete'],
  fitViewOnInit: true,
  panOnDrag: false,
  panOnScroll: true,
  selectNodesOnDrag: true,
  zoomOnDoubleClick: false,
})

const emits = defineEmits<FlowEmits>()
const slots = defineSlots<FlowSlots>()

// Instantiating `useForwardPropsEmits` with the large `FlowProps`/`FlowEmits` types
// exceeds TypeScript's instantiation depth, so the helper is called through a
// looser local signature (same runtime behaviour).
const forwardPropsEmits = useForwardPropsEmits as unknown as (
  forwardProps: Record<string, unknown>,
  forwardEmits: (...args: any[]) => void,
) => Record<string, any>

const forwarded = forwardPropsEmits(props, emits)
</script>

<template>
  <VueFlow data-slot="canvas" v-bind="forwarded">
    <Background />

    <template v-if="slots['connection-line']" #connection-line="connectionLineProps">
      <slot name="connection-line" v-bind="connectionLineProps" />
    </template>

    <template v-if="slots['zoom-pane']" #zoom-pane>
      <slot name="zoom-pane" />
    </template>

    <slot />
  </VueFlow>
</template>
