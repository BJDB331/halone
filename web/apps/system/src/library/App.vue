<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { Bookmark, Download, HistoryEntry } from '@halone/sdk'
import NoHost from '../shared/NoHost.vue'
import { displayUrl } from '../shared/address'
import { formatBytes } from '../shared/format'
import { errorMessage, onRoute, useHost } from '../shared/host'
import { showMenu } from '../shared/menu'
import Icon from '../shared/Icon.vue'

type Tab = 'history' | 'bookmarks' | 'downloads'

const { halone, status, error } = useHost()
const tab = ref<Tab>('history')
const query = ref('')
const history = ref<HistoryEntry[]>([])
const bookmarks = ref<Bookmark[]>([])
const downloads = ref<Download[]>([])
const message = ref<string | null>(null)
const clearing = ref(false)
const clearWhat = ref({ history: true, cookies: true, cache: true })
const renaming = ref<string | null>(null)
const renameText = ref('')
const renameField = ref<HTMLInputElement[]>()

onRoute(halone, (route) => {
  if (route === 'clear') {
    tab.value = 'history'
    clearing.value = true
  } else if (route === 'history' || route === 'bookmarks' || route === 'downloads') {
    tab.value = route
  }
})

async function loadHistory() {
  history.value = await halone.history.list(query.value.trim() || undefined).catch(() => history.value)
}

halone.ready.then(() => {
  void loadHistory()
  halone.bookmarks.list().then((value) => (bookmarks.value = value), () => {})
  halone.downloads.list().then((value) => (downloads.value = value), () => {})
}, () => {})

let historyTimer: ReturnType<typeof setTimeout> | undefined
const offs = [
  halone.on('history.changed', () => {
    clearTimeout(historyTimer)
    historyTimer = setTimeout(loadHistory, 300)
  }),
  halone.on('bookmarks.changed', (value) => (bookmarks.value = value)),
  halone.on('downloads.changed', (value) => (downloads.value = value)),
]
onBeforeUnmount(() => offs.forEach((off) => off()))

watch(query, () => {
  clearTimeout(historyTimer)
  historyTimer = setTimeout(loadHistory, 200)
})

const days = computed(() => {
  const groups: { label: string; entries: HistoryEntry[] }[] = []
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const dayMs = 24 * 60 * 60 * 1000
  for (const entry of history.value) {
    const day = new Date(entry.visitedAt)
    day.setHours(0, 0, 0, 0)
    const ago = Math.round((today.getTime() - day.getTime()) / dayMs)
    const label =
      ago <= 0 ? 'Today' : ago === 1 ? 'Yesterday' : day.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' })
    const last = groups[groups.length - 1]
    if (last?.label === label) last.entries.push(entry)
    else groups.push({ label, entries: [entry] })
  }
  return groups
})

const shownBookmarks = computed(() => {
  const needle = query.value.trim().toLowerCase()
  return needle ? bookmarks.value.filter((b) => (b.title + ' ' + b.url).toLowerCase().includes(needle)) : bookmarks.value
})

const shownDownloads = computed(() => {
  const needle = query.value.trim().toLowerCase()
  return needle ? downloads.value.filter((d) => (d.fileName + ' ' + d.url).toLowerCase().includes(needle)) : downloads.value
})

const time = (at: number) => new Date(at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
const host = (url: string) => {
  try {
    return new URL(url).host
  } catch {
    return url
  }
}

async function run(action: () => Promise<unknown>) {
  try {
    message.value = null
    await action()
  } catch (caught) {
    message.value = errorMessage(caught)
  }
}

const open = (url: string, isPrivate = false) => run(() => halone.browser.open(url, isPrivate))

function pageMenu(event: MouseEvent, url: string, extra: Parameters<typeof showMenu>[1]) {
  showMenu(
    halone,
    [
      { id: 'open', label: 'Open in new tab', run: () => open(url) },
      { id: 'open-private', label: 'Open in private tab', run: () => open(url, true) },
      { id: 'copy', label: 'Copy address', run: () => halone.clipboard.writeText(url) },
      ...extra,
    ],
    event.clientX,
    event.clientY,
  ).catch(() => {})
}

function historyMenu(event: MouseEvent, entry: HistoryEntry) {
  pageMenu(event, entry.url, [
    'separator',
    { id: 'remove', label: 'Delete from history', danger: true, run: () => run(() => halone.history.remove(entry.url)) },
  ])
}

function bookmarkMenu(event: MouseEvent, bookmark: Bookmark) {
  pageMenu(event, bookmark.url, [
    'separator',
    { id: 'rename', label: 'Rename', run: () => startRename(bookmark) },
    { id: 'remove', label: 'Delete bookmark', danger: true, run: () => run(() => halone.bookmarks.remove(bookmark.id)) },
  ])
}

async function startRename(bookmark: Bookmark) {
  renaming.value = bookmark.id
  renameText.value = bookmark.title
  await nextTick()
  renameField.value?.[0]?.select()
}

function finishRename(bookmark: Bookmark) {
  const title = renameText.value.trim()
  renaming.value = null
  if (title && title !== bookmark.title) run(() => halone.bookmarks.update(bookmark.id, { title }))
}

function folderOf(path: string) {
  return path.slice(0, path.lastIndexOf('/')) || '/'
}

function openDownload(download: Download) {
  if (download.state === 'done' && download.path) run(() => halone.apps.open(download.path!))
}

function downloadMenu(event: MouseEvent, download: Download) {
  const done = download.state === 'done' && !!download.path
  showMenu(
    halone,
    [
      { id: 'open', label: 'Open', disabled: !done, run: () => openDownload(download) },
      { id: 'folder', label: 'Show in Files', disabled: !done, run: () => run(() => halone.apps.launch('files', folderOf(download.path!))) },
      { id: 'copy', label: 'Copy download link', run: () => halone.clipboard.writeText(download.url) },
      'separator',
      download.state === 'downloading'
        ? { id: 'cancel', label: 'Cancel', danger: true, run: () => run(() => halone.downloads.cancel(download.id)) }
        : { id: 'remove', label: 'Remove from list', run: () => run(() => halone.downloads.remove(download.id)) },
    ],
    event.clientX,
    event.clientY,
  ).catch(() => {})
}

function progress(download: Download) {
  if (download.state === 'downloading') {
    const total = download.totalBytes ? ` of ${formatBytes(download.totalBytes)}` : ''
    return `${formatBytes(download.receivedBytes)}${total}`
  }
  if (download.state === 'done') return formatBytes(download.totalBytes ?? download.receivedBytes)
  if (download.state === 'cancelled') return 'Cancelled'
  return `Failed${download.error ? `: ${download.error}` : ''}`
}

function clearData() {
  const what = { ...clearWhat.value }
  clearing.value = false
  run(async () => {
    await halone.browser.clearData(what)
    message.value = 'Cleared.'
  })
}
</script>

<template>
  <NoHost :status="status" :error="error" />
  <main v-if="status === 'ready'" class="library">
    <header>
      <div class="tabs">
        <button class="x-button" :class="{ primary: tab === 'history' }" @click="tab = 'history'">History</button>
        <button class="x-button" :class="{ primary: tab === 'bookmarks' }" @click="tab = 'bookmarks'">Bookmarks</button>
        <button class="x-button" :class="{ primary: tab === 'downloads' }" @click="tab = 'downloads'">Downloads</button>
      </div>
      <input v-model="query" class="x-input search" placeholder="Search" spellcheck="false" autocomplete="off" />
      <button v-if="tab === 'history'" class="x-button" @click="clearing = true">Clear…</button>
      <button v-if="tab === 'downloads'" class="x-button" :disabled="!downloads.some((d) => d.state !== 'downloading')" @click="run(halone.downloads.clear)">
        Clear list
      </button>
    </header>
    <p v-if="message" class="message">{{ message }}</p>

    <section v-if="tab === 'history'" class="list">
      <p v-if="!history.length" class="x-muted empty">{{ query ? 'Nothing in history matches.' : 'No history yet.' }}</p>
      <template v-for="day in days" :key="day.label">
        <h2 class="x-section-title">{{ day.label }}</h2>
        <div v-for="entry in day.entries" :key="entry.url" class="row" @click="open(entry.url)" @contextmenu.prevent="historyMenu($event, entry)">
          <span class="favicon">{{ (entry.title || host(entry.url)).slice(0, 1).toUpperCase() }}</span>
          <span class="text">
            <span class="title">{{ entry.title || displayUrl(entry.url) }}</span>
            <span class="x-muted url">{{ host(entry.url) }}</span>
          </span>
          <span class="x-muted when">{{ time(entry.visitedAt) }}</span>
        </div>
      </template>
    </section>

    <section v-else-if="tab === 'bookmarks'" class="list">
      <p v-if="!shownBookmarks.length" class="x-muted empty">
        {{ query ? 'No bookmark matches.' : 'No bookmarks yet. The star in a web window’s address field adds one.' }}
      </p>
      <div
        v-for="bookmark in shownBookmarks"
        :key="bookmark.id"
        class="row"
        @click="renaming !== bookmark.id && open(bookmark.url)"
        @contextmenu.prevent="bookmarkMenu($event, bookmark)"
      >
        <img v-if="bookmark.iconUrl" class="favicon" :src="bookmark.iconUrl" alt="" @error="bookmark.iconUrl = null" />
        <span v-else class="favicon">{{ bookmark.title.slice(0, 1).toUpperCase() }}</span>
        <span class="text">
          <input
            v-if="renaming === bookmark.id"
            ref="renameField"
            v-model="renameText"
            class="x-input rename"
            @click.stop
            @keydown.enter="finishRename(bookmark)"
            @keydown.escape="renaming = null"
            @blur="finishRename(bookmark)"
          />
          <span v-else class="title">{{ bookmark.title }}</span>
          <span class="x-muted url">{{ displayUrl(bookmark.url) }}</span>
        </span>
      </div>
    </section>

    <section v-else class="list">
      <p v-if="!shownDownloads.length" class="x-muted empty">{{ query ? 'No download matches.' : 'Nothing downloaded yet.' }}</p>
      <div
        v-for="download in shownDownloads"
        :key="download.id"
        class="row"
        :class="{ dim: download.state === 'failed' || download.state === 'cancelled' }"
        @click="openDownload(download)"
        @contextmenu.prevent="downloadMenu($event, download)"
      >
        <span class="favicon"><Icon name="download" /></span>
        <span class="text">
          <span class="title">{{ download.fileName }}</span>
          <span class="x-muted url">{{ progress(download) }} · {{ host(download.url) }}</span>
          <span v-if="download.state === 'downloading'" class="bar">
            <span
              class="fill"
              :class="{ unknown: !download.totalBytes }"
              :style="download.totalBytes ? { width: `${(100 * download.receivedBytes) / download.totalBytes}%` } : {}"
            />
          </span>
        </span>
        <button v-if="download.state === 'downloading'" class="x-button small danger" @click.stop="run(() => halone.downloads.cancel(download.id))">
          Cancel
        </button>
      </div>
    </section>

    <div v-if="clearing" class="scrim" @click.self="clearing = false">
      <div class="x-card dialog">
        <h2 class="x-title">Clear browsing data</h2>
        <label><input v-model="clearWhat.history" type="checkbox" class="switch" /> History</label>
        <label><input v-model="clearWhat.cookies" type="checkbox" class="switch" /> Cookies, site data and permissions <span class="x-muted">(signs you out of sites)</span></label>
        <label><input v-model="clearWhat.cache" type="checkbox" class="switch" /> Cached pages and images</label>
        <p class="x-muted">Bookmarks and downloaded files stay.</p>
        <div class="actions">
          <button class="x-button" @click="clearing = false">Cancel</button>
          <button class="x-button primary danger" :disabled="!clearWhat.history && !clearWhat.cookies && !clearWhat.cache" @click="clearData">
            Clear
          </button>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.library {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 28px 36px 0;
}

header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.tabs {
  display: flex;
  gap: 8px;
}

.search {
  flex: 1;
}

.message {
  margin: 12px 0 0;
  color: var(--accent);
}

.list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0 28px;
}

.empty {
  margin-top: 40px;
  text-align: center;
}

.row {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 72px;
  padding: 8px 14px;
  border-radius: var(--radius-small);
  cursor: pointer;
}

.row:hover {
  background: var(--surface-hover);
}

.row.dim {
  opacity: 0.6;
}

.favicon {
  flex: none;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--surface-raised);
  object-fit: contain;
  font-size: 22px;
}

.text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.title,
.url {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.url,
.when {
  font-size: var(--font-size-small);
}

.rename {
  height: 44px;
}

.bar {
  position: relative;
  height: 6px;
  margin-top: 8px;
  border-radius: 3px;
  background: var(--surface-raised);
  overflow: hidden;
}

.fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--accent);
}

.fill.unknown {
  width: 30%;
  animation: sweep 1.2s linear infinite;
}

@keyframes sweep {
  from {
    left: -30%;
  }
  to {
    left: 100%;
  }
}

.scrim {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.6);
}

.dialog {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 620px;
  padding: 32px;
}

.dialog .x-title {
  margin: 0 0 6px;
}

.dialog label {
  display: flex;
  align-items: center;
  gap: 16px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
