<script setup lang="ts">
import { computed } from 'vue'
import type { StereoRegion } from '@halone/sdk'

/**
 * Shows its content over a stereo region in both eyes. The host stretches each
 * eye's half of the region over all of it, so the content is drawn twice,
 * squeezed into each half, and comes out where it was laid out, in the
 * region's own coordinates. It takes no pointer input: what the wearer sees at
 * a point is not what lies under it in the page.
 */
const props = defineProps<{ region: StereoRegion }>()

const halves = computed(() => {
  const { xPx, yPx, widthPx, heightPx, stereo } = props.region
  const sideBySide = stereo === 'side-by-side'
  const half = sideBySide ? { width: widthPx / 2, height: heightPx } : { width: widthPx, height: heightPx / 2 }
  const squeeze = sideBySide ? 'scale(0.5, 1)' : 'scale(1, 0.5)'
  return [0, 1].map((eye) => ({
    box: {
      left: `${xPx + (sideBySide ? eye * half.width : 0)}px`,
      top: `${yPx + (sideBySide ? 0 : eye * half.height)}px`,
      width: `${half.width}px`,
      height: `${half.height}px`,
    },
    content: { width: `${widthPx}px`, height: `${heightPx}px`, transform: squeeze },
  }))
})
</script>

<template>
  <div v-for="(half, eye) in halves" :key="eye" class="half" :style="half.box">
    <div class="content" :style="half.content"><slot /></div>
  </div>
</template>

<style scoped>
.half {
  position: fixed;
  overflow: hidden;
  pointer-events: none;
}

.content {
  position: absolute;
  left: 0;
  top: 0;
  transform-origin: 0 0;
}
</style>
