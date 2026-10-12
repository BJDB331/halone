import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { filesPlugin } from './server/files'
import { webAppsPlugin } from './server/webapps'
import { devPlugin } from './server/dev'
import { socketsPlugin } from './server/sockets'

export default defineConfig({
  plugins: [vue(), filesPlugin(), webAppsPlugin(), devPlugin(), socketsPlugin()],
})
