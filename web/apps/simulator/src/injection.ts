import { inject, type InjectionKey } from 'vue'
import type { Bridge } from './shell/bridge'
import type { SimMedia } from './shell/media'
import type { Shell } from './shell/shell'

export interface Simulator {
  shell: Shell
  bridge: Bridge
  media: SimMedia
  appsOrigin: string
  bootError: string | null
}

export const SIMULATOR: InjectionKey<Simulator> = Symbol('simulator')

export function useSimulator(): Simulator {
  const simulator = inject(SIMULATOR)
  if (!simulator) throw new Error('simulator not provided')
  return simulator
}
