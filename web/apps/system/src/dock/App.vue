<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { Battery, StatusItem } from '@halone/sdk'
import AppIcon from '../shared/AppIcon.vue'
import BatteryIcon from '../shared/BatteryIcon.vue'
import { useApps, useHost, useRecording, useSettings, useWindows } from '../shared/host'

const { halone, status } = useHost()
const windows = useWindows(halone)
const settings = useSettings(halone)
const apps = useApps(halone, true)
const battery = ref<Battery | null>(null)
const statusItems = ref<StatusItem[]>([])
const expanded = ref(false)
const windowList = ref<HTMLElement | null>(null)
const fadeStart = ref(false)
const fadeEnd = ref(false)
const launcherOpen = ref(false)
const now = ref(new Date())

halone.system.battery().then((value) => (battery.value = value), () => {})
halone.status.list().then((items) => (statusItems.value = items), () => {})
halone.launcher.get().then(({ open }) => (launcherOpen.value = open), () => {})
const offs = [
  halone.on('system.batteryChanged', (value) => (battery.value = value)),
  halone.on('status.changed', (items) => (statusItems.value = items)),
  halone.on('launcher.changed', ({ open }) => (launcherOpen.value = open)),
]
const clock = setInterval(() => (now.value = new Date()), 1000)
onBeforeUnmount(() => {
  clearInterval(clock)
  offs.forEach((off) => off())
})

const recording = useRecording(halone)
const recorded = computed(() => {
  const started = recording.value.startedAt
  if (!recording.value.recording || started === null) return ''
  const seconds = Math.max(0, Math.floor((now.value.getTime() - started) / 1000))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
})
const time = computed(() => now.value.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
const appFor = (appId: string | null) => apps.value.find((app) => app.id === appId)
const pinnedIds = computed(() => settings.value?.['dock.pinnedApps'] ?? [])
const pinned = computed(() =>
  pinnedIds.value.flatMap((id) => {
    const app = apps.value.find((a) => a.id === id && !a.hidden)
    return app ? [{ app, window: windows.value.find((w) => w.appId === id) }] : []
  }),
)
const others = computed(() => windows.value.filter((w) => !w.appId || !pinnedIds.value.includes(w.appId)))

function openPinned(appId: string, windowId?: string) {
  ;(windowId ? halone.windows.focus(windowId) : halone.apps.launch(appId)).catch(() => {})
}
/** Two items fit beside the battery; with more, the rest wait behind a button that gives the status the whole dock. */
const COMPACT_ITEMS = 2
const compactItems = computed(() => (statusItems.value.length > COMPACT_ITEMS ? statusItems.value.slice(0, COMPACT_ITEMS) : statusItems.value))
const hiddenItems = computed(() => statusItems.value.length - compactItems.value.length)

watch(hiddenItems, (count) => {
  if (count === 0) expanded.value = false
})

function clickItem(item: StatusItem) {
  halone.status.click(item.id).catch(() => {})
}

/** Shows which ends of the window list have more beyond them. */
function updateFades() {
  const list = windowList.value
  if (!list) return
  fadeStart.value = list.scrollLeft > 1
  fadeEnd.value = list.scrollLeft + list.clientWidth < list.scrollWidth - 1
}

/** A mouse wheel turns vertically; the list scrolls sideways. */
function onWheel(event: WheelEvent) {
  const list = windowList.value
  if (!list || list.scrollWidth <= list.clientWidth) return
  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  list.scrollLeft += delta
  event.preventDefault()
}

const resizeObserver = new ResizeObserver(updateFades)
watch(windowList, (list, old) => {
  if (old) resizeObserver.unobserve(old)
  if (list) resizeObserver.observe(list)
})
watch([pinned, others], () => nextTick(updateFades), { deep: true })
onBeforeUnmount(() => resizeObserver.disconnect())

const GRID = [0, 1, 2].flatMap((row) => [0, 1, 2].map((column) => [8 + column * 10, 8 + row * 10]))
</script>

<template>
  <main v-if="status === 'ready'" class="dock">
    <button
      class="launcher"
      :class="{ active: launcherOpen }"
      :aria-label="launcherOpen ? 'Close the launcher' : 'Open the launcher'"
      @click="halone.launcher.setOpen(!launcherOpen)"
    >
      <svg viewBox="0 0 36 36" width="36" height="36" aria-hidden="true">
        <circle v-for="(dot, i) in GRID" :key="i" :cx="dot[0]" :cy="dot[1]" r="3" />
      </svg>
    </button>

    <div class="divider" :class="{ collapsed: expanded }" />

    <div
      ref="windowList"
      class="windows"
      :class="{ collapsed: expanded, 'fade-start': fadeStart, 'fade-end': fadeEnd }"
      :aria-hidden="expanded"
      @scroll="updateFades"
      @wheel="onWheel"
    >
      <button
        v-for="{ app, window } in pinned"
        :key="app.id"
        class="window"
        :class="{ running: window, focused: window?.focused }"
        :aria-label="app.name"
        @click="openPinned(app.id, window?.id)"
      >
        <AppIcon class="icon" :app="app" />
        <span v-if="window?.pinned" class="pin" />
      </button>
      <div v-if="pinned.length && others.length" class="divider small" />
      <button
        v-for="window in others"
        :key="window.id"
        class="window"
        :class="{ running: true, focused: window.focused }"
        :aria-label="window.title"
        @click="halone.windows.focus(window.id)"
      >
        <AppIcon class="icon" :app="appFor(window.appId)" />
        <span v-if="window.pinned" class="pin" />
      </button>
      <span v-if="windows.length === 0 && pinned.length === 0" class="empty x-muted">No windows open</span>
    </div>

    <div class="status" :class="{ expanded }">
      <button v-if="recording.recording" class="recording" aria-label="Stop recording" @click="halone.screen.stopRecording()">
        <span class="dot" />
        {{ recorded }}
      </button>
      <Transition name="swap" mode="out-in">
        <div v-if="expanded" key="wide" class="wide">
          <button class="toggle" aria-label="Show apps again" title="Show apps again" @click="expanded = false">
            <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M9 6 L15 12 L9 18" /></svg>
          </button>
          <button
            v-for="item in statusItems"
            :key="item.id"
            class="status-item large"
            :title="item.label"
            :aria-label="item.label || item.text"
            @click="clickItem(item)"
          >
            <span v-if="item.tinted" class="glyph" :style="{ '--icon': `url(${item.iconUrl})` }" />
            <img v-else class="glyph" :src="item.iconUrl" alt="" />
            <span v-if="item.text">{{ item.text }}</span>
          </button>
          <span class="time">{{ time }}</span>
          <BatteryIcon v-if="battery" class="battery" :level="battery.level" :charging="battery.charging" :height="20" />
        </div>
        <div v-else key="compact" class="compact">
          <span class="time">{{ time }}</span>
          <div class="second-row">
            <button
              v-for="item in compactItems"
              :key="item.id"
              class="status-item"
              :title="item.label"
              :aria-label="item.label || item.text"
              @click="clickItem(item)"
            >
              <span v-if="item.tinted" class="glyph" :style="{ '--icon': `url(${item.iconUrl})` }" />
              <img v-else class="glyph" :src="item.iconUrl" alt="" />
              <span v-if="item.text">{{ item.text }}</span>
            </button>
            <button
              v-if="hiddenItems > 0"
              class="status-item more"
              :aria-label="`Show ${hiddenItems} more`"
              :title="`Show ${hiddenItems} more`"
              @click="expanded = true"
            >
              +{{ hiddenItems }}
            </button>
            <BatteryIcon v-if="battery" class="battery" :level="battery.level" :charging="battery.charging" :height="17" />
          </div>
        </div>
      </Transition>
    </div>
  </main>
</template>

<style scoped>
.recording {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid var(--danger);
  border-radius: 22px;
  background: none;
  color: var(--danger);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.recording .dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--danger);
}

.dock {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 100vh;
  padding: 0 24px 0 16px;
  border-radius: 52px;
  background: rgba(22, 28, 38, 0.94);
  overflow: hidden;
}

button {
  flex: none;
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.launcher {
  border-radius: 50%;
  fill: var(--text-muted);
}

.launcher:hover {
  background: var(--surface-hover);
  fill: var(--text);
}

.launcher.active {
  background: var(--accent);
  fill: #04121f;
}

.divider {
  flex: none;
  width: 2px;
  height: 48px;
  border-radius: 1px;
  background: var(--border);
}

.windows {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  transition:
    max-width 0.3s ease,
    opacity 0.2s ease;
}

.windows::-webkit-scrollbar {
  display: none;
}

.windows.fade-start {
  mask-image: linear-gradient(to right, transparent, black 32px);
}

.windows.fade-end {
  mask-image: linear-gradient(to left, transparent, black 32px);
}

.windows.fade-start.fade-end {
  mask-image: linear-gradient(to right, transparent, black 32px, black calc(100% - 32px), transparent);
}

.windows.collapsed {
  max-width: 0;
  opacity: 0;
  pointer-events: none;
}

.window {
  position: relative;
  border-radius: 20px;
}

.window:hover {
  background: var(--surface-hover);
}

.window .icon {
  font-size: 38px;
  line-height: 1;
}

.window.running::after,
.window.focused::after {
  content: '';
  position: absolute;
  bottom: 2px;
  left: 50%;
  width: 6px;
  height: 4px;
  margin-left: -3px;
  border-radius: 2px;
  background: var(--text-muted);
}

.window.focused::after {
  width: 18px;
  margin-left: -9px;
  background: var(--text);
}

.divider {
  transition: opacity 0.2s ease;
}

.divider.collapsed {
  opacity: 0;
}

.divider.small {
  height: 36px;
  margin: 0 4px;
}

.window .pin {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgb(64, 204, 255);
}

.empty {
  padding-left: 8px;
  font-size: 20px;
}

.status {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 16px;
  font-variant-numeric: tabular-nums;
  transition: flex-grow 0.3s ease;
}

.status.expanded {
  flex-grow: 1;
  justify-content: flex-end;
}

.compact {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.compact .time {
  font-size: 26px;
  line-height: 32px;
}

.second-row {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 28px;
  font-size: 17px;
}

.wide {
  display: flex;
  align-items: center;
  gap: 18px;
  font-size: 22px;
}

.wide .time {
  font-size: 28px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 4px;
  width: auto;
  height: 28px;
  padding: 0 4px;
  border-radius: 8px;
  color: var(--text-muted);
  font: inherit;
}

.status-item:hover {
  background: var(--surface-hover);
}

.status-item .glyph {
  display: block;
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.status-item.large {
  height: 48px;
  gap: 6px;
  padding: 0 8px;
  border-radius: 12px;
}

.status-item.large .glyph {
  width: 28px;
  height: 28px;
}

.status-item.more {
  padding: 0 8px;
  border: 1px solid var(--border);
  border-radius: 14px;
  font-size: 15px;
}

span.glyph {
  background: currentColor;
  mask-image: var(--icon);
  -webkit-mask-image: var(--icon);
  mask-size: contain;
  mask-repeat: no-repeat;
  mask-position: center;
  -webkit-mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  -webkit-mask-position: center;
}

.toggle {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  fill: none;
  stroke: var(--text-muted);
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.toggle:hover {
  background: var(--surface-hover);
  stroke: var(--text);
}

.swap-enter-active,
.swap-leave-active {
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

.swap-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.swap-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.battery {
  color: var(--text-muted);
}
</style>
