<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Stage from './components/Stage.vue'
import Sidebar from './components/Sidebar.vue'
import { useSimulator } from './injection'
import { readPreference, writePreference } from './preferences'

const { shell, bootError } = useSimulator()
const sidebarOpen = ref(readPreference('sidebar') !== 'closed')
const showRoom = ref(readPreference('room') === 'on')

watch(sidebarOpen, (open) => writePreference('sidebar', open ? 'open' : 'closed'))
watch(showRoom, (on) => writePreference('room', on ? 'on' : 'off'))

const KEY_STEP_DEGREES = 2
const KEY_STEP_FAST_DEGREES = 10

function onKey(event: KeyboardEvent) {
  if (event.target instanceof HTMLInputElement || event.target instanceof HTMLSelectElement) return
  const step = event.shiftKey ? KEY_STEP_FAST_DEGREES : KEY_STEP_DEGREES
  const head = shell.state.head
  const actions: Record<string, () => void> = {
    ArrowLeft: () => shell.setHead(head.yawDegrees - step, head.pitchDegrees),
    ArrowRight: () => shell.setHead(head.yawDegrees + step, head.pitchDegrees),
    ArrowUp: () => shell.setHead(head.yawDegrees, head.pitchDegrees + step),
    ArrowDown: () => shell.setHead(head.yawDegrees, head.pitchDegrees - step),
    r: () => shell.recenter(),
    l: () => shell.setLauncherOpen(!shell.state.launcherOpen),
    '`': () => (sidebarOpen.value = !sidebarOpen.value),
  }
  const action = actions[event.key]
  if (!action) return
  event.preventDefault()
  action()
}

onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="sim-root">
    <div class="sim-stage-area">
      <Stage :show-room="showRoom" />
      <div v-if="bootError" class="boot-error">{{ bootError }}</div>
    </div>
    <aside v-if="sidebarOpen" class="sim-sidebar">
      <Sidebar v-model:show-room="showRoom" />
    </aside>
  </div>
</template>

<style scoped>
.boot-error {
  position: absolute;
  top: 16px;
  left: 16px;
  right: 16px;
  padding: 12px 16px;
  border: 1px solid var(--danger);
  border-radius: 8px;
  background: #2a0f12;
  color: var(--danger);
}
</style>
