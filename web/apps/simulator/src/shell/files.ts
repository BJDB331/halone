import { HaloneError, type FileEntry, type FileRoot } from '@halone/sdk'

/** The client side of `server/files.ts`, served from the simulator's own origin. */
export class SimFiles {
  constructor(private readonly origin = window.location.origin) {}

  roots(): Promise<FileRoot[]> {
    return this.get('roots')
  }

  list(path: string): Promise<FileEntry[]> {
    return this.get('list', path)
  }

  stat(path: string): Promise<FileEntry> {
    return this.get('stat', path)
  }

  copy(paths: string[], to: string): Promise<FileEntry[]> {
    return this.post('copy', { paths, to })
  }

  move(paths: string[], to: string): Promise<FileEntry[]> {
    return this.post('move', { paths, to })
  }

  rename(path: string, name: string): Promise<FileEntry> {
    return this.post('rename', { path, name })
  }

  delete(paths: string[]): Promise<void> {
    return this.post('delete', { paths })
  }

  createDirectory(parent: string, name: string): Promise<FileEntry> {
    return this.post('mkdir', { parent, name })
  }

  private async post<T>(endpoint: string, body: unknown): Promise<T> {
    const response = await fetch(new URL(`/__files/${endpoint}`, this.origin), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const result = await response.json()
    if (!response.ok) throw new HaloneError(result.code ?? 'internal', result.message ?? `HTTP ${response.status}`)
    return result as T
  }

  private async get<T>(endpoint: string, path?: string): Promise<T> {
    if (path !== undefined && typeof path !== 'string') throw new HaloneError('invalid-params', 'path must be a string')
    const url = new URL(`/__files/${endpoint}`, this.origin)
    if (path !== undefined) url.searchParams.set('path', path)
    const response = await fetch(url)
    const body = await response.json()
    if (!response.ok) throw new HaloneError(body.code ?? 'internal', body.message ?? `HTTP ${response.status}`)
    return body as T
  }
}
