<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useSimulator } from '../injection'
import { panelDistancePx } from '../shell/geometry'
import type { SimOverlay } from '../shell/shell'

const props = defineProps<{ overlay: SimOverlay; camera: string; blend: boolean }>()

const { bridge } = useSimulator()
const frame = ref<HTMLIFrameElement>()
const src = ref<string>()

const transform = computed(
  () =>
    `${props.camera} rotateY(${-props.overlay.yawDegrees}deg) rotateX(${-props.overlay.pitchDegrees}deg) translateZ(${-panelDistancePx}px) translate(-50%, -50%)`,
)
const radius = computed(() => (props.overlay.id === 'dock' ? props.overlay.heightPx / 2 : 24))

onMounted(() => {
  bridge.attach(props.overlay.id, frame.value!)
  src.value = props.overlay.url
})
onBeforeUnmount(() => bridge.detach(props.overlay.id))
</script>

<template>
  <div
    class="overlay"
    :class="{ blend, hidden: !overlay.shown }"
    :style="{ transform, opacity: overlay.opacity }"
    :data-overlay-id="overlay.id"
  >
    <iframe
      ref="frame"
      :src="src"
      :title="overlay.id"
      :width="overlay.widthPx"
      :height="overlay.heightPx"
      :style="{ borderRadius: `${radius}px` }"
    />
  </div>
</template>

<style scoped>
.overlay {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 10;
  transform-origin: 0 0;
}

.overlay.blend {
  mix-blend-mode: screen;
}

.overlay.hidden {
  pointer-events: none;
}

iframe {
  display: block;
  border: 0;
  background: #000;
}
</style>
