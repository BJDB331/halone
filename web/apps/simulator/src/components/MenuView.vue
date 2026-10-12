<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useSimulator } from '../injection'
import { panelDistancePx } from '../shell/geometry'
import { MENU } from '../shell/shell'

const props = defineProps<{ camera: string }>()

const { shell, bridge, appsOrigin } = useSimulator()
const frame = ref<HTMLIFrameElement>()
const src = ref<string>()
const app = shell.state.apps.find((candidate) => candidate.id === MENU)

/** The menu's parent: a window or an overlay, with its pose and size. */
const parent = computed(() => {
  const menu = shell.state.menu
  if (!menu) return null
  return shell.state.windows.find((w) => w.id === menu.sourceId) ?? shell.state.overlays.find((o) => o.id === menu.sourceId) ?? null
})

/** On the parent's plane, with its corner at the point, moved left or up where the parent has no room, as `PanelScene.showPopup` does. */
const transform = computed(() => {
  const menu = shell.state.menu
  const on = parent.value
  if (!menu || !on) return 'scale(0)'
  const fit = (at: number, size: number, room: number) => (at + size <= room ? at : at - size >= 0 ? at - size : Math.max(0, room - size))
  const left = fit(menu.xPx, menu.widthPx, on.widthPx)
  const top = fit(menu.yPx, menu.heightPx, on.heightPx)
  return `${props.camera} rotateY(${-on.yawDegrees}deg) rotateX(${-on.pitchDegrees}deg) translateZ(${-panelDistancePx}px) translate(${left - on.widthPx / 2}px, ${top - on.heightPx / 2}px)`
})

const shown = computed(() => !!shell.state.menu?.shown && !!parent.value)

/** A click anywhere else takes focus into another frame or lands on the stage; either dismisses the menu. */
function onBlur() {
  setTimeout(() => {
    if (shell.state.menu && document.activeElement !== frame.value) shell.dismissMenu()
  })
}

function onDown(event: PointerEvent) {
  if (shell.state.menu && !frame.value?.contains(event.target as Node)) shell.dismissMenu()
}

onMounted(() => {
  if (!app) return
  bridge.attach(MENU, frame.value!)
  src.value = new URL(app.entry, appsOrigin).href
  window.addEventListener('blur', onBlur)
  window.addEventListener('pointerdown', onDown, true)
})
onBeforeUnmount(() => {
  bridge.detach(MENU)
  window.removeEventListener('blur', onBlur)
  window.removeEventListener('pointerdown', onDown, true)
})
</script>

<template>
  <div class="menu" :class="{ hidden: !shown }" :style="{ transform }">
    <iframe
      ref="frame"
      :src="src"
      title="Menu"
      :width="shell.state.menu?.widthPx ?? app?.widthPx"
      :height="shell.state.menu?.heightPx ?? app?.heightPx"
    />
  </div>
</template>

<style scoped>
.menu {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 20;
  transform-origin: 0 0;
}

.menu.hidden {
  visibility: hidden;
  pointer-events: none;
}

iframe {
  display: block;
  border: 0;
  border-radius: 14px;
  background: transparent;
}
</style>
