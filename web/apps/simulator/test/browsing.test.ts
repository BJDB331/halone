import { describe, expect, it } from 'vitest'
import type { AppInfo } from '@halone/sdk'
import { freeName } from '../server/files'
import { SimBrowsing } from '../src/shell/browsing'
import { Shell, menuSize } from '../src/shell/shell'
import { DEFAULT_SETTINGS } from '../src/shell/settings'

const app = (id: string): AppInfo => ({ id, name: id, description: '', icon: '', entry: `/${id}/`, widthPx: 1280, heightPx: 800 })

function shell() {
  return new Shell({ apps: [app('files'), app('settings')], appsOrigin: 'http://apps.test', settings: { ...DEFAULT_SETTINGS } })
}

describe('context menus', () => {
  it('answer with the item chosen, once the menu page has drawn them', async () => {
    const s = shell()
    const files = s.launch('files')
    const answer = s.showMenu(files.id, [{ id: 'copy', label: 'Copy' }], 10, 20)
    expect(s.state.menu?.shown).toBe(false)
    s.menuRendered(s.state.menu!.menuId)
    expect(s.state.menu?.shown).toBe(true)
    s.chooseMenu(s.state.menu!.menuId, 'copy')
    expect(await answer).toBe('copy')
    expect(s.state.menu).toBeNull()
  })

  it('dismiss the last menu when another opens, and when their window closes', async () => {
    const s = shell()
    const files = s.launch('files')
    const first = s.showMenu(files.id, [{ id: 'a', label: 'A' }], 0, 0)
    const second = s.showMenu(files.id, [{ id: 'b', label: 'B' }], 0, 0)
    expect(await first).toBeNull()
    s.close(files.id)
    expect(await second).toBeNull()
  })

  it('refuse a disabled item', () => {
    const s = shell()
    const files = s.launch('files')
    void s.showMenu(files.id, [{ id: 'paste', label: 'Paste', disabled: true }], 0, 0)
    expect(() => s.chooseMenu(s.state.menu!.menuId, 'paste')).toThrow(/no such item/)
  })

  it('are sized as MenuLayout sizes them', () => {
    expect(menuSize([{ id: 'a', label: 'Copy' }])).toEqual({ widthPx: 240, heightPx: 64 })
    expect(menuSize([{ id: 'a', label: 'Copy' }, { separator: true }, { id: 'b', label: 'Paste' }]).heightPx).toBe(16 + 48 + 17 + 48)
  })
})

describe('the clipboard', () => {
  it('keeps files only while its text is theirs', () => {
    const s = shell()
    s.writeClipboardFiles({ paths: ['/a', '/b'], cut: true })
    expect(s.readClipboard().files).toEqual({ paths: ['/a', '/b'], cut: true })
    s.writeClipboardText('hello')
    expect(s.readClipboard()).toEqual({ text: 'hello', files: null })
  })

  it('forgets cut files once they are moved', () => {
    const s = shell()
    s.writeClipboardFiles({ paths: ['/a'], cut: true })
    s.filesMoved(['/a'])
    expect(s.readClipboard().files).toBeNull()
  })
})

describe('routes', () => {
  it('reach a new window and a running one', () => {
    const s = shell()
    const routed: (string | undefined)[] = []
    s.on('routed', (id) => routed.push(id))
    const window = s.launch('settings', 'browser')
    expect(window.route).toBe('browser')
    s.launch('settings', 'gestures')
    expect(window.route).toBe('gestures')
    expect(routed).toEqual([window.id])
  })
})

describe('browsing', () => {
  it('records ordinary tabs in history and leaves private ones out', () => {
    const s = shell()
    const web = s.openUrl('https://a.example/')
    s.newTab(web.id, 'https://secret.example/', true)
    s.newTab(web.id, 'https://b.example/')
    expect(s.browsing.listHistory().map((entry) => entry.url)).toEqual(['https://b.example/', 'https://a.example/'])
  })

  it('opens a page from elsewhere in the web window there is', () => {
    const s = shell()
    const web = s.openUrl('https://a.example/')
    const opened = s.openInBrowser('https://b.example/', true)
    expect(opened.id).toBe(web.id)
    const state = s.browserState(web.id)
    expect(state.tabs.at(-1)).toMatchObject({ url: 'https://b.example/', private: true })
  })

  it('shows a page as bookmarked once it is', () => {
    const s = shell()
    const web = s.openUrl('https://a.example/')
    s.toggleBookmark(web.id)
    expect(s.browserState(web.id).bookmarked).toBe(true)
    s.toggleBookmark(web.id)
    expect(s.browsing.bookmarks).toEqual([])
  })

  it('keeps history and bookmarks across a restart', () => {
    const saved = new Map<string, string>()
    const store = { load: (key: string) => saved.get(key) ?? null, save: (key: string, value: string) => saved.set(key, value) }
    const first = new SimBrowsing(store)
    first.visited('https://a.example/', 'A')
    const bookmark = first.addBookmark('https://a.example/', 'A')
    const again = new SimBrowsing(store)
    expect(again.listHistory()[0]?.title).toBe('A')
    expect(again.addBookmark('https://b.example/', 'B').id).not.toBe(bookmark.id)
  })
})

describe('freeName', () => {
  it('numbers a taken name before its extension', () => {
    expect(freeName(new Set(['a.jpg']), 'a.jpg')).toBe('a (2).jpg')
    expect(freeName(new Set(['a.jpg', 'a (2).jpg']), 'a.jpg')).toBe('a (3).jpg')
    expect(freeName(new Set(['notes']), 'notes')).toBe('notes (2)')
    expect(freeName(new Set(), 'b.txt')).toBe('b.txt')
  })
})
