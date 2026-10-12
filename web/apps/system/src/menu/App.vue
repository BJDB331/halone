<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import type { DialogAnswer, MenuRequest } from '@halone/sdk'
import ColorPicker from './ColorPicker.vue'
import DateTimePicker from './DateTimePicker.vue'
import FilePicker from './FilePicker.vue'
import { useHost } from '../shared/host'
import Icon from '../shared/Icon.vue'

// Menus keep MenuLayout's measures and dialogs DialogLayout's (core-shell),
// which size this panel before it draws.

const { halone } = useHost()
const menu = ref<MenuRequest | null>(null)
const list = ref<HTMLElement>()
const firstField = ref<HTMLInputElement>()
const text = ref('')
const username = ref('')
const password = ref('')
const selected = ref(new Set<string>())
const paths = ref<string[]>([])

const dialog = computed(() => menu.value?.dialog ?? null)

async function show(request: MenuRequest | null) {
  menu.value = request
  if (!request) return
  const spec = request.dialog
  text.value = spec?.value ?? ''
  username.value = spec?.username ?? ''
  password.value = spec?.password ?? ''
  selected.value = new Set(spec?.choices?.flatMap((item) => ('separator' in item || !item.checked ? [] : [item.id])) ?? [])
  paths.value = []
  await nextTick()
  list.value?.querySelector('.checked')?.scrollIntoView({ block: 'center' })
  if (spec && (spec.kind === 'text' || spec.kind === 'login')) {
    firstField.value?.focus()
    firstField.value?.select()
  }
  let reported = false
  const report = () => {
    if (reported) return
    reported = true
    halone.menu.rendered(request.menuId).catch(() => {})
  }
  // A hidden page may get no animation frames at all, so a timer stands in.
  requestAnimationFrame(() => requestAnimationFrame(report))
  setTimeout(report, 50)
}

halone.menu.current().then(show, () => {})
const off = halone.on('menu.changed', show)

function choose(itemId: string | null, answer?: DialogAnswer) {
  const current = menu.value
  if (current) halone.menu.choose(current.menuId, itemId, answer).catch(() => {})
}

function press(buttonId: string) {
  const spec = dialog.value
  if (!spec) return
  const answer: DialogAnswer = {}
  if (spec.kind === 'text' || spec.kind === 'datetime' || spec.kind === 'color') answer.text = text.value
  if (spec.kind === 'login') {
    answer.username = username.value
    answer.password = password.value
  }
  if (spec.kind === 'choices') answer.selected = [...selected.value]
  if (spec.kind === 'files') answer.paths = paths.value
  choose(buttonId, answer)
}

const primary = computed(() => dialog.value?.buttons.find((button) => button.primary) ?? dialog.value?.buttons.at(-1))

function toggle(id: string) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape') choose(null)
  else if (event.key === 'Enter' && dialog.value && primary.value && dialog.value.kind !== 'files') {
    event.preventDefault()
    press(primary.value.id)
  }
}
document.addEventListener('keydown', onKey)
onBeforeUnmount(() => {
  off()
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <form v-if="dialog" class="dialog" :class="dialog.kind" @submit.prevent="primary && press(primary.id)" @contextmenu.prevent>
    <h1 v-if="dialog.title">{{ dialog.title }}</h1>
    <p v-if="dialog.message" class="message">{{ dialog.message }}</p>

    <input v-if="dialog.kind === 'text'" ref="firstField" v-model="text" class="x-input" spellcheck="false" autocomplete="off" />

    <template v-else-if="dialog.kind === 'login'">
      <input v-if="dialog.username !== null" ref="firstField" v-model="username" class="x-input" placeholder="User name" autocomplete="off" />
      <input
        :ref="dialog.username === null ? 'firstField' : undefined"
        v-model="password"
        class="x-input"
        type="password"
        placeholder="Password"
        autocomplete="off"
      />
    </template>

    <ul v-else-if="dialog.kind === 'choices'" ref="list" class="choices">
      <template v-for="(item, index) in dialog.choices ?? []" :key="index">
        <li
          v-if="!('separator' in item)"
          class="choice"
          :class="{ disabled: item.disabled, checked: selected.has(item.id) }"
          @click="!item.disabled && toggle(item.id)"
        >
          <span class="box"><Icon v-if="selected.has(item.id)" name="check" /></span>
          {{ item.label }}
        </li>
      </template>
    </ul>

    <DateTimePicker
      v-else-if="dialog.kind === 'datetime'"
      :key="menu!.menuId"
      :type="dialog.inputType ?? 'date'"
      :value="dialog.value ?? ''"
      :min="dialog.min"
      :max="dialog.max"
      :step="dialog.step"
      @update:value="text = $event"
    />

    <ColorPicker v-else-if="dialog.kind === 'color'" :key="menu!.menuId" :value="dialog.value ?? ''" :swatches="dialog.swatches ?? []" @update:value="text = $event" />

    <FilePicker v-else-if="dialog.kind === 'files'" :accept="dialog.accept ?? []" :multiple="!!dialog.multiple" @update:paths="paths = $event" />

    <div class="buttons">
      <button
        v-for="button in dialog.buttons"
        :key="button.id"
        :type="button === primary ? 'submit' : 'button'"
        class="x-button"
        :class="{ primary: button.primary, danger: button.danger }"
        :disabled="dialog.kind === 'files' && button === primary && !paths.length"
        @click.prevent="press(button.id)"
      >
        {{ button.label }}
      </button>
    </div>
  </form>

  <ul v-else-if="menu" ref="list" class="menu" role="menu" @contextmenu.prevent>
    <template v-for="(item, index) in menu.items" :key="index">
      <li v-if="'separator' in item" class="separator" role="separator" />
      <li
        v-else
        class="item"
        :class="{ disabled: item.disabled, danger: item.danger, checked: item.checked }"
        role="menuitem"
        :aria-disabled="item.disabled"
        @click="!item.disabled && choose(item.id)"
      >
        {{ item.label }}
      </li>
    </template>
  </ul>
</template>

<style scoped>
.menu {
  height: 100vh;
  margin: 0;
  padding: 8px 0;
  list-style: none;
  overflow-y: auto;
  background: var(--surface-raised);
  border: 1px solid var(--border);
  border-radius: 14px;
  font-size: 20px;
}

.item {
  position: relative;
  height: 48px;
  margin: 0 8px;
  padding: 0 16px;
  border-radius: 10px;
  line-height: 48px;
  white-space: pre;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.item.checked {
  color: var(--accent);
}

.item:hover:not(.disabled) {
  background: var(--surface-hover);
}

.item:active:not(.disabled) {
  background: var(--border);
}

.item.disabled {
  color: var(--text-muted);
  opacity: 0.55;
  cursor: default;
}

.item.danger {
  color: var(--danger);
}

.separator {
  height: 1px;
  margin: 8px 16px;
  background: var(--border);
}

.dialog {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: 100vh;
  padding: 32px;
  background: var(--surface-raised);
  border: 1px solid var(--border);
  border-radius: 14px;
  font-size: 22px;
}

h1 {
  height: 52px;
  margin: 0;
  overflow: hidden;
  font-size: 26px;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.message {
  max-height: 320px;
  margin: 0;
  overflow-y: auto;
  line-height: 31px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.dialog .x-input {
  flex: none;
  height: 60px;
  margin-top: 16px;
}

.choices {
  flex: 1;
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
}

.choice {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 52px;
  padding: 0 8px;
  border-radius: 10px;
  white-space: pre;
  cursor: pointer;
}

.choice:hover:not(.disabled) {
  background: var(--surface-hover);
}

.choice.disabled {
  color: var(--text-muted);
  cursor: default;
}

.box {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 2px solid var(--border);
  border-radius: 8px;
  color: var(--accent);
  font-size: 18px;
}

.choice.checked .box {
  border-color: var(--accent);
}

.buttons {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: auto;
  padding-top: 24px;
}
</style>
