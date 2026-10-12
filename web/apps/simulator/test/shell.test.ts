import { describe, expect, it } from 'vitest'
import type { AppInfo } from '@halone/sdk'
import { Shell, type SimWindow } from '../src/shell/shell'
import { DEFAULT_SETTINGS, loadSettings } from '../src/shell/settings'
import { declaredLimits, defaultLimits } from '../src/shell/limits'
import { angularHalfHeightDegrees, horizontalFovDegrees, perspectivePx, pointerAngles, wrapDegrees } from '../src/shell/geometry'

const app = (id: string): AppInfo => ({ id, name: id, description: '', icon: '', entry: `/${id}/`, widthPx: 1280, heightPx: 800 })

function shell(settings = {}) {
  return new Shell({
    apps: [app('files'), app('settings'), app('about')],
    appsOrigin: 'http://apps.test',
    settings: { ...DEFAULT_SETTINGS, ...settings },
  })
}

function settle(s: Shell, seconds = 20) {
  for (let t = 0; t < seconds; t += 1 / 60) s.tick(1 / 60)
}

const yaws = (s: Shell) => s.state.windows.map((w) => Math.round(w.yawDegrees))
const sorted = (values: number[]) => [...values].sort((a, b) => a - b)

function pointAt(s: Shell, window: SimWindow) {
  const offset = ((window.yawDegrees - s.state.head.yawDegrees) * Math.PI) / 180
  s.setPointer(perspectivePx * Math.tan(offset), 0)
}

describe('geometry', () => {
  it('sizes an 800px panel as the device does, 75% of the 19° sizing field', () => {
    expect(2 * angularHalfHeightDegrees(800)).toBeCloseTo(14.25, 2)
  })

  it('shows the calibrated field, about 39° across', () => {
    expect(horizontalFovDegrees).toBeCloseTo(39.26, 0)
  })

  it('wraps angles into (-180, 180]', () => {
    expect(wrapDegrees(190)).toBe(-170)
    expect(wrapDegrees(-180)).toBe(180)
    expect(wrapDegrees(540)).toBe(180)
  })

  it('aims the pointer where the display shows it', () => {
    expect(pointerAngles(30, 10, 0, 0).yawDegrees).toBeCloseTo(30)
    expect(pointerAngles(30, 10, 0, 0).pitchDegrees).toBeCloseTo(10)
    expect(pointerAngles(30, 0, perspectivePx, 0).yawDegrees).toBeCloseTo(75)
    expect(pointerAngles(0, 0, 0, -perspectivePx).pitchDegrees).toBeCloseTo(45)
  })
})

describe('shell', () => {
  it('resolves app entries against the apps origin', () => {
    expect(shell().launch('settings').url).toBe('http://apps.test/settings/')
  })

  it('keeps apps single-instance', () => {
    const s = shell()
    const first = s.launch('settings')
    s.launch('about')
    expect(s.launch('settings').id).toBe(first.id)
    expect(s.state.windows).toHaveLength(2)
  })

  it('spreads followers evenly around the anchor', () => {
    const s = shell()
    s.launch('files')
    s.launch('settings')
    s.launch('about')
    settle(s)
    expect(sorted(yaws(s))).toEqual([-30, 0, 30])
  })

  it('appends a new window to the layout, leaving focus with the pointer', () => {
    const s = shell()
    const launcher = s.launch('files')
    s.launch('settings')
    settle(s)
    s.launch('about')
    expect(s.state.windows.map((w) => w.appId)).toEqual(['files', 'settings', 'about'])
    expect(s.state.focusedId).toBe(launcher.id)
  })

  it('stays world-locked inside the band', () => {
    const s = shell()
    s.launch('files')
    s.setHead(10, 0)
    settle(s)
    expect(s.state.anchorYawDegrees).toBe(0)
  })

  it('brings the arrangement back once the head leaves the band', () => {
    const s = shell()
    s.launch('files')
    s.setHead(60, 0)
    settle(s)
    expect(s.state.anchorYawDegrees).toBeCloseTo(60, 0)
    expect(s.state.returning).toBe(false)
  })

  it('catches up briskly on a turn but drifts slowly just past the band', () => {
    const turned = shell()
    turned.launch('files')
    turned.setHead(60, 0)
    settle(turned, 1)
    expect(turned.state.anchorYawDegrees).toBeCloseTo(60, 0)

    const drifted = shell()
    drifted.launch('files')
    drifted.setHead(25, 0)
    settle(drifted, 1)
    expect(drifted.state.returning).toBe(true)
    expect(drifted.state.anchorYawDegrees).toBeLessThan(15)
  })

  it('brings back the window last looked at on a catch-up, not the gap between two', () => {
    const s = shell()
    s.launch('files')
    const settings = s.launch('settings')
    settle(s)
    pointAt(s, settings)
    s.clearPointer()
    s.setHead(70, 0)
    settle(s)
    expect(Math.abs(settings.yawDegrees - 70)).toBeLessThan(1.5)
  })

  it('centres the window nearest the gaze on a drift', () => {
    const s = shell()
    const launcher = s.launch('files')
    const settings = s.launch('settings')
    settle(s)
    pointAt(s, launcher)
    s.clearPointer()
    const past = wrapDegrees(settings.yawDegrees + 25 * Math.sign(settings.yawDegrees))
    s.setHead(past, 0)
    settle(s)
    expect(Math.abs(wrapDegrees(settings.yawDegrees - past))).toBeLessThan(1.5)
  })

  it('waits for the head to come to rest before drifting', () => {
    const s = shell()
    s.launch('files')
    s.launch('settings')
    settle(s)
    let head = 40
    for (let t = 0; t < 0.25; t += 1 / 60) {
      s.setHead((head += 40 / 60), 0)
      s.tick(1 / 60)
    }
    expect(s.state.returning).toBe(false)
    settle(s, 0.2)
    expect(s.state.returning).toBe(false)
    settle(s, 0.2)
    expect(s.state.returning).toBe(true)
  })

  it('goes back where it was when the wearer looks back', () => {
    const s = shell()
    s.launch('files')
    s.setHead(60, 0)
    settle(s, 0.1)
    expect(s.state.anchorYawDegrees).toBeGreaterThan(5)
    s.setHead(0, 0)
    settle(s)
    expect(s.state.anchorYawDegrees).toBeCloseTo(0, 0)
  })

  it('holds focus while panels sweep under the pointer', () => {
    const s = shell()
    const launcher = s.launch('files')
    const settings = s.launch('settings')
    settle(s)
    pointAt(s, settings)
    s.setHead(70, 0)
    settle(s, 0.1)
    pointAt(s, launcher)
    expect(s.state.focusedId).toBe(settings.id)
    settle(s)
    pointAt(s, launcher)
    expect(s.state.focusedId).toBe(launcher.id)
  })

  it('focuses the window under the head in gaze mode', () => {
    const s = shell({ 'input.pointer': 'gaze' })
    s.launch('files')
    const settings = s.launch('settings')
    settle(s)
    s.setHead(settings.yawDegrees, 0)
    s.tick(1 / 60)
    expect(s.state.focusedId).toBe(settings.id)
  })

  it('parks a follower clear of a pinned window unless overlap is allowed', () => {
    const parked = (policy: string) => {
      const s = shell({ 'layout.collisionPolicy': policy })
      const launcher = s.launch('files')
      const settings = s.launch('settings')
      settle(s)
      s.setPinned(launcher.id, true)
      settle(s)
      return Math.round(Math.abs(settings.yawDegrees))
    }
    expect(parked('overlap')).toBe(0)
    expect(parked('avoid-pinned')).toBeGreaterThanOrEqual(15)
  })

  it('re-lays out when the slot spacing changes', () => {
    const s = shell()
    s.launch('files')
    s.launch('settings')
    s.launch('about')
    settle(s)
    s.setSetting('layout.slotSpacingDegrees', 40)
    settle(s)
    expect(sorted(yaws(s))).toEqual([-40, 0, 40])
  })

  it('does not follow when following is off', () => {
    const s = shell({ 'follow.enabled': false })
    s.launch('files')
    s.setHead(90, 0)
    settle(s)
    expect(s.state.anchorYawDegrees).toBe(0)
  })

  it('leaves a pinned window in place and re-lays out the rest', () => {
    const s = shell()
    const launcher = s.launch('files')
    s.launch('settings')
    settle(s)
    const place = launcher.yawDegrees
    s.setPinned(launcher.id, true)
    s.setHead(120, 0)
    settle(s)
    expect(launcher.yawDegrees).toBeCloseTo(place)
    expect(Math.round(s.find('w2').yawDegrees)).toBe(120)
  })

  it('keeps a hand-placed follower at its offset from the anchor', () => {
    const s = shell()
    s.launch('files')
    const settings = s.launch('settings')
    settle(s)
    const placed = settings.yawDegrees - 25
    s.beginGrab(settings.id)
    s.moveGrab(-25, 5)
    s.endGrab()
    expect(settings.handPlaced).toBe(true)
    expect(settings.slotDegrees).toBeCloseTo(placed)
    s.setHead(100, 0)
    settle(s)
    expect(Math.round(settings.yawDegrees)).toBe(Math.round(s.state.anchorYawDegrees + placed))
    expect(settings.pitchDegrees).toBe(5)
  })

  it('treats a press on the title bar that never moves as a click, not a placement', () => {
    const s = shell()
    s.launch('files')
    const settings = s.launch('settings')
    settle(s)
    const yaw = settings.yawDegrees
    s.beginGrab(settings.id)
    s.moveGrab(0.3, 0.2)
    s.endGrab()
    expect(settings.handPlaced).toBe(false)
    expect(settings.yawDegrees).toBeCloseTo(yaw)
  })

  it('recentring makes the current heading forward and puts the arrangement there', () => {
    const s = shell({ 'follow.enabled': false })
    s.launch('files')
    s.setHead(70, 0)
    s.recenter()
    settle(s)
    expect(s.state.head.yawDegrees).toBe(0)
    expect(yaws(s)).toEqual([0])
  })

  it('focusing a window brings the arrangement round to it, keeping its shape', () => {
    const s = shell({ 'follow.enabled': false })
    s.launch('files')
    s.launch('settings')
    s.launch('about')
    settle(s)
    const far = s.state.windows.find((w) => Math.round(w.yawDegrees) === 30)!
    const gaps = s.state.windows.map((w) => w.yawDegrees - far.yawDegrees)
    s.setHead(-30, 0)
    s.focus(far.id)
    settle(s)
    expect(Math.abs(far.yawDegrees + 30)).toBeLessThan(1.5)
    expect(s.state.focusedId).toBe(far.id)
    s.state.windows.forEach((w, i) => expect(w.yawDegrees - far.yawDegrees).toBeCloseTo(gaps[i]!, 0))
  })

  it('opens the dock and launcher as overlays, the launcher forced open until a window opens', () => {
    const s = new Shell({
      apps: [
        { ...app('dock'), widthPx: 880, heightPx: 104, hidden: true },
        { ...app('launcher'), hidden: true },
        app('settings'),
      ],
      appsOrigin: 'http://apps.test',
      settings: { ...DEFAULT_SETTINGS },
    })
    s.openOverlays()
    settle(s, 1)
    expect(s.state.overlays.map((o) => o.id)).toEqual(['dock', 'launcher'])
    expect(s.state.launcherOpen).toBe(true)
    expect(() => s.launch('launcher')).toThrow(/part of the shell/)

    const settings = s.launch('settings')
    settle(s, 1)
    expect(s.state.launcherOpen).toBe(false)
    const dock = s.state.overlays[0]!
    expect(dock.pitchDegrees).toBeLessThan(settings.pitchDegrees - 8)
    expect(dock.opacity).toBe(1)

    s.setSetting('dock.visibility', 'auto-hide')
    s.setPointer(0, 0)
    settle(s, 1)
    expect(s.state.overlays[0]!.shown).toBe(false)
  })

  it('moves focus to the first window when the focused one closes', () => {
    const s = shell()
    s.launch('files')
    const settings = s.launch('settings')
    settle(s)
    pointAt(s, settings)
    expect(s.state.focusedId).toBe(settings.id)
    s.close(settings.id)
    expect(s.state.focusedId).toBe('w1')
  })

  it('keeps tabs and history in a web window, which keeps its size when the tab row shows', () => {
    const s = shell()
    const web = s.openUrl('https://example.com/')
    settle(s, 1)
    expect(web.chromePx).toBe(76)
    s.navigate(web.id, 'https://halone.dev/a')
    expect(s.browserState(web.id)).toMatchObject({ canGoBack: true, canGoForward: false })
    s.goBack(web.id)
    expect(web.url).toBe('https://example.com/')
    expect(s.browserState(web.id).canGoForward).toBe(true)

    const pitch = web.pitchDegrees
    s.newTab(web.id)
    settle(s, 1)
    expect(web.chromePx).toBe(128)
    expect(web.heightPx).toBe(876)
    expect(web.pitchDegrees).toBe(pitch)
    expect(s.browserState(web.id).tabs).toHaveLength(2)
    expect(web.title).toBe('New tab')

    s.closeTab(web.id, s.browserState(web.id).activeTab)
    expect(web.chromePx).toBe(76)
    expect(web.title).toBe('example.com')
    s.closeTab(web.id, s.browserState(web.id).activeTab)
    expect(s.state.windows).toHaveLength(0)
  })

  it("reads a page's size limits from its meta tags, falling back to the viewport and defaults", () => {
    const defaults = defaultLimits(1024, 800)
    expect(defaults).toEqual({ minWidthPx: 480, minHeightPx: 360, maxWidthPx: 2560, maxHeightPx: 2000 })
    const own = '<meta name="halone:window" content="min-width=700, max-height=1200"><meta name="viewport" content="width=900">'
    expect(declaredLimits(own, defaults)).toEqual({ minWidthPx: 700, minHeightPx: 360, maxWidthPx: 2560, maxHeightPx: 1200 })
    expect(declaredLimits('<meta content="width=900, initial-scale=1" name="viewport">', defaults).minWidthPx).toBe(900)
    expect(declaredLimits('<meta name="viewport" content="width=device-width">', defaults)).toEqual(defaults)
  })

  it('resizes a window within its limits', () => {
    const s = shell()
    const settings = s.launch('settings')
    settle(s, 1)
    s.resizeWindow(settings.id, 5000, 100, 'BOTTOM_RIGHT')
    expect(settings.widthPx).toBe(Math.floor(1280 * 2.5))
    expect(settings.heightPx).toBe(360)
    settle(s, 1)
    expect(settings.pitchDegrees).toBeGreaterThan(0)
  })

  it('only opens http and https URLs', () => {
    const s = shell()
    expect(() => s.openUrl('javascript:alert(1)')).toThrow(/only http and https/)
    expect(() => s.openUrl('not a url')).toThrow(/not a URL/)
    expect(s.openUrl('https://example.com/a').kind).toBe('web')
  })

  it('validates settings and persists accepted ones', () => {
    let saved: unknown = null
    const s = new Shell({ apps: [], appsOrigin: 'http://apps.test', settings: DEFAULT_SETTINGS, persistSettings: (v) => (saved = v) })
    expect(() => s.setSetting('display.brightness', 3)).toThrow(/invalid value/)
    expect(() => s.setSetting('nope', 1)).toThrow(/unknown setting/)
    s.setSetting('follow.marginDegrees', 20)
    expect(saved).toMatchObject({ 'follow.marginDegrees': 20 })
  })

  it('only allows hold-only gestures on hold slots', () => {
    const s = shell()
    expect(() => s.setSetting('gesture.pad.tap1', 'scroll')).toThrow(/invalid value/)
    s.setSetting('gesture.pad.hold1', 'move-window')
    expect(s.state.settings['gesture.pad.hold1']).toBe('move-window')
  })

  it('defaults to clicking on a glasses tap, with double taps off', () => {
    expect(DEFAULT_SETTINGS['gesture.glasses.left.tap']).toBe('click')
    expect(DEFAULT_SETTINGS['gesture.glasses.right.doubleTap']).toBe('disabled')
  })

  it('keeps pinned apps as a list of distinct app ids', () => {
    const s = shell()
    expect(s.state.settings['dock.pinnedApps']).toEqual(['files', 'settings'])
    s.setSetting('dock.pinnedApps', ['about', 'files'])
    expect(s.state.settings['dock.pinnedApps']).toEqual(['about', 'files'])
    expect(() => s.setSetting('dock.pinnedApps', ['a', 'a'])).toThrow(/invalid value/)
    expect(() => s.setSetting('dock.pinnedApps', ['a', 3])).toThrow(/invalid value/)
    expect(() => s.setSetting('dock.pinnedApps', 'settings')).toThrow(/invalid value/)
  })

  it('drops invalid stored settings instead of failing to boot', () => {
    const loaded = loadSettings(JSON.stringify({ 'display.brightness': 9, 'follow.marginDegrees': 20, junk: 1 }))
    expect(loaded['display.brightness']).toBe(DEFAULT_SETTINGS['display.brightness'])
    expect(loaded['follow.marginDegrees']).toBe(20)
    expect(loadSettings('{broken')).toEqual(DEFAULT_SETTINGS)
  })
})
