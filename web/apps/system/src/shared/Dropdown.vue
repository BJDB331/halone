<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import Icon from './Icon.vue'

/**
 * A native <select> opens an Android popup window, which a WebView living in a
 * Presentation on a virtual display has nowhere to show; this stays in the page.
 */
const props = defineProps<{ modelValue: string; options: { value: string; label: string }[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const open = ref(false)
const upward = ref(false)
const root = ref<HTMLElement>()

const OPTION_HEIGHT_PX = 52

function toggle() {
  if (!open.value && root.value) {
    const below = window.innerHeight - root.value.getBoundingClientRect().bottom
    upward.value = below < props.options.length * OPTION_HEIGHT_PX + 24
  }
  open.value = !open.value
}

const current = computed(() => props.options.find((o) => o.value === props.modelValue)?.label ?? props.modelValue)

function choose(value: string) {
  open.value = false
  if (value !== props.modelValue) emit('update:modelValue', value)
}

function onOutside(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}

onMounted(() => document.addEventListener('pointerdown', onOutside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutside))
</script>

<template>
  <div ref="root" class="dropdown" :class="{ open }">
    <button class="x-button trigger" type="button" @click="toggle">
      <span>{{ current }}</span>
      <Icon class="chevron" name="expand_more" />
    </button>
    <ul v-if="open" class="menu" :class="{ upward }" role="listbox">
      <li
        v-for="option in options"
        :key="option.value"
        role="option"
        :aria-selected="option.value === modelValue"
        :class="{ selected: option.value === modelValue }"
        @click="choose(option.value)"
      >
        {{ option.label }}
      </li>
    </ul>
  </div>
</template>

<style scoped>
.dropdown {
  position: relative;
  min-width: 300px;
}

.trigger {
  width: 100%;
  justify-content: space-between;
}

.chevron {
  color: var(--text-muted);
}

.menu {
  position: absolute;
  z-index: 10;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  margin: 0;
  padding: 6px;
  list-style: none;
  border: 1px solid var(--accent);
  border-radius: var(--radius-small);
  background: var(--surface-raised);
}

.menu.upward {
  top: auto;
  bottom: calc(100% + 6px);
}

.menu li {
  padding: 14px 16px;
  border-radius: 8px;
  cursor: pointer;
}

.menu li:hover {
  background: var(--surface-hover);
}

.menu li.selected {
  color: var(--accent-strong);
}
</style>
