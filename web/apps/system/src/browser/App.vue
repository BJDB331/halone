<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { BrowserState, DownloadState, PermissionKind } from '@halone/sdk'
import { displayUrl, toUrl } from '../shared/address'
import { useHost, useSettings } from '../shared/host'
import { showMenu, type MenuAction } from '../shared/menu'

const { halone, status } = useHost()
const settings = useSettings(halone)
const state = ref<BrowserState | null>(null)
const field = ref<HTMLInputElement>()
const address = ref('')
const editing = ref(false)

halone.browser.state().then((value) => (state.value = value), () => {})
const off = halone.on('browser.changed', (value) => (state.value = value))
const offCommand = halone.on('browser.command', ({ command }) => {
  if (command === 'focus-address') field.value?.focus()
})
onBeforeUnmount(() => {
  off()
  offCommand()
})

const active = computed(() => state.value?.tabs.find((tab) => tab.id === state.value?.activeTab) ?? null)

watch(
  () => active.value?.url,
  (url) => {
    if (!editing.value) address.value = url ? displayUrl(url) : ''
  },
  { immediate: true },
)

// A new tab is blank and waiting to be told where to go.
watch(
  () => state.value?.activeTab,
  async () => {
    if (active.value && !active.value.url) {
      await nextTick()
      field.value?.focus()
    }
  },
)

function startEditing() {
  editing.value = true
  address.value = active.value?.url ?? ''
  nextTick(() => field.value?.select())
}

function stopEditing() {
  editing.value = false
  address.value = active.value?.url ? displayUrl(active.value.url) : ''
}

function go() {
  const text = address.value.trim()
  if (!text) return
  halone.browser.navigate(toUrl(text, settings.value?.['browser.searchEngine'])).catch(() => {})
  field.value?.blur()
}

function onFieldKey(event: KeyboardEvent) {
  if (event.key === 'Escape') field.value?.blur()
}

const call = (action: () => Promise<unknown>) => action().catch(() => {})

/** Downloads finished since this window opened that the wearer has not looked at yet; they keep the button up. */
const unseen = ref(new Set<number>())
const known = new Map<number, DownloadState>()
halone.downloads.list().then((list) => list.forEach((download) => known.set(download.id, download.state)), () => {})
const offDownloads = halone.on('downloads.changed', (list) => {
  const next = new Set(unseen.value)
  for (const download of list) {
    if (download.state === 'done' && known.get(download.id) !== 'done') next.add(download.id)
    known.set(download.id, download.state)
  }
  for (const id of next) if (!list.some((download) => download.id === id)) next.delete(id)
  unseen.value = next
})
onBeforeUnmount(offDownloads)

function openDownloads() {
  unseen.value = new Set()
  library('downloads')
}

const ASKS: Record<PermissionKind, string> = {
  geolocation: 'wants to know your location',
  popup: 'wants to open a pop-up window',
  microphone: 'wants to use your microphone',
  clipboard: 'wants to read your clipboard',
  'persistent-storage': 'wants to keep its data on this device',
  drm: 'wants to play protected content',
  'storage-access': 'wants to use its cookies on this site',
  'local-network': 'wants to reach devices on your network',
  'local-device': 'wants to reach devices on this phone',
}

const remember = ref(true)
watch(
  () => state.value?.prompt?.id,
  () => (remember.value = true),
)

function answer(allow: boolean) {
  const prompt = state.value?.prompt
  if (prompt) call(() => halone.browser.answerPrompt(prompt.id, allow, prompt.rememberable && remember.value))
}

const isPrivate = computed(() => active.value?.private ?? false)
const library = (route: string) => call(() => halone.apps.launch('library', route))

/** Installing the page as an app, or opening it in the app once it is one. */
function appActions(): MenuAction[] {
  const app = state.value?.webApp
  if (!app) return []
  if (!app.installed) return [{ id: 'install', label: `Install “${app.name}”`, run: () => halone.browser.installApp() }]
  return [{ id: 'open-app', label: `Open in “${app.name}”`, run: () => halone.apps.launch(app.appId, active.value?.url) }]
}

function openMenu(event: MouseEvent) {
  const button = (event.currentTarget as HTMLElement).getBoundingClientRect()
  showMenu(
    halone,
    [
      { id: 'new-tab', label: 'New tab', run: () => halone.browser.newTab() },
      { id: 'new-private', label: 'New private tab', run: () => halone.browser.newTab(undefined, true) },
      'separator',
      {
        id: 'bookmark',
        label: state.value?.bookmarked ? 'Remove bookmark' : 'Bookmark this page',
        disabled: !active.value?.url,
        run: () => halone.browser.toggleBookmark(),
      },
      ...appActions(),
      { id: 'bookmarks', label: 'Bookmarks', run: () => library('bookmarks') },
      { id: 'history', label: 'History', run: () => library('history') },
      { id: 'downloads', label: 'Downloads', run: () => library('downloads') },
      'separator',
      { id: 'clear', label: 'Clear history and cookies…', run: () => library('clear') },
      { id: 'settings', label: 'Browser settings', run: () => halone.apps.launch('settings', 'browser') },
    ],
    button.right,
    button.bottom,
  ).catch(() => {})
}

function tabMenu(event: MouseEvent, tabId: number) {
  const others = (state.value?.tabs.length ?? 1) > 1
  showMenu(
    halone,
    [
      { id: 'new-tab', label: 'New tab', run: () => halone.browser.newTab() },
      'separator',
      { id: 'close', label: 'Close tab', run: () => halone.browser.closeTab(tabId) },
      {
        id: 'close-others',
        label: 'Close other tabs',
        disabled: !others,
        run: async () => {
          for (const tab of state.value?.tabs ?? []) if (tab.id !== tabId) await halone.browser.closeTab(tab.id)
        },
      },
    ],
    event.clientX,
    event.clientY,
  ).catch(() => {})
}
</script>

<template>
  <main v-if="status === 'ready' && state" class="chrome" :class="{ private: isPrivate }">
    <div class="toolbar">
      <button class="tool" :disabled="!state.canGoBack" aria-label="Back" @click="call(halone.browser.back)">
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M20 7 L11 16 L20 25" /></svg>
      </button>
      <button class="tool" :disabled="!state.canGoForward" aria-label="Forward" @click="call(halone.browser.forward)">
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M12 7 L21 16 L12 25" /></svg>
      </button>
      <button
        class="tool"
        :aria-label="state.loading ? 'Stop' : 'Reload'"
        @click="call(state.loading ? halone.browser.stop : halone.browser.reload)"
      >
        <svg v-if="state.loading" viewBox="0 0 32 32" aria-hidden="true"><path d="M9 9 L23 23 M23 9 L9 23" /></svg>
        <svg v-else viewBox="0 0 32 32" aria-hidden="true">
          <path d="M24 16 A8 8 0 1 1 21.7 10.3" />
          <path d="M22 5 V11 H16" />
        </svg>
      </button>

      <form class="address" @submit.prevent="go">
        <span v-if="isPrivate && !editing" class="private-badge" title="Private tab">
          <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M5 14 C9 9 23 9 27 14 M8 18 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0 M16 18 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0" /></svg>
        </span>
        <input
          ref="field"
          v-model="address"
          :class="{ editing }"
          placeholder="Search or enter a web address"
          autocomplete="off"
          autocapitalize="off"
          spellcheck="false"
          enterkeyhint="go"
          @focus="startEditing"
          @blur="stopEditing"
          @keydown="onFieldKey"
        />
        <button
          v-if="active?.url && !editing"
          type="button"
          class="star"
          :class="{ on: state.bookmarked }"
          :aria-label="state.bookmarked ? 'Remove bookmark' : 'Bookmark this page'"
          @click="call(halone.browser.toggleBookmark)"
        >
          <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M16 5 L19.3 12 L27 12.8 L21.2 18 L22.9 25.6 L16 21.6 L9.1 25.6 L10.8 18 L5 12.8 L12.7 12 Z" /></svg>
        </button>
      </form>

      <button
        v-if="state.activeDownloads > 0 || unseen.size > 0"
        class="tool downloads"
        :class="{ done: state.activeDownloads === 0 }"
        aria-label="Downloads"
        @click="openDownloads"
      >
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M16 6 V20 M10 14 L16 20 L22 14 M8 26 H24" /></svg>
        <span class="count">{{ state.activeDownloads > 0 ? state.activeDownloads : '✓' }}</span>
      </button>
      <button class="tool" aria-label="New tab" @click="call(() => halone.browser.newTab())">
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M16 7 V25 M7 16 H25" /></svg>
      </button>
      <button class="tool" aria-label="Menu" @click="openMenu">
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M16 8 v0.5 M16 16 v0.5 M16 24 v0.5" class="dots" /></svg>
      </button>
      <div v-if="state.loading" class="progress" />
    </div>

    <div v-if="state.tabs.length > 1" class="tabs">
      <div
        v-for="tab in state.tabs"
        :key="tab.id"
        class="tab"
        :class="{ active: tab.id === state.activeTab, private: tab.private }"
        @click="call(() => halone.browser.selectTab(tab.id))"
        @contextmenu.prevent="tabMenu($event, tab.id)"
      >
        <svg v-if="tab.private" class="tab-private" viewBox="0 0 32 32" aria-hidden="true">
          <path d="M5 14 C9 9 23 9 27 14 M8 18 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0 M16 18 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0" />
        </svg>
        <span class="tab-title">{{ tab.title || (tab.url ? displayUrl(tab.url) : 'New tab') }}</span>
        <button class="close" aria-label="Close tab" @click.stop="call(() => halone.browser.closeTab(tab.id))">
          <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M11 11 L21 21 M21 11 L11 21" /></svg>
        </button>
      </div>
    </div>

    <div v-if="state.prompt" class="prompt" role="alertdialog">
      <span class="prompt-text"><strong>{{ state.prompt.host }}</strong> {{ ASKS[state.prompt.kind] ?? `wants ${state.prompt.kind}` }}</span>
      <label v-if="state.prompt.rememberable" class="remember"><input v-model="remember" type="checkbox" /> Remember</label>
      <button class="x-button small" @click="answer(false)">Block</button>
      <button class="x-button small primary" @click="answer(true)">Allow</button>
    </div>
  </main>
</template>

<style scoped>
.chrome {
  height: 100vh;
  background: var(--surface-raised);
}

.toolbar {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 76px;
  padding: 0 16px;
}

.tool {
  flex: none;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.tool:hover:not(:disabled) {
  background: var(--surface-hover);
}

.tool:disabled {
  opacity: 0.3;
  cursor: default;
}

svg {
  width: 28px;
  height: 28px;
  fill: none;
  stroke: var(--text);
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.address {
  position: relative;
  flex: 1;
  margin: 0 10px;
}

.chrome.private {
  background: #241a36;
}

.chrome.private .address input {
  background: #170f26;
}

.private-badge {
  position: absolute;
  left: 18px;
  top: 12px;
  pointer-events: none;
}

.private-badge svg,
.tab-private {
  stroke: #c9a6ff;
}

.tab-private {
  flex: none;
  width: 22px;
  height: 22px;
}

.star {
  position: absolute;
  right: 8px;
  top: 6px;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.star svg {
  width: 24px;
  height: 24px;
}

.star.on svg {
  fill: var(--pinned);
  stroke: var(--pinned);
}

.star:hover {
  background: var(--surface-hover);
}

.dots {
  stroke-width: 4.5;
}

.downloads {
  position: relative;
}

.downloads.done .count {
  background: var(--ok);
}

.downloads .count {
  position: absolute;
  right: 4px;
  bottom: 4px;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  background: var(--accent);
  color: #000;
  font-size: 14px;
  line-height: 20px;
}

.address input {
  width: 100%;
  height: 52px;
  padding: 0 56px;
  border: 1px solid transparent;
  border-radius: 26px;
  background: var(--surface);
  color: var(--text);
  font: inherit;
  font-size: 22px;
  text-align: center;
  outline: none;
}

.address input.editing {
  border-color: var(--accent);
  text-align: left;
}

.address input::placeholder {
  color: var(--text-muted);
}

.progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  background-size: 40% 100%;
  background-repeat: no-repeat;
  animation: sweep 1.2s linear infinite;
}

@keyframes sweep {
  from {
    background-position: -40% 0;
  }
  to {
    background-position: 140% 0;
  }
}

.prompt {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 64px;
  padding: 0 16px 8px 28px;
  font-size: 20px;
}

.prompt-text {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.remember {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 18px;
}

.remember input {
  width: 22px;
  height: 22px;
  accent-color: var(--accent);
}

.tabs {
  display: flex;
  gap: 6px;
  height: 52px;
  padding: 0 16px 8px;
}

.tab {
  flex: 1 1 0;
  max-width: 300px;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 4px 0 18px;
  border-radius: 14px;
  color: var(--text-muted);
  font-size: 18px;
  cursor: pointer;
}

.tab:hover {
  background: var(--surface-hover);
}

.tab.active {
  background: var(--surface);
  color: var(--text);
}

.tab-title {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.close {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.close svg {
  width: 20px;
  height: 20px;
}

.close:hover {
  background: var(--surface-hover);
}
</style>
