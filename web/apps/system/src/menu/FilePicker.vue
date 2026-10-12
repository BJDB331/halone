<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { mimeMatches, parentPath, type FileEntry, type FileRoot } from '@halone/sdk'
import { byName, formatBytes } from '../shared/format'
import { errorMessage, useHost } from '../shared/host'
import Icon from '../shared/Icon.vue'
import { fileIconUrl } from '../shared/fileIcons'

const props = defineProps<{ accept: string[]; multiple: boolean }>()
const emit = defineEmits<{ 'update:paths': [paths: string[]] }>()

const { halone } = useHost()
const granted = ref<boolean | null>(null)
const roots = ref<FileRoot[]>([])
const path = ref<string | null>(null)
const entries = ref<FileEntry[]>([])
const failure = ref<string | null>(null)
const chosen = ref<string[]>([])

async function start() {
  try {
    granted.value = (await halone.files.access()).granted
    if (!granted.value) return
    roots.value = await halone.files.roots()
    path.value = roots.value.find((root) => root.kind === 'downloads')?.path ?? roots.value[0]?.path ?? null
  } catch (caught) {
    failure.value = errorMessage(caught)
  }
}
void start()
const off = halone.on('files.accessChanged', ({ granted: now }) => now && start())
onBeforeUnmount(off)

watch(path, async (current) => {
  if (!current) return
  failure.value = null
  try {
    entries.value = await halone.files.list(current)
  } catch (caught) {
    entries.value = []
    failure.value = errorMessage(caught)
  }
})

/** What the page accepts, as `<input accept>` writes it: MIME types, `type/*`, or extensions. */
function accepted(entry: FileEntry): boolean {
  if (!props.accept.length) return true
  const name = entry.name.toLowerCase()
  return props.accept.some((pattern) => {
    const rule = pattern.trim().toLowerCase()
    if (!rule) return true
    if (rule.startsWith('.')) return name.endsWith(rule)
    return mimeMatches(rule, entry.mimeType)
  })
}

const shown = computed(() => {
  const folders = entries.value.filter((entry) => entry.kind === 'directory').sort(byName)
  const files = entries.value.filter((entry) => entry.kind === 'file' && accepted(entry)).sort(byName)
  return [...folders, ...files]
})

const root = computed(() =>
  roots.value.filter((r) => path.value === r.path || path.value?.startsWith(r.path.replace(/\/$/, '') + '/')).sort((a, b) => b.path.length - a.path.length)[0],
)

function pick(entry: FileEntry) {
  if (entry.kind === 'directory') {
    path.value = entry.path
    return
  }
  const next = props.multiple
    ? chosen.value.includes(entry.path)
      ? chosen.value.filter((p) => p !== entry.path)
      : [...chosen.value, entry.path]
    : [entry.path]
  chosen.value = next
  emit('update:paths', next)
}

function up() {
  const parent = path.value && parentPath(path.value)
  if (parent && root.value && parent.length >= root.value.path.replace(/\/$/, '').length) path.value = parent
}
</script>

<template>
  <div class="picker">
    <div v-if="granted === false" class="empty">
      <p>halone needs permission to read the phone's storage.</p>
      <button type="button" class="x-button" @click="halone.files.requestAccess()">Open the setting on the phone</button>
    </div>
    <template v-else>
      <nav>
        <button v-for="candidate in roots" :key="candidate.path" type="button" :class="{ active: root?.path === candidate.path }" @click="path = candidate.path">
          {{ candidate.name }}
        </button>
      </nav>
      <section>
        <header>
          <button type="button" class="x-button small" :disabled="!root || path === root.path" aria-label="Up" @click="up"><Icon name="arrow_upward" /></button>
          <span class="where">{{ path }}</span>
          <span v-if="chosen.length" class="x-muted">{{ chosen.length }} chosen</span>
        </header>
        <p v-if="failure" class="empty">{{ failure }}</p>
        <p v-else-if="!shown.length" class="empty x-muted">Nothing here the page accepts.</p>
        <ul v-else>
          <li v-for="entry in shown" :key="entry.path" :class="{ chosen: chosen.includes(entry.path) }" @click="pick(entry)">
            <img v-if="entry.thumbnailUrl" :src="entry.thumbnailUrl" alt="" loading="lazy" />
            <img v-else class="icon" :src="fileIconUrl(entry)" alt="" draggable="false" />
            <span class="name">{{ entry.name }}</span>
            <span class="x-muted size">{{ entry.kind === 'file' ? formatBytes(entry.sizeBytes) : '' }}</span>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.picker {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  margin-top: 16px;
}

nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 200px;
  flex: none;
}

nav button {
  padding: 10px 14px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

nav button.active,
nav button:hover {
  background: var(--surface-hover);
}

section {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.where {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 18px;
}

ul {
  flex: 1;
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
}

li {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 56px;
  padding: 0 10px;
  border-radius: 10px;
  cursor: pointer;
}

li:hover {
  background: var(--surface-hover);
}

li.chosen {
  background: var(--border);
  color: var(--accent);
}

li img {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
}

li img.icon {
  object-fit: contain;
}

.name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.size {
  font-size: 18px;
}

.empty {
  margin: 40px auto;
  text-align: center;
}
</style>
