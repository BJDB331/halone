import { describe, expect, it } from 'vitest'
import { homeUrl, toUrl } from '../src/shared/address'

describe('homeUrl', () => {
  it('is the configured search engine\'s front page', () => {
    expect(homeUrl('duckduckgo')).toBe('https://duckduckgo.com/')
    expect(homeUrl('google')).toBe('https://www.google.com/')
    expect(homeUrl('bing')).toBe('https://www.bing.com/')
  })

  it('defaults to DuckDuckGo, like searching does', () => {
    expect(homeUrl()).toBe('https://duckduckgo.com/')
    expect(toUrl('halone')).toBe('https://duckduckgo.com/?q=halone')
  })
})
