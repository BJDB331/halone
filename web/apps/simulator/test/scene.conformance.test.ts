import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { SimulatorScene } from '@halone/scene'
import { panelDistancePx, wrapDegrees } from '../src/shell/geometry'

const FIXTURES = new URL('../../../../fixtures/scene/', import.meta.url)
const FPS = 60
const DT = 1 / FPS
const SAMPLE_EVERY = 3
const TOLERANCE_DEGREES = 0.01

type Step = Record<string, unknown>
interface Scenario {
  settings?: Record<string, unknown>
  steps: Step[]
}
type Sample = { f: number; p: Record<string, number[]> }

function play(scenario: Scenario): Sample[] {
  const scene = new SimulatorScene(panelDistancePx)
  const trace: Sample[] = []
  let head = 0
  let frame = 0
  let aim = { yaw: 0, pitch: 0 }

  const pose = (id: unknown) => {
    const panel = scene.panels().find((p) => p.id === id)
    if (!panel) throw new Error(`no panel ${id}`)
    return panel
  }

  const apply = (settings: Record<string, unknown>) => {
    if ('policy' in settings) scene.setCollisionPolicy(settings.policy as string)
    if ('margin' in settings) scene.setFollowMarginDegrees(settings.margin as number)
    if ('catchUp' in settings) scene.setCatchUpSeconds(settings.catchUp as number)
    if ('follow' in settings) scene.setFollowsHead(settings.follow as boolean)
    if ('spacing' in settings) scene.setSlotSpacingDegrees(settings.spacing as number)
  }

  const sample = () => {
    const p: Record<string, number[]> = {}
    for (const panel of scene.panels().sort((a, b) => (a.id < b.id ? -1 : 1))) {
      p[panel.id] = [panel.yawDegrees, panel.pitchDegrees, panel.slotDegrees, +panel.placedByHand, +panel.pinned]
    }
    trace.push({ f: frame, p })
  }

  const frames = (seconds: number, beforeEach: () => void = () => {}) => {
    for (let i = 0; i < Math.trunc(seconds * FPS); i++) {
      beforeEach()
      scene.advance(head, DT)
      frame++
      if (frame % SAMPLE_EVERY === 0) sample()
    }
  }

  if (scenario.settings) apply(scenario.settings)
  for (const step of scenario.steps) {
    if ('open' in step) scene.open(step.open as string, (step.widthPx as number) ?? 1280, (step.heightPx as number) ?? 800)
    else if ('close' in step) scene.close(step.close as string)
    else if ('pin' in step) scene.setPinned(step.pin as string, true)
    else if ('unpin' in step) scene.setPinned(step.unpin as string, false)
    else if ('head' in step) head = wrapDegrees(step.head as number)
    else if ('run' in step) frames(step.run as number)
    else if ('turn' in step) {
      const seconds = step.seconds as number
      const perFrame = (step.turn as number) / Math.trunc(seconds * FPS)
      frames(seconds, () => (head = wrapDegrees(head + perFrame)))
    } else if ('point' in step) {
      const target = pose(step.point)
      scene.hitTest(target.yawDegrees, target.pitchDegrees)
    } else if ('grab' in step) {
      const target = pose(step.grab)
      aim = { yaw: target.yawDegrees, pitch: target.pitchDegrees }
      scene.beginGrab(step.grab as string, aim.yaw, aim.pitch)
    } else if ('drag' in step) {
      const [yaw, pitch] = step.drag as [number, number]
      aim = { yaw: wrapDegrees(aim.yaw + yaw), pitch: aim.pitch + pitch }
      scene.updateGrab(aim.yaw, aim.pitch)
    } else if ('drop' in step) scene.endGrab(head)
    else if ('bringIntoView' in step) scene.bringIntoView(step.bringIntoView as string, head)
    else if ('set' in step) apply(step.set as Record<string, unknown>)
    else throw new Error(`unknown step ${JSON.stringify(step)}`)
    sample()
  }
  return trace
}

function firstDivergence(expected: Sample[], actual: Sample[]): string | null {
  if (expected.length !== actual.length) return `${actual.length} samples, golden has ${expected.length}`
  for (let i = 0; i < expected.length; i++) {
    const want = expected[i]!
    const got = actual[i]!
    const ids = Object.keys(want.p)
    if (ids.join() !== Object.keys(got.p).join()) return `frame ${want.f}: panels ${Object.keys(got.p)}, golden has ${ids}`
    for (const id of ids) {
      const w = want.p[id]!
      const g = got.p[id]!
      const off = w.some((value, k) => Math.abs((k < 3 ? wrapDegrees(g[k]! - value) : g[k]! - value)) > TOLERANCE_DEGREES)
      if (off) return `frame ${want.f} panel ${id}: golden [${w}], got [${g.map((v) => +v.toFixed(4))}]`
    }
  }
  return null
}

const scenarios = JSON.parse(readFileSync(new URL('scenarios.json', FIXTURES), 'utf8')) as Record<string, Scenario>

describe('compiled scene against the JVM goldens', () => {
  for (const [name, scenario] of Object.entries(scenarios)) {
    it(name, () => {
      const golden = JSON.parse(readFileSync(new URL(`golden/${name}.json`, FIXTURES), 'utf8')) as Sample[]
      expect(firstDivergence(golden, play(scenario))).toBeNull()
    })
  }
})
