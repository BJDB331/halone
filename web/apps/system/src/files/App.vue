<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { isVideo, parentPath, type FileEntry, type FileRoot, type FileRootKind } from '@halone/sdk'
import NoHost from '../shared/NoHost.vue'
import Icon from '../shared/Icon.vue'
import { fileIconUrl } from '../shared/fileIcons'
import { byName, formatBytes, formatDate } from '../shared/format'
import { errorMessage, onRoute, useHost } from '../shared/host'
import { showMenu, type MenuAction } from '../shared/menu'

type Sort = 'name' | 'modified' | 'size'

const ROOT_ICONS: Record<FileRootKind, string> = {
  storage: 'smartphone',
  pictures: 'image',
  camera: 'photo_camera',
  movies: 'movie',
  music: 'music_note',
  downloads: 'download',
  documents: 'description',
}

const { halone, status, error } = useHost()
const granted = ref<boolean | null>(null)
const roots = ref<FileRoot[]>([])
const path = ref<string | null>(null)
const entries = ref<FileEntry[]>([])
const loading = ref(false)
const failure = ref<string | null>(null)
const notice = ref<string | null>(null)
const view = ref<'grid' | 'list'>('grid')
const sort = ref<Sort>('name')
const history = ref<string[]>([])
/** Thumbnails that would not load, which fall back to the file's icon. */
const broken = ref(new Set<string>())
const thumbnailOf = (entry: FileEntry) => (entry.thumbnailUrl && !broken.value.has(entry.thumbnailUrl) ? entry.thumbnailUrl : null)

function markBroken(url: string) {
  broken.value = new Set(broken.value).add(url)
}

/** A folder `apps.launch` asked for, opened once the roots are known. */
const routed = ref<string | null>(null)
onRoute(halone, (route) => {
  routed.value = route
  if (roots.value.length && within(route)) open(route)
})

halone.ready.then(checkAccess, () => {})
halone.on('files.accessChanged', ({ granted: now }) => {
  granted.value = now
  if (now) void loadRoots()
})

async function checkAccess() {
  granted.value = (await halone.files.access()).granted
  if (granted.value) await loadRoots()
}

async function loadRoots() {
  try {
    roots.value = await halone.files.roots()
    if (routed.value && within(routed.value)) open(routed.value)
    else if (!path.value) open(roots.value.find((root) => root.kind === 'pictures')?.path ?? roots.value[0]?.path ?? '/')
  } catch (caught) {
    failure.value = errorMessage(caught)
  }
}

watch(path, (current) => current && load(current))

async function load(current: string) {
  loading.value = true
  failure.value = null
  try {
    entries.value = await halone.files.list(current)
  } catch (caught) {
    entries.value = []
    failure.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

const refresh = () => path.value && load(path.value)

function open(target: string, remember = true) {
  if (target === path.value) return
  if (remember && path.value) history.value.push(path.value)
  path.value = target
}

function back() {
  const previous = history.value.pop()
  if (previous) open(previous, false)
}

function up() {
  const parent = path.value && parentPath(path.value)
  if (parent && within(parent)) open(parent)
}

/** The root a path lies under, the deepest one when roots nest. */
const root = computed(() =>
  roots.value
    .filter((candidate) => path.value && within(path.value, candidate))
    .sort((a, b) => b.path.length - a.path.length)[0],
)

function within(target: string, candidate?: FileRoot): boolean {
  const bases = candidate ? [candidate] : roots.value
  return bases.some((base) => target === base.path || target.startsWith(base.path.endsWith('/') ? base.path : base.path + '/'))
}

const crumbs = computed(() => {
  const base = root.value
  if (!base || !path.value) return []
  const rest = path.value.slice(base.path.length).split('/').filter(Boolean)
  const list = [{ name: base.name, path: base.path }]
  let at = base.path
  for (const name of rest) {
    at = at.endsWith('/') ? at + name : `${at}/${name}`
    list.push({ name, path: at })
  }
  return list
})

const sorted = computed(() => {
  const compare: Record<Sort, (a: FileEntry, b: FileEntry) => number> = {
    name: byName,
    modified: (a, b) => b.modifiedAt - a.modifiedAt || byName(a, b),
    size: (a, b) => b.sizeBytes - a.sizeBytes || byName(a, b),
  }
  const folders = entries.value.filter((entry) => entry.kind === 'directory').sort(sort.value === 'size' ? byName : compare[sort.value])
  const files = entries.value.filter((entry) => entry.kind === 'file').sort(compare[sort.value])
  return [...folders, ...files]
})

const summary = computed(() => {
  const folders = entries.value.filter((entry) => entry.kind === 'directory').length
  const files = entries.value.length - folders
  const parts = []
  if (folders) parts.push(`${folders} folder${folders === 1 ? '' : 's'}`)
  if (files) parts.push(`${files} file${files === 1 ? '' : 's'}`)
  return parts.join(', ') || 'Empty'
})

let noticeTimer: ReturnType<typeof setTimeout> | undefined

function tell(message: string) {
  notice.value = message
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => (notice.value = null), 4000)
}

type Dialog =
  | { kind: 'rename'; entry: FileEntry; text: string }
  | { kind: 'folder'; text: string }
  | { kind: 'delete'; entry: FileEntry }

const dialog = ref<Dialog | null>(null)
const dialogField = ref<HTMLInputElement>()

async function ask(next: Dialog) {
  dialog.value = next
  await nextTick()
  const field = dialogField.value
  if (!field) return
  field.focus()
  const dot = next.kind === 'rename' && next.entry.kind === 'file' ? next.text.lastIndexOf('.') : -1
  field.setSelectionRange(0, dot > 0 ? dot : field.value.length)
}

async function act(action: () => Promise<unknown>, done?: string) {
  try {
    await action()
    if (done) tell(done)
  } catch (caught) {
    tell(errorMessage(caught))
  }
  await refresh()
}

function confirmDialog() {
  const current = dialog.value
  if (!current || !path.value) return
  dialog.value = null
  const folder = path.value
  if (current.kind === 'rename') {
    const name = current.text.trim()
    if (name && name !== current.entry.name) void act(() => halone.files.rename(current.entry.path, name))
  } else if (current.kind === 'folder') {
    const name = current.text.trim()
    if (name) void act(() => halone.files.createDirectory(folder, name))
  } else {
    void act(() => halone.files.delete([current.entry.path]), `Deleted ${current.entry.name}`)
  }
}

const putOnClipboard = (entry: FileEntry, cut: boolean) =>
  act(() => halone.clipboard.writeFiles({ paths: [entry.path], cut }), cut ? `Cut ${entry.name}` : `Copied ${entry.name}`)

async function paste(into: string) {
  const files = (await halone.clipboard.read().catch(() => null))?.files
  if (!files?.paths.length) return
  const count = files.paths.length === 1 ? 'one item' : `${files.paths.length} items`
  await act(async () => {
    if (files.cut) await halone.files.move(files.paths, into)
    else await halone.files.copy(files.paths, into)
  }, `${files.cut ? 'Moved' : 'Copied'} ${count}`)
}

async function entryMenu(event: MouseEvent, entry: FileEntry) {
  const clipboard = await halone.clipboard.read().catch(() => null)
  const folder = entry.kind === 'directory'
  const actions: MenuAction[] = [
    { id: 'open', label: 'Open', run: () => activate(entry) },
    'separator',
    { id: 'cut', label: 'Cut', run: () => putOnClipboard(entry, true) },
    { id: 'copy', label: 'Copy', run: () => putOnClipboard(entry, false) },
  ]
  if (folder) actions.push({ id: 'paste', label: 'Paste into folder', disabled: !clipboard?.files, run: () => paste(entry.path) })
  actions.push(
    'separator',
    { id: 'rename', label: 'Rename', run: () => ask({ kind: 'rename', entry, text: entry.name }) },
    { id: 'copy-path', label: 'Copy path', run: () => halone.clipboard.writeText(entry.path) },
    'separator',
    { id: 'delete', label: 'Delete', danger: true, run: () => ask({ kind: 'delete', entry }) },
  )
  await showMenu(halone, actions, event.clientX, event.clientY).catch(() => {})
}

async function folderMenu(event: MouseEvent) {
  if (!path.value) return
  const here = path.value
  const clipboard = await halone.clipboard.read().catch(() => null)
  await showMenu(
    halone,
    [
      { id: 'paste', label: 'Paste', disabled: !clipboard?.files, run: () => paste(here) },
      { id: 'new-folder', label: 'New folder', run: () => ask({ kind: 'folder', text: 'New folder' }) },
      'separator',
      { id: 'refresh', label: 'Refresh', run: refresh },
      { id: 'copy-path', label: 'Copy path', run: () => halone.clipboard.writeText(here) },
    ],
    event.clientX,
    event.clientY,
  ).catch(() => {})
}

async function activate(entry: FileEntry) {
  if (entry.kind === 'directory') return open(entry.path)
  try {
    await halone.apps.open(entry.path)
  } catch (caught) {
    tell(errorMessage(caught))
  }
}
</script>

<template>
  <NoHost :status="status" :error="error" />
  <main v-if="status === 'ready'" class="files">
    <section v-if="granted === false" class="access">
      <div class="x-card">
        <h1 class="x-title">Allow access to files</h1>
        <p class="x-muted">halone needs permission to read the phone's storage. Grant it on the phone, then come back here.</p>
        <button class="x-button primary" @click="halone.files.requestAccess()">Open the setting on the phone</button>
        <button class="x-button" @click="checkAccess">Check again</button>
      </div>
    </section>

    <template v-else-if="granted">
      <nav class="sidebar">
        <button
          v-for="candidate in roots"
          :key="candidate.path"
          class="root"
          :class="{ active: root?.path === candidate.path }"
          @click="open(candidate.path)"
        >
          <Icon class="root-icon" :name="ROOT_ICONS[candidate.kind]" />
          {{ candidate.name }}
        </button>
      </nav>

      <section class="content">
        <header class="toolbar">
          <button class="x-button small" :disabled="!history.length" aria-label="Back" @click="back"><Icon name="arrow_back" /></button>
          <button class="x-button small" :disabled="!root || path === root.path" aria-label="Up" @click="up"><Icon name="arrow_upward" /></button>
          <ol class="crumbs">
            <li v-for="crumb in crumbs" :key="crumb.path">
              <button :class="{ current: crumb.path === path }" @click="open(crumb.path)">{{ crumb.name }}</button>
            </li>
          </ol>
          <div class="segmented">
            <button v-for="option in ['name', 'modified', 'size'] as const" :key="option" :class="{ on: sort === option }" @click="sort = option">
              {{ option === 'name' ? 'Name' : option === 'modified' ? 'Date' : 'Size' }}
            </button>
          </div>
          <div class="segmented">
            <button :class="{ on: view === 'grid' }" aria-label="Grid" @click="view = 'grid'"><Icon name="grid_view" /></button>
            <button :class="{ on: view === 'list' }" aria-label="List" @click="view = 'list'"><Icon name="view_list" /></button>
          </div>
        </header>

        <div class="listing" @contextmenu.prevent="folderMenu">
          <p v-if="failure" class="message">{{ failure }}</p>
          <p v-else-if="!loading && !entries.length" class="message x-muted">This folder is empty.</p>

          <div v-else-if="view === 'grid'" class="grid">
            <button
              v-for="entry in sorted"
              :key="entry.path"
              class="tile"
              @click="activate(entry)"
              @contextmenu.prevent.stop="entryMenu($event, entry)"
            >
              <span class="thumb">
                <img
                  v-if="thumbnailOf(entry)"
                  :src="thumbnailOf(entry)!"
                  loading="lazy"
                  decoding="async"
                  alt=""
                  @error="markBroken(entry.thumbnailUrl!)"
                />
                <img v-else class="thumb-icon" :src="fileIconUrl(entry)" alt="" draggable="false" />
                <span v-if="isVideo(entry) && thumbnailOf(entry)" class="play"><Icon name="play_arrow" /></span>
              </span>
              <span class="tile-name">{{ entry.name }}</span>
            </button>
          </div>

          <table v-else class="list">
            <tbody>
              <tr v-for="entry in sorted" :key="entry.path" @click="activate(entry)" @contextmenu.prevent.stop="entryMenu($event, entry)">
                <td class="list-icon">
                  <img
                    v-if="thumbnailOf(entry)"
                    :src="thumbnailOf(entry)!"
                    loading="lazy"
                    decoding="async"
                    alt=""
                    @error="markBroken(entry.thumbnailUrl!)"
                  />
                  <img v-else class="type-icon" :src="fileIconUrl(entry)" alt="" draggable="false" />
                </td>
                <td class="list-name">{{ entry.name }}</td>
                <td class="x-muted">{{ formatDate(entry.modifiedAt) }}</td>
                <td class="x-muted size">{{ entry.kind === 'file' ? formatBytes(entry.sizeBytes) : '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="status x-muted">
          <span>{{ loading ? 'Loading…' : summary }}</span>
          <span v-if="notice" class="notice">{{ notice }}</span>
        </footer>
      </section>

      <div v-if="dialog" class="scrim" @click.self="dialog = null">
        <form class="x-card dialog" @submit.prevent="confirmDialog">
          <template v-if="dialog.kind === 'delete'">
            <h2 class="x-title">Delete “{{ dialog.entry.name }}”?</h2>
            <p class="x-muted">
              {{ dialog.entry.kind === 'directory' ? 'The folder and everything in it are' : 'The file is' }} deleted for good; the phone has no bin.
            </p>
          </template>
          <template v-else>
            <h2 class="x-title">{{ dialog.kind === 'rename' ? 'Rename' : 'New folder' }}</h2>
            <input ref="dialogField" v-model="dialog.text" class="x-input" spellcheck="false" autocomplete="off" @keydown.escape="dialog = null" />
          </template>
          <div class="actions">
            <button type="button" class="x-button" @click="dialog = null">Cancel</button>
            <button type="submit" class="x-button primary" :class="{ danger: dialog.kind === 'delete' }">
              {{ dialog.kind === 'delete' ? 'Delete' : dialog.kind === 'rename' ? 'Rename' : 'Create' }}
            </button>
          </div>
        </form>
      </div>
    </template>
  </main>
</template>

<style scoped>
.files {
  display: flex;
  height: 100%;
}

.access {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.access .x-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16px;
  max-width: 720px;
  padding: 36px;
}

.access p {
  margin: 0;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 260px;
  flex: none;
  padding: 20px 12px;
  border-right: 1px solid var(--border);
  overflow-y: auto;
}

.root {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 56px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: var(--radius-small);
  background: none;
  text-align: left;
  cursor: pointer;
}

.root:hover {
  background: var(--surface-hover);
}

.root.active {
  border-color: var(--accent);
  color: var(--accent-strong);
}

.root-icon {
  color: var(--text-muted);
}

.root.active .root-icon {
  color: inherit;
}

.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
}

.toolbar .x-button:disabled {
  opacity: 0.35;
  cursor: default;
}

.crumbs {
  flex: 1;
  display: flex;
  align-items: center;
  min-width: 0;
  margin: 0 8px;
  padding: 0;
  list-style: none;
  overflow: hidden;
  white-space: nowrap;
}

.crumbs li + li::before {
  content: '›';
  margin: 0 6px;
  color: var(--text-muted);
}

.crumbs button {
  padding: 6px 8px;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--text-muted);
  cursor: pointer;
}

.crumbs button:hover {
  background: var(--surface-hover);
}

.crumbs button.current {
  color: var(--text);
}

.segmented {
  display: flex;
  border: 1px solid var(--border);
  border-radius: var(--radius-small);
  overflow: hidden;
  flex: none;
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

.listing {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.message {
  margin: 40px auto;
  text-align: center;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(176px, 1fr));
  gap: 16px;
}

.tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-small);
  background: none;
  cursor: pointer;
}

.tile:hover {
  border-color: var(--accent);
  background: var(--surface);
}

.thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 10px;
  background: var(--surface);
  overflow: hidden;
}

.thumb {
  position: relative;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.play {
  position: absolute;
  right: 8px;
  bottom: 8px;
  display: grid;
  place-items: center;
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.65);
  font-size: var(--font-size-small);
}

.thumb img.thumb-icon {
  width: 72px;
  height: 72px;
  object-fit: contain;
}

.tile-name {
  width: 100%;
  font-size: var(--font-size-small);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-small);
}

.list tr {
  cursor: pointer;
}

.list tr:hover td {
  background: var(--surface-hover);
}

.list td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

.list-icon {
  width: 64px;
  font-size: 28px;
  text-align: center;
}

.list-icon img {
  display: block;
  width: 56px;
  height: 42px;
  border-radius: 6px;
  object-fit: cover;
}

.list-icon img.type-icon {
  width: 40px;
  height: 40px;
  margin: 1px 8px;
  object-fit: contain;
}

.list-name {
  width: 100%;
  max-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text);
  font-size: var(--font-size);
}

.size {
  text-align: right;
}

.status {
  display: flex;
  justify-content: space-between;
  gap: var(--gap);
  padding: 10px 20px;
  border-top: 1px solid var(--border);
  font-size: var(--font-size-small);
}

.notice {
  color: var(--pinned);
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
  width: 560px;
  padding: 32px;
}

.dialog .x-title,
.dialog p {
  margin: 0;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
