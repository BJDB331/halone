<script setup lang="ts">
defineProps<{ status: 'connecting' | 'ready' | 'no-host'; error: string | null }>()
</script>

<template>
  <div v-if="status !== 'ready'" class="no-host">
    <template v-if="status === 'connecting'">Connecting…</template>
    <template v-else>
      <strong>Not running inside Halone.</strong>
      <span class="x-muted">{{ error }} Open this app from the simulator at http://127.0.0.1:5173.</span>
    </template>
  </div>
</template>

<style scoped>
.no-host {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: var(--gap);
  padding: var(--gap);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: var(--font-size-small);
}
</style>
