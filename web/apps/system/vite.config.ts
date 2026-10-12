import { resolve } from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const apps = ['dock', 'launcher', 'browser', 'menu', 'library', 'settings', 'about', 'files', 'images', 'video']

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: Object.fromEntries(apps.map((app) => [app, resolve(import.meta.dirname, app, 'index.html')])),
    },
  },
})
