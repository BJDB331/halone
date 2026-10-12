import { HaloneError, type Bookmark, type Download, type HistoryEntry } from '@halone/sdk'

export interface BrowsingStore {
  load(key: string): string | null
  save(key: string, value: string): void
}

const HISTORY_LIMIT = 5000

const recordable = (url: string) => url.startsWith('https://') || url.startsWith('http://')

/**
 * History and bookmarks as `BrowserData.kt` keeps them: history newest first,
 * one entry per address; bookmarks in the order added. The simulator's web
 * windows are iframes, which cannot download, so the download list stays
 * empty here.
 */
export class SimBrowsing {
  history: HistoryEntry[]
  bookmarks: Bookmark[]
  readonly downloads: Download[] = []
  private nextBookmark: number
  private readonly listeners = new Set<(what: 'history' | 'bookmarks') => void>()

  constructor(private readonly store?: BrowsingStore) {
    this.history = parse(store?.load('history')) ?? []
    this.bookmarks = parse(store?.load('bookmarks')) ?? []
    this.nextBookmark = Math.max(0, ...this.bookmarks.map((b) => Number(b.id.slice(1)) || 0)) + 1
  }

  onChange(listener: (what: 'history' | 'bookmarks') => void) {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  visited(url: string, title: string) {
    if (!recordable(url)) return
    const previous = this.history.find((entry) => entry.url === url)
    this.history = [{ url, title: title || previous?.title || '', visitedAt: Date.now() }, ...this.history.filter((e) => e.url !== url)].slice(
      0,
      HISTORY_LIMIT,
    )
    this.changed('history')
  }

  listHistory(query?: string, limit = 500): HistoryEntry[] {
    const words = (query ?? '').trim().toLowerCase().split(/\s+/).filter(Boolean)
    return this.history
      .filter((entry) => words.every((word) => entry.url.toLowerCase().includes(word) || entry.title.toLowerCase().includes(word)))
      .slice(0, Math.max(1, Math.min(limit, 500)))
  }

  removeHistory(url: string) {
    this.history = this.history.filter((entry) => entry.url !== url)
    this.changed('history')
  }

  clearHistory() {
    this.history = []
    this.changed('history')
  }

  findBookmark(url: string | undefined) {
    return url ? this.bookmarks.find((bookmark) => bookmark.url === url) : undefined
  }

  addBookmark(url: string, title: string, iconUrl: string | null = null): Bookmark {
    if (!recordable(url)) throw new HaloneError('invalid-url', `only http and https pages can be bookmarked: ${url}`)
    const existing = this.findBookmark(url)
    if (existing) return existing
    const bookmark = { id: `b${this.nextBookmark++}`, url, title: (title || url).slice(0, 200), iconUrl, addedAt: Date.now() }
    this.bookmarks = [...this.bookmarks, bookmark]
    this.changed('bookmarks')
    return bookmark
  }

  updateBookmark(id: string, changes: { title?: string; url?: string }): Bookmark {
    const index = this.bookmarks.findIndex((bookmark) => bookmark.id === id)
    if (index < 0) throw new HaloneError('no-such-bookmark', `no such bookmark: ${id}`)
    if (changes.url !== undefined && !recordable(changes.url)) throw new HaloneError('invalid-url', `not a web address: ${changes.url}`)
    const current = this.bookmarks[index]!
    const updated = { ...current, title: changes.title?.trim() ? changes.title.slice(0, 200) : current.title, url: changes.url ?? current.url }
    this.bookmarks = this.bookmarks.map((bookmark, i) => (i === index ? updated : bookmark))
    this.changed('bookmarks')
    return updated
  }

  removeBookmark(id: string) {
    this.bookmarks = this.bookmarks.filter((bookmark) => bookmark.id !== id)
    this.changed('bookmarks')
  }

  private changed(what: 'history' | 'bookmarks') {
    this.store?.save(what, JSON.stringify(what === 'history' ? this.history : this.bookmarks))
    for (const listener of this.listeners) listener(what)
  }
}

function parse<T>(text: string | null | undefined): T | null {
  if (!text) return null
  try {
    return JSON.parse(text) as T
  } catch {
    return null
  }
}
