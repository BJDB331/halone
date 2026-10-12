<script setup lang="ts">
import NoHost from '../shared/NoHost.vue'
import SystemPanel from '../shared/SystemPanel.vue'
import { useHost } from '../shared/host'

const { status, error } = useHost()
</script>

<template>
  <NoHost :status="status" :error="error" />
  <main v-if="status === 'ready'" class="about">
    <h1 class="x-title">System</h1>
    <SystemPanel />
  </main>
</template>

<style scoped>
.about {
  height: 100%;
  padding: 40px 48px;
  overflow-y: auto;
}

.x-title {
  margin-bottom: 28px;
}
</style>
