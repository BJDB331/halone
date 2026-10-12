<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  eyeAspect,
  fitBox,
  layoutFromName,
  type FileEntry,
  type MediaLayout,
  type MediaProjection,
  type MediaState,
  type StereoLayout,
} from '@halone/sdk'
import NoHost from '../shared/NoHost.vue'
import { formatTime } from '../shared/format'
import { errorMessage, useHost, useOpenedFile, useSettings } from '../shared/host'
import Icon from '../shared/Icon.vue'

/** In this window, or handed to the shell's immersive player with one of its projections. */
type Display = 'window' | MediaProjection

const SKIP_SECONDS = 10

const DISPLAYS: { value: Display; label: string }[] = [
  { value: 'window', label: 'Window' },
  { value: 'flat', label: 'Big screen' },
  { value: 'equirect-180', label: '180°' },
  { value: 'equirect-360', label: '360°' },
]

const STEREO: { value: StereoLayout; label: string }[] = [
  { value: 'mono', label: '2D' },
  { value: 'side-by-side', label: 'Side by side' },
  { value: 'top-bottom', label: 'Top and bottom' },
]

const { halone, status, error } = useHost()
const opened = useOpenedFile(halone)
const settings = useSettings(halone)
const file = ref<FileEntry | null>(null)
const video = ref<HTMLVideoElement>()
const display = ref<Display>('window')
const stereo = ref<StereoLayout>('mono')
const failure = ref<string | null>(null)
const busy = ref(false)
const local = reactive({ playing: false, position: 0, duration: 0, ended: false, width: 0, height: 0 })
const screen = ref<HTMLElement>()
const controls = ref<HTMLElement>()
const screenSize = reactive({ width: 0, height: 0 })
const observer = new ResizeObserver(([entry]) => Object.assign(screenSize, { width: entry!.contentRect.width, height: entry!.contentRect.height }))
watch(screen, (element) => element && observer.observe(element))
onBeforeUnmount(() => observer.disconnect())
const immersive = ref<MediaState | null>(null)
const isAudio = computed(() => file.value?.mimeType?.startsWith('audio/') ?? false)
const coverFailed = ref(false)
const cover = computed(() => (isAudio.value && !coverFailed.value ? file.value?.thumbnailUrl : null))

const off = halone.on('media.changed', (state) => {
  immersive.value = state.open ? state : null
  if (state.error) failure.value = state.error
  if (!state.open && display.value !== 'window') {
    display.value = 'window'
    void restoreWindow()
  }
})
onBeforeUnmount(off)

const inWindow = computed(() => display.value === 'window')
const layout = computed<MediaLayout>(() => ({ projection: inWindow.value ? 'flat' : (display.value as MediaProjection), stereo: stereo.value }))

const playing = computed(() => (inWindow.value ? local.playing : (immersive.value?.playing ?? false)))
const position = computed(() => (inWindow.value ? local.position : (immersive.value?.positionSeconds ?? 0)))
const duration = computed(() => (inWindow.value ? local.duration : (immersive.value?.durationSeconds ?? 0)))

watch(opened, (path) => path && void load(path))

async function load(path: string) {
  failure.value = null
  try {
    const entry = await halone.files.stat(path)
    file.value = entry
    coverFailed.value = false
    void halone.windows.setTitle(entry.name)
    const declared = entry.mimeType?.startsWith('audio/') ? null : layoutFromName(entry.name)
    stereo.value = declared?.stereo ?? 'mono'
    const wanted: Display = !declared || declared.projection === 'flat' ? 'window' : declared.projection
    if (wanted === 'window') {
      if (immersive.value) await halone.media.close()
      immersive.value = null
      display.value = 'window'
      await restoreWindow()
    } else {
      display.value = wanted
      await openImmersive(0)
    }
  } catch (caught) {
    failure.value = errorMessage(caught)
  }
}

async function openImmersive(startSeconds: number) {
  if (!file.value) return
  busy.value = true
  try {
    immersive.value = await halone.media.open(file.value.path, layout.value, startSeconds)
    await updateRotatable()
    await shrinkToControls()
  } catch (caught) {
    failure.value = errorMessage(caught)
    display.value = 'window'
    await restoreWindow()
  } finally {
    busy.value = false
  }
}

async function chooseDisplay(next: Display) {
  if (next === display.value || busy.value) return
  const from = position.value
  const wasImmersive = !inWindow.value
  display.value = next
  if (next === 'window') {
    await halone.media.close().catch(() => {})
    immersive.value = null
    await restoreWindow()
    resumeLocal(from)
  } else if (wasImmersive) {
    await halone.media.setLayout(layout.value).catch((caught) => (failure.value = errorMessage(caught)))
    await updateRotatable()
  } else {
    video.value?.pause()
    await openImmersive(from)
  }
}

async function chooseStereo(next: StereoLayout) {
  if (next === stereo.value) return
  stereo.value = next
  if (!inWindow.value) await halone.media.setLayout(layout.value).catch((caught) => (failure.value = errorMessage(caught)))
}

/** A scene around the wearer can be turned by dragging on empty space; a screen stays where it was put. */
async function updateRotatable() {
  await halone.media.setRotatable(display.value === 'equirect-180' || display.value === 'equirect-360').catch(() => {})
}

/** The window's size before it shrank to its controls, to go back to. */
let windowSize: { widthPx: number; heightPx: number } | null = null

/** While the video plays around the wearer, the window is only its controls, locked at that size. */
async function shrinkToControls() {
  await nextTick()
  const self = await halone.windows.self()
  windowSize ??= { widthPx: self.widthPx, heightPx: self.heightPx }
  const height = Math.ceil(controls.value?.offsetHeight ?? 0)
  if (!height) return
  await halone.windows.setSizeLimits({ minWidthPx: self.widthPx, minHeightPx: height, maxWidthPx: self.widthPx, maxHeightPx: height })
  await halone.windows.resize(self.widthPx, height, 'bottom')
}

async function restoreWindow() {
  if (hidingWindows.value) await setHidingWindows(false)
  const size = windowSize
  if (!size) return
  windowSize = null
  await halone.windows.setSizeLimits(null)
  await halone.windows.resize(size.widthPx, size.heightPx, 'bottom')
}

/** Where the picture goes in the window: the eye's own shape, fitted, since one eye sees half the frame. */
const pictureBox = computed(() =>
  screenSize.width ? fitBox(screenSize.width, screenSize.height, eyeAspect(local.width, local.height, stereo.value)) : null,
)

const pictureStyle = computed(() => {
  const box = pictureBox.value
  return box ? { left: `${box.x}px`, top: `${box.y}px`, width: `${box.width}px`, height: `${box.height}px` } : {}
})

watch(
  () => [inWindow.value, stereo.value, pictureBox.value, file.value?.path] as const,
  async ([here, layout, box]) => {
    await nextTick()
    const rect = screen.value?.getBoundingClientRect()
    if (!here || layout === 'mono' || !box || !box.width || !box.height || !rect || !file.value) {
      return void halone.windows.setStereoRegion(null).catch(() => {})
    }
    void halone.windows
      .setStereoRegion({ stereo: layout, xPx: rect.left + box.x, yPx: rect.top + box.y, widthPx: box.width, heightPx: box.height })
      .catch((caught) => (failure.value = errorMessage(caught)))
  },
)

/** This window's own request to hide every window; it never outlives immersive playback. */
const hidingWindows = ref(false)

async function setHidingWindows(enabled: boolean) {
  hidingWindows.value = enabled
  await halone.windows.setAutoHide(enabled).catch((caught) => (failure.value = errorMessage(caught)))
}

function resumeLocal(seconds: number) {
  const element = video.value
  if (!element) return
  const start = () => {
    element.currentTime = seconds
    void element.play().catch(() => {})
  }
  if (element.readyState >= 1) start()
  else element.addEventListener('loadedmetadata', start, { once: true })
}

function syncLocal() {
  const element = video.value
  if (!element) return
  local.playing = !element.paused
  local.position = element.currentTime
  local.duration = Number.isFinite(element.duration) ? element.duration : 0
  local.ended = element.ended
  local.width = element.videoWidth
  local.height = element.videoHeight
}

function toggle() {
  if (inWindow.value) {
    const element = video.value
    if (!element) return
    if (element.paused) void element.play()
    else element.pause()
  } else {
    void (playing.value ? halone.media.pause() : halone.media.play())
  }
}

function seek(seconds: number) {
  const target = Math.max(0, Math.min(seconds, duration.value || seconds))
  if (inWindow.value) {
    if (video.value) video.value.currentTime = target
  } else {
    void halone.media.seek(target)
  }
}

function onScrub(event: Event) {
  seek(Number((event.target as HTMLInputElement).value))
}

function onKey(event: KeyboardEvent) {
  const actions: Record<string, () => void> = {
    ' ': toggle,
    k: toggle,
    ArrowLeft: () => seek(position.value - SKIP_SECONDS),
    ArrowRight: () => seek(position.value + SKIP_SECONDS),
  }
  const action = actions[event.key]
  if (!action) return
  event.preventDefault()
  action()
}
window.addEventListener('keydown', onKey)
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

/** A screen is sized by its width; a sphere can't be, but can be zoomed out to fit more of it in view. */
const scale = computed(() =>
  display.value === 'flat'
    ? { key: 'media.screenWidthDegrees' as const, label: 'Screen size', min: 20, max: 90, step: 1, format: (v: number) => `${Math.round(v)}°` }
    : { key: 'media.sphereZoom' as const, label: 'Zoom', min: 0.4, max: 1, step: 0.05, format: (v: number) => `${Math.round(v * 100)}%` },
)

function onScale(event: Event) {
  void halone.settings.set(scale.value.key, Number((event.target as HTMLInputElement).value)).catch((caught) => (failure.value = errorMessage(caught)))
}

const where = computed(() => {
  if (display.value === 'flat') return 'on a big screen in front of you'
  if (display.value === 'equirect-180') return 'in front of you, 180° wide'
  return 'all around you'
})
</script>

<template>
  <NoHost :status="status" :error="error" />
  <main v-if="status === 'ready'" class="player">
    <div v-show="inWindow || !file" ref="screen" class="screen" @click="file && toggle()">
      <video
        v-if="file?.url"
        v-show="inWindow && !isAudio"
        ref="video"
        :style="pictureStyle"
        :src="file.url"
        :autoplay="inWindow"
        playsinline
        @loadedmetadata="syncLocal"
        @timeupdate="syncLocal"
        @play="syncLocal"
        @pause="syncLocal"
        @ended="syncLocal"
        @error="failure = `${file.name} could not be played here`"
      />
      <div v-if="file && isAudio" class="cover">
        <img v-if="cover" :src="cover" alt="" @error="coverFailed = true" />
        <span v-else class="cover-placeholder" aria-hidden="true"><Icon name="music_note" /></span>
        <strong>{{ file.name }}</strong>
      </div>
      <div v-if="!file" class="empty">
        <strong>Nothing open</strong>
        <span class="x-muted">Open a video or a song from Files to play it here.</span>
        <button class="x-button primary" @click.stop="halone.apps.launch('files')">Open Files</button>
      </div>
      <p v-if="failure" class="failure" @click.stop>{{ failure }}</p>
    </div>

    <footer v-if="file" ref="controls" class="controls">
      <div v-if="!inWindow" class="away">
        <span class="x-muted">
          Playing {{ where }}
          <template v-if="immersive?.videoWidthPx"> · {{ immersive.videoWidthPx }}×{{ immersive.videoHeightPx }}</template>
          <template v-if="immersive?.rotatable"> · drag outside the windows to turn it</template>
        </span>
        <p v-if="failure" class="away-failure">{{ failure }}</p>
        <button class="x-button small" @click="halone.media.recenter()">Bring it in front of me</button>
        <button class="x-button small" :class="{ primary: hidingWindows }" @click="setHidingWindows(!hidingWindows)">
          {{ hidingWindows ? 'Windows hide' : 'Hide windows' }}
        </button>
      </div>
      <div v-if="!inWindow && settings" class="scale">
        <span class="x-muted">{{ scale.label }}</span>
        <input
          class="scrub"
          type="range"
          :min="scale.min"
          :max="scale.max"
          :step="scale.step"
          :value="settings[scale.key]"
          :aria-label="scale.label"
          @input="onScale"
        />
        <span class="time">{{ scale.format(settings[scale.key]) }}</span>
      </div>
      <div class="transport">
        <button class="x-button small" aria-label="Back 10 seconds" @click="seek(position - SKIP_SECONDS)"><Icon name="replay_10" /></button>
        <button class="x-button play" :aria-label="playing ? 'Pause' : 'Play'" @click="toggle"><Icon :name="playing ? 'pause' : 'play_arrow'" /></button>
        <button class="x-button small" aria-label="Forward 10 seconds" @click="seek(position + SKIP_SECONDS)"><Icon name="forward_10" /></button>
        <span class="time">{{ formatTime(position) }}</span>
        <input class="scrub" type="range" min="0" :max="duration || 0" step="0.1" :value="position" @change="onScrub" />
        <span class="time">{{ formatTime(duration) }}</span>
      </div>
      <div v-if="!isAudio" class="modes">
        <div class="segmented" :class="{ busy }">
          <button v-for="option in DISPLAYS" :key="option.value" :class="{ on: display === option.value }" @click="chooseDisplay(option.value)">
            {{ option.label }}
          </button>
        </div>
        <div class="segmented">
          <button v-for="option in STEREO" :key="option.value" :class="{ on: stereo === option.value }" @click="chooseStereo(option.value)">
            {{ option.label }}
          </button>
        </div>
      </div>
    </footer>
  </main>
</template>

<style scoped>
.player {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.screen {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  overflow: hidden;
}

video {
  position: absolute;
  object-fit: fill;
}

.cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  height: 100%;
  padding: 32px;
  box-sizing: border-box;
  min-width: 0;
}

.cover img {
  flex: 1;
  min-height: 0;
  max-width: 100%;
  aspect-ratio: 1;
  object-fit: contain;
  border-radius: 12px;
}

.cover-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  font-size: 160px;
}

.cover strong {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}

.away {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scale > .x-muted {
  min-width: 120px;
  font-size: var(--font-size-small);
}

.away span {
  flex: 1;
  font-size: var(--font-size-small);
}

.away-failure {
  margin: 0;
  color: var(--danger);
  font-size: var(--font-size-small);
}

.failure {
  position: absolute;
  left: 24px;
  right: 24px;
  bottom: 16px;
  margin: 0;
  color: var(--danger);
  text-align: center;
}

.controls {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 24px 20px;
  border-top: 1px solid var(--border);
  background: var(--surface);
}

.transport,
.modes,
.scale {
  display: flex;
  align-items: center;
  gap: 12px;
}

.modes {
  justify-content: space-between;
}

.play {
  width: 72px;
  min-height: 64px;
  padding: 0;
  font-size: 26px;
}

.time {
  min-width: 80px;
  text-align: center;
  font-size: var(--font-size-small);
  font-variant-numeric: tabular-nums;
}

.scrub {
  flex: 1;
  height: 44px;
  accent-color: var(--accent);
  cursor: pointer;
}

.segmented {
  display: flex;
  border: 1px solid var(--border);
  border-radius: var(--radius-small);
  overflow: hidden;
}

.segmented.busy {
  opacity: 0.5;
}

.segmented button {
  min-height: 48px;
  padding: 0 18px;
  border: none;
  background: none;
  font-size: var(--font-size-small);
  cursor: pointer;
}

.segmented button + button {
  border-left: 1px solid var(--border);
}

.segmented button:hover {
  background: var(--surface-hover);
}

.segmented button.on {
  background: var(--surface-raised);
  color: var(--accent-strong);
}
</style>
