<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ImmersiveView from './ImmersiveView.vue'
import MenuView from './MenuView.vue'
import OverlayView from './OverlayView.vue'
import PanelView from './PanelView.vue'
import { useSimulator } from '../injection'
import { DISPLAY_HEIGHT_PX, DISPLAY_WIDTH_PX, panelDistancePx, perspectivePx, pxPerDegree } from '../shell/geometry'

defineProps<{ showRoom: boolean }>()

const { shell, media } = useSimulator()
const state = shell.state
const area = ref<HTMLElement>()
const scale = ref(1)
const draggingHead = ref(false)

let observer: ResizeObserver | null = null

onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    const { width, height } = entry!.contentRect
    scale.value = Math.min(width / DISPLAY_WIDTH_PX, height / DISPLAY_HEIGHT_PX)
  })
  observer.observe(area.value!)
})
onBeforeUnmount(() => observer?.disconnect())

const camera = computed(
  () =>
    `perspective(${perspectivePx}px) translateZ(${perspectivePx}px) rotateX(${state.head.pitchDegrees}deg) rotateY(${state.head.yawDegrees}deg)`,
)

const busy = computed(() => draggingHead.value || state.grabbedId !== null)

function onBackgroundDown(event: PointerEvent) {
  if (event.button !== 0) return
  draggingHead.value = true
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

const display = ref<HTMLElement>()

function onPointer(event: PointerEvent) {
  const rect = display.value!.getBoundingClientRect()
  shell.setPointer(
    (event.clientX - rect.left - rect.width / 2) / scale.value,
    (event.clientY - rect.top - rect.height / 2) / scale.value,
  )
}

function onBackgroundMove(event: PointerEvent) {
  if (!draggingHead.value) return
  const degreesPerScreenPx = 1 / (pxPerDegree * scale.value)
  // On the glasses any drag on empty space turns a rotatable video; here a
  // plain drag is the head, so shift stands in for it.
  if (event.shiftKey && media.rotateBy(event.movementX * degreesPerScreenPx)) return
  shell.setHead(
    state.head.yawDegrees - event.movementX * degreesPerScreenPx,
    state.head.pitchDegrees + event.movementY * degreesPerScreenPx,
  )
}

function onBackgroundUp() {
  draggingHead.value = false
}

function onGrabMove(dx: number, dy: number) {
  const degreesPerScreenPx = 1 / (pxPerDegree * scale.value)
  shell.moveGrab(dx * degreesPerScreenPx, -dy * degreesPerScreenPx)
}
</script>

<template>
  <div
    ref="area"
    class="stage-area"
    @pointerdown.self="onBackgroundDown"
    @pointermove="onBackgroundMove"
    @pointerup="onBackgroundUp"
    @pointercancel="onBackgroundUp"
    @pointermove.capture="onPointer"
    @pointerover.capture="onPointer"
    @pointerleave="shell.clearPointer()"
  >
    <div
      ref="display"
      class="display"
      :class="{ room: showRoom, busy }"
      :style="{
        width: `${DISPLAY_WIDTH_PX}px`,
        height: `${DISPLAY_HEIGHT_PX}px`,
        transform: `translate(-50%, -50%) scale(${scale})`,
        filter: `brightness(${state.settings['display.brightness']})`,
      }"
      @pointerdown.self="onBackgroundDown"
    >
      <ImmersiveView />
      <div class="world">
        <PanelView
          v-for="window in state.windows"
          :key="window.id"
          :window="window"
          :camera="camera"
          :blend="showRoom"
          :screen-to-panel="panelDistancePx / perspectivePx / scale"
          @grab-move="onGrabMove"
        />
        <OverlayView v-for="overlay in state.overlays" :key="overlay.id" :overlay="overlay" :camera="camera" :blend="showRoom" />
        <MenuView :camera="camera" />
      </div>
      <div v-if="state.settings['input.pointer'] === 'gaze'" class="reticle" />
    </div>
  </div>
</template>

<style scoped>
.stage-area {
  position: absolute;
  inset: 0;
  overflow: hidden;
  cursor: grab;
  touch-action: none;
}

.stage-area:active {
  cursor: grabbing;
}

.display {
  position: absolute;
  left: 50%;
  top: 50%;
  overflow: hidden;
  background: #000;
  outline: 1px solid #1a2230;
  transform-origin: center;
}

.display.room {
  background:
    radial-gradient(ellipse at 30% 20%, rgba(255, 236, 200, 0.35), transparent 55%),
    linear-gradient(180deg, #6d6a64 0%, #8a8378 55%, #4d4338 56%, #6b5b4a 100%);
}

.display.busy :deep(iframe) {
  pointer-events: none;
}

.world {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 0;
  height: 0;
}

.reticle {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 14px;
  height: 14px;
  margin: -7px 0 0 -7px;
  border: 2px solid rgba(255, 255, 255, 0.85);
  border-radius: 50%;
  pointer-events: none;
}
</style>
