import { createApp } from 'vue'
import '@halone/ui/theme.css'
import './simulator.css'
import type { AppInfo } from '@halone/sdk'
import App from './App.vue'
import { Bridge } from './shell/bridge'
import { SimFiles } from './shell/files'
import { SimMedia } from './shell/media'
import { Shell } from './shell/shell'
import { SimBrowsing } from './shell/browsing'
import { SimWebApps } from './shell/webapps'
import { SimPackageApps } from './shell/packages'
import { loadSettings } from './shell/settings'
import { readPreference, writePreference } from './preferences'
import { SIMULATOR, type Simulator } from './injection'
import { listenForDeveloper } from './shell/developer'

interface BatteryManager extends EventTarget {
  level: number
  charging: boolean
}

const appsOrigin = import.meta.env.VITE_APPS_ORIGIN ?? 'http://127.0.0.1:5174'

async function loadApps(): Promise<AppInfo[]> {
  const response = await fetch(new URL('/apps.json', appsOrigin))
  if (!response.ok) throw new Error(`apps.json: HTTP ${response.status}`)
  return response.json()
}

async function boot() {
  let apps: AppInfo[] = []
  let bootError: string | null = null
  try {
    apps = await loadApps()
  } catch (error) {
    bootError = `Could not load system apps from ${appsOrigin} (${error instanceof Error ? error.message : error}). Is the system app server running?`
  }

  const shell = new Shell({
    apps,
    appsOrigin,
    settings: loadSettings(readPreference('settings')),
    persistSettings: (settings) => writePreference('settings', JSON.stringify(settings)),
    browsing: new SimBrowsing({ load: readPreference, save: writePreference }),
    webApps: new SimWebApps({ load: readPreference, save: writePreference }),
    packageApps: new SimPackageApps({ load: readPreference, save: writePreference }),
  })
  const media = new SimMedia()
  const bridge = new Bridge(shell, appsOrigin, new SimFiles(), media)
  const simulator: Simulator = { shell, bridge, media, appsOrigin, bootError }

  Object.assign(window, { __halone: simulator })
  shell.openOverlays()
  const battery = await (navigator as Navigator & { getBattery?: () => Promise<BatteryManager> }).getBattery?.().catch(() => undefined)
  if (battery) {
    const report = () => shell.setBattery({ level: battery.level, charging: battery.charging })
    report()
    battery.addEventListener('levelchange', report)
    battery.addEventListener('chargingchange', report)
  }

  let last = performance.now()
  const frame = (now: number) => {
    shell.tick(Math.min((now - last) / 1000, 0.1))
    last = now
    requestAnimationFrame(frame)
  }
  requestAnimationFrame(frame)

  listenForDeveloper(shell)
  createApp(App).provide(SIMULATOR, simulator).mount('#app')
}

void boot()
