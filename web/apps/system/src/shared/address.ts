import type { Settings } from '@halone/sdk'

const SEARCH: Record<Settings['browser.searchEngine'], string> = {
  duckduckgo: 'https://duckduckgo.com/?q=',
  google: 'https://www.google.com/search?q=',
  bing: 'https://www.bing.com/search?q=',
}

const HOME: Record<Settings['browser.searchEngine'], string> = {
  duckduckgo: 'https://duckduckgo.com/',
  google: 'https://www.google.com/',
  bing: 'https://www.bing.com/',
}

/** The search engine's own front page, for opening the browser with nothing typed. */
export function homeUrl(engine: Settings['browser.searchEngine'] = 'duckduckgo') {
  return HOME[engine]
}

/** What the wearer typed, as a URL to open: an address as it is, anything else as a search. */
export function toUrl(input: string, engine: Settings['browser.searchEngine'] = 'duckduckgo') {
  const text = input.trim()
  if (/^https?:\/\//i.test(text)) return text
  if (/^[\w-]+(\.[\w-]+)+(:\d+)?(\/.*)?$/.test(text) || /^localhost(:\d+)?(\/.*)?$/.test(text)) return `https://${text}`
  return `${SEARCH[engine]}${encodeURIComponent(text)}`
}

/** A URL as the address field shows it while not being edited. */
export function displayUrl(url: string) {
  const short = url.replace(/^https?:\/\//, '').replace(/\/$/, '')
  try {
    return decodeURI(short)
  } catch {
    return short
  }
}
