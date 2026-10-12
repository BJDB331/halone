<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  baseName,
  eyeAspect,
  fitBox,
  isImage,
  layoutFromName,
  parentPath,
  splitMpo,
  type FileEntry,
  type StereoLayout,
  type StereoRegion,
} from '@halone/sdk'
import NoHost from '../shared/NoHost.vue'
import StereoOverlay from '../shared/StereoOverlay.vue'
import { byName, formatBytes, formatDate } from '../shared/format'
import { errorMessage, useHost, useOpenedFile } from '../shared/host'
import Icon from '../shared/Icon.vue'

const MIN_ZOOM = 0.1
const MAX_ZOOM = 8
const ZOOM_STEP = 1.25

const STEREO: { value: StereoLayout; label: string }[] = [
  { value: 'mono', label: '2D' },
  { value: 'side-by-side', label: 'Side by side' },
  { value: 'top-bottom', label: 'Top and bottom' },
]

const { halone, status, error } = useHost()
const opened = useOpenedFile(halone)
const siblings = ref<FileEntry[]>([])
const current = ref<FileEntry | null>(null)
const failure = ref<string | null>(null)
const loaded = ref(false)
const showInfo = ref(false)
const stereo = ref<StereoLayout>('mono')
const image = ref<HTMLElement>()
const region = ref<StereoRegion | null>(null)

/**
 * A format that is stereo by nature (MPO, JPS) only needs 2D or 3D. Each eye
 * is shown as it is, side by side for 3D and the left alone for 2D, rather
 * than drawn into one new picture: encoding that took seconds on the phone.
 */
interface EyePicture {
  url: string
  /** Which part of [url] is this eye: all of it, or one half of a side-by-side frame. */
  part: 'whole' | 'left' | 'right'
}

interface NativePair {
  left: EyePicture
  right: EyePicture
  eyeWidth: number
  eyeHeight: number
  /** Object URLs made for it, released with it. */
  owned: string[]
}

const pair = ref<NativePair | null>(null)
const shownEyes = computed(() => (pair.value ? (stereo.value === 'mono' ? [pair.value.left] : [pair.value.left, pair.value.right]) : []))

function eyeStyle(eye: EyePicture) {
  return {
    backgroundImage: `url("${eye.url}")`,
    backgroundSize: eye.part === 'whole' ? '100% 100%' : '200% 100%',
    backgroundPosition: eye.part === 'right' ? '100% 0' : '0 0',
  }
}

function releasePair() {
  pair.value?.owned.forEach((url) => URL.revokeObjectURL(url))
  pair.value = null
}
onBeforeUnmount(releasePair)

watch([pair, stereo], () => {
  const native = pair.value
  if (!native) return
  natural.width = stereo.value === 'mono' ? native.eyeWidth : native.eyeWidth * 2
  natural.height = native.eyeHeight
  loaded.value = true
})

const natural = reactive({ width: 0, height: 0 })
const stage = ref<HTMLElement>()
const box = reactive({ width: 0, height: 0 })
const observer = new ResizeObserver(([entry]) => Object.assign(box, { width: entry!.contentRect.width, height: entry!.contentRect.height }))
watch(stage, (element) => element && observer.observe(element))
onBeforeUnmount(() => observer.disconnect())
/** Zoom relative to fitting the stage; 1 is fitted. */
const view = reactive({ zoom: 1, x: 0, y: 0 })

watch(opened, (path) => path && void show(path))

async function show(path: string) {
  failure.value = null
  try {
    const entry = await halone.files.stat(path)
    const native = await nativePair(entry)
    if (opened.value !== path) {
      native?.owned.forEach((url) => URL.revokeObjectURL(url))
      return
    }
    releasePair()
    current.value = entry
    resetView()
    stereo.value = native ? 'side-by-side' : (layoutFromName(entry.name)?.stereo ?? 'mono')
    pair.value = native
    void halone.windows.setTitle(entry.name)
    const parent = parentPath(path)
    if (parent && siblings.value[0] && parentPath(siblings.value[0].path) === parent) return
    siblings.value = parent ? (await halone.files.list(parent)).filter(isImage).sort(byName) : [entry]
  } catch (caught) {
    failure.value = errorMessage(caught)
  }
}

async function nativePair(entry: FileEntry): Promise<NativePair | null> {
  if (!entry.url) return null
  if (entry.mimeType === 'image/mpo') {
    const pictures = splitMpo(new Uint8Array(await (await fetch(entry.url)).arrayBuffer()))
    if (!pictures) return null
    const [left, right] = pictures.map((bytes) => URL.createObjectURL(new Blob([bytes], { type: 'image/jpeg' })))
    const size = await createImageBitmap(new Blob([pictures[0]], { type: 'image/jpeg' }))
    return {
      left: { url: left!, part: 'whole' },
      right: { url: right!, part: 'whole' },
      eyeWidth: size.width,
      eyeHeight: size.height,
      owned: [left!, right!],
    }
  }
  if (/\.jps$/i.test(entry.name)) {
    // JPS keeps the cross-eyed order: the right eye's picture is on the left.
    const size = await createImageBitmap(await (await fetch(entry.url)).blob())
    return {
      left: { url: entry.url, part: 'right' },
      right: { url: entry.url, part: 'left' },
      eyeWidth: Math.floor(size.width / 2),
      eyeHeight: size.height,
      owned: [],
    }
  }
  return null
}

const index = computed(() => siblings.value.findIndex((entry) => entry.path === current.value?.path))

function step(delta: number) {
  const count = siblings.value.length
  if (count < 2 || index.value < 0) return
  const next = siblings.value[(index.value + delta + count) % count]!
  opened.value = next.path
}

function resetView() {
  loaded.value = false
  Object.assign(view, { zoom: 1, x: 0, y: 0 })
}

function onLoad(event: Event) {
  const image = event.target as HTMLImageElement
  natural.width = image.naturalWidth
  natural.height = image.naturalHeight
  loaded.value = true
}

/** A stereo picture is shown whole, fitted at one eye's shape; zooming would show each eye a different crop. */
const stereoBox = computed(() =>
  stereo.value !== 'mono' && box.width && natural.width
    ? fitBox(box.width, box.height, eyeAspect(natural.width, natural.height, stereo.value))
    : null,
)

watch(
  () => [stereoBox.value, loaded.value, current.value?.path] as const,
  async ([fitted, ready]) => {
    await nextTick()
    const rect = image.value?.getBoundingClientRect()
    const layout = stereo.value
    region.value =
      fitted && ready && rect && rect.width > 0 && rect.height > 0 && layout !== 'mono'
        ? { stereo: layout, xPx: rect.left, yPx: rect.top, widthPx: rect.width, heightPx: rect.height }
        : null
    void halone.windows.setStereoRegion(region.value).catch((caught) => (failure.value = errorMessage(caught)))
  },
)

const fitScale = computed(() => {
  if (!box.width || !natural.width) return 1
  return Math.min(box.width / natural.width, box.height / natural.height, 1)
})

const scale = computed(() => fitScale.value * view.zoom)
const percent = computed(() => `${Math.round(scale.value * 100)}%`)

function zoomBy(factor: number, originX = 0, originY = 0) {
  if (stereo.value !== 'mono') return
  const zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, view.zoom * factor))
  const applied = zoom / view.zoom
  view.x = originX - (originX - view.x) * applied
  view.y = originY - (originY - view.y) * applied
  view.zoom = zoom
  if (zoom <= 1) Object.assign(view, { x: 0, y: 0 })
}

function actualSize() {
  if (stereo.value !== 'mono') return
  view.zoom = 1 / fitScale.value
  Object.assign(view, { x: 0, y: 0 })
}

function onWheel(event: WheelEvent) {
  const box = stage.value!.getBoundingClientRect()
  zoomBy(event.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP, event.clientX - box.left - box.width / 2, event.clientY - box.top - box.height / 2)
}

let drag: { x: number; y: number; moved: number } | null = null

function onDown(event: PointerEvent) {
  drag = { x: event.clientX, y: event.clientY, moved: 0 }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onMove(event: PointerEvent) {
  if (!drag) return
  const dx = event.clientX - drag.x
  const dy = event.clientY - drag.y
  drag = { x: event.clientX, y: event.clientY, moved: drag.moved + Math.hypot(dx, dy) }
  if (view.zoom > 1) {
    view.x += dx
    view.y += dy
  }
}

function onUp() {
  drag = null
}

function onKey(event: KeyboardEvent) {
  const actions: Record<string, () => void> = {
    ArrowLeft: () => step(-1),
    ArrowRight: () => step(1),
    '+': () => zoomBy(ZOOM_STEP),
    '=': () => zoomBy(ZOOM_STEP),
    '-': () => zoomBy(1 / ZOOM_STEP),
    '0': () => resetView(),
    i: () => (showInfo.value = !showInfo.value),
  }
  actions[event.key]?.()
}
window.addEventListener('keydown', onKey)
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

const imageStyle = computed(() => {
  const fitted = stereoBox.value
  if (fitted) return { width: `${fitted.width}px`, height: `${fitted.height}px` }
  return {
    width: `${natural.width * scale.value}px`,
    height: `${natural.height * scale.value}px`,
    transform: `translate(${view.x}px, ${view.y}px)`,
  }
})

function chooseStereo(next: StereoLayout) {
  stereo.value = next
  Object.assign(view, { zoom: 1, x: 0, y: 0 })
}
</script>

<template>
  <NoHost :status="status" :error="error" />
  <main v-if="status === 'ready'" class="viewer">
    <div
      ref="stage"
      class="stage"
      :class="{ grab: view.zoom > 1 }"
      @wheel.prevent="onWheel"
      @pointerdown="onDown"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointercancel="onUp"
    >
      <div v-if="current && pair" ref="image" class="eyes" :style="imageStyle">
        <span v-for="(eye, side) in shownEyes" :key="side" class="eye" :style="eyeStyle(eye)" />
      </div>
      <img
        v-else-if="current?.url"
        ref="image"
        :key="current.path"
        :src="current.url"
        :alt="current.name"
        :style="loaded ? imageStyle : { opacity: 0 }"
        draggable="false"
        @load="onLoad"
        @error="failure = `${current.name} could not be shown`"
      />
      <div v-if="failure" class="empty">
        <strong>{{ failure }}</strong>
      </div>
      <div v-else-if="!current" class="empty">
        <strong>No image open</strong>
        <span class="x-muted">Open a picture from Files to see it here.</span>
        <button class="x-button primary" @click="halone.apps.launch('files')">Open Files</button>
      </div>
      <template v-if="siblings.length > 1 && stereo === 'mono'">
        <button class="nav prev" aria-label="Previous image" @pointerdown.stop @click="step(-1)"><Icon name="chevron_left" /></button>
        <button class="nav next" aria-label="Next image" @pointerdown.stop @click="step(1)"><Icon name="chevron_right" /></button>
      </template>
    </div>

    <footer v-if="current" class="bar">
      <div class="name">
        <strong>{{ current.name }}</strong>
        <span class="x-muted">
          <template v-if="siblings.length > 1">{{ index + 1 }} of {{ siblings.length }} · </template>
          <template v-if="natural.width && pair && stereo !== 'mono'">{{ natural.width / 2 }}×{{ natural.height }} per eye</template>
          <template v-else-if="natural.width">{{ natural.width }}×{{ natural.height }}</template>
        </span>
      </div>
      <div class="tools">
        <div v-if="pair" class="segmented">
          <button :class="{ on: stereo === 'mono' }" @click="chooseStereo('mono')">2D</button>
          <button :class="{ on: stereo !== 'mono' }" @click="chooseStereo('side-by-side')">3D</button>
        </div>
        <div v-else class="segmented">
          <button v-for="option in STEREO" :key="option.value" :class="{ on: stereo === option.value }" @click="chooseStereo(option.value)">
            {{ option.label }}
          </button>
        </div>
        <template v-if="stereo === 'mono'">
          <button class="x-button small" aria-label="Zoom out" @click="zoomBy(1 / ZOOM_STEP)"><Icon name="zoom_out" /></button>
          <span class="zoom">{{ percent }}</span>
          <button class="x-button small" aria-label="Zoom in" @click="zoomBy(ZOOM_STEP)"><Icon name="zoom_in" /></button>
          <button class="x-button small" @click="resetView">Fit</button>
          <button class="x-button small" @click="actualSize">1:1</button>
        </template>
        <template v-else-if="siblings.length > 1">
          <button class="x-button small" aria-label="Previous image" @click="step(-1)"><Icon name="chevron_left" /></button>
          <button class="x-button small" aria-label="Next image" @click="step(1)"><Icon name="chevron_right" /></button>
        </template>
        <button class="x-button small" :class="{ primary: showInfo }" @click="showInfo = !showInfo">Info</button>
      </div>
    </footer>

    <component :is="region ? StereoOverlay : 'div'" v-if="showInfo && current" v-bind="region ? { region } : {}">
      <aside class="info x-card">
        <h2 class="x-section-title">Details</h2>
        <dl>
          <dt>Folder</dt>
          <dd>{{ baseName(parentPath(current.path) ?? '/') }}</dd>
          <dt>Size</dt>
          <dd>{{ formatBytes(current.sizeBytes) }}</dd>
          <dt>Type</dt>
          <dd>{{ current.mimeType ?? 'unknown' }}</dd>
          <dt>Modified</dt>
          <dd>{{ formatDate(current.modifiedAt) }}</dd>
        </dl>
      </aside>
    </component>
  </main>
</template>

<style scoped>
.viewer {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.stage {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  touch-action: none;
}

.stage.grab {
  cursor: grab;
}

.eyes {
  display: flex;
  flex: none;
}

.eye {
  flex: 1;
  background-repeat: no-repeat;
}

.stage img {
  flex: none;
  max-width: none;
  object-fit: fill;
  user-select: none;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}

.nav {
  position: absolute;
  top: 50%;
  width: 72px;
  height: 120px;
  margin-top: -60px;
  border: 1px solid var(--border);
  border-radius: var(--radius-small);
  background: rgba(13, 17, 23, 0.7);
  font-size: 56px;
  line-height: 1;
  cursor: pointer;
  opacity: 0.6;
  transition: opacity 120ms, border-color 120ms;
}

.nav:hover {
  opacity: 1;
  border-color: var(--accent);
}

.prev {
  left: 20px;
}

.next {
  right: 20px;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap);
  padding: 14px 24px;
  border-top: 1px solid var(--border);
  background: var(--surface);
}

.name {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.name strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name span {
  font-size: var(--font-size-small);
}

.tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.segmented {
  display: flex;
  border: 1px solid var(--border);
  border-radius: var(--radius-small);
  overflow: hidden;
}

.segmented button {
  min-height: 44px;
  padding: 0 14px;
  border: none;
  background: none;
  font-size: var(--font-size-small);
  cursor: pointer;
}

.segmented button + button {
  border-left: 1px solid var(--border);
}

.segmented button.on {
  background: var(--surface-raised);
  color: var(--accent-strong);
}

.zoom {
  min-width: 72px;
  text-align: center;
  font-size: var(--font-size-small);
  font-variant-numeric: tabular-nums;
}

.info {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 380px;
}

dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 20px;
  margin: 0;
  font-size: var(--font-size-small);
}

dt {
  color: var(--text-muted);
}

dd {
  margin: 0;
  overflow-wrap: anywhere;
}
</style>
