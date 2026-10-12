import { execFile } from 'node:child_process'
import { createReadStream, existsSync, promises as fs, type Stats } from 'node:fs'
import { basename, dirname, resolve, sep } from 'node:path'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Plugin } from 'vite'
import { mimeTypeOf } from '@halone/sdk/files'
import type { FileEntry, FileRoot, FileRootKind } from '@halone/sdk/protocol'

export const FILES_PREFIX = '/__files'

const ROOTS: [FileRootKind, string, string][] = [
  ['pictures', 'Pictures', '/Pictures'],
  ['camera', 'Camera', '/DCIM/Camera'],
  ['movies', 'Movies', '/Movies'],
  ['music', 'Music', '/Music'],
  ['downloads', 'Downloads', '/Downloads'],
  ['documents', 'Documents', '/Documents'],
]

class FileError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

/**
 * The simulator's shared storage: a folder on this machine, `HALONE_FILES` or
 * the bundled samples, seen by apps as `/`. Paths never leave it.
 */
export function filesPlugin(root = process.env.HALONE_FILES ?? resolve(import.meta.dirname, '../sample-files')): Plugin {
  const base = resolve(root)

  function local(path: string): string {
    if (!path.startsWith('/')) throw new FileError('invalid-params', `not an absolute path: ${path}`)
    const target = resolve(base, '.' + path)
    if (target !== base && !target.startsWith(base + sep)) throw new FileError('no-such-file', `no such file: ${path}`)
    return target
  }

  const encode = (path: string) => path.split('/').map(encodeURIComponent).join('/')

  function entry(path: string, stats: Stats, origin: string): FileEntry {
    const name = path === '/' ? 'Storage' : path.slice(path.lastIndexOf('/') + 1)
    const directory = stats.isDirectory()
    const type = directory ? null : mimeTypeOf(name)
    const previewable = type?.startsWith('image/') || type?.startsWith('video/') || type?.startsWith('audio/')
    return {
      path,
      name,
      kind: directory ? 'directory' : 'file',
      sizeBytes: directory ? 0 : stats.size,
      modifiedAt: Math.round(stats.mtimeMs),
      mimeType: type,
      url: directory ? null : `${origin}${FILES_PREFIX}/raw${encode(path)}`,
      thumbnailUrl: previewable ? `${origin}${FILES_PREFIX}/thumb${encode(path)}?v=${Math.round(stats.mtimeMs)}` : null,
    }
  }

  async function stat(path: string) {
    try {
      return await fs.stat(local(path))
    } catch (error) {
      if (error instanceof FileError) throw error
      throw new FileError('no-such-file', `no such file: ${path}`)
    }
  }

  const join = (directory: string, name: string) => (directory === '/' ? `/${name}` : `${directory}/${name}`)

  async function list(path: string, origin: string): Promise<FileEntry[]> {
    const stats = await stat(path)
    if (!stats.isDirectory()) throw new FileError('not-a-directory', `not a directory: ${path}`)
    const names = (await fs.readdir(local(path))).filter((name) => !name.startsWith('.'))
    const entries = await Promise.all(
      names.map(async (name) => {
        const child = join(path, name)
        return entry(child, await fs.stat(local(child)), origin)
      }),
    )
    return entries
  }

  function roots(): FileRoot[] {
    const found = ROOTS.filter(([, , path]) => existsSync(local(path))).map(([kind, name, path]) => ({ kind, name, path }))
    return [{ kind: 'storage', name: 'Storage', path: '/' }, ...found]
  }

  async function folder(path: string) {
    if (!(await stat(path)).isDirectory()) throw new FileError('not-a-directory', `not a directory: ${path}`)
    return path
  }

  function checkName(name: unknown): string {
    if (typeof name !== 'string' || !name.trim() || name === '.' || name === '..' || name.includes('/') || name.length > 255) {
      throw new FileError('invalid-params', `not a valid name: ${name}`)
    }
    return name
  }

  function paths(value: unknown): string[] {
    if (!Array.isArray(value) || !value.every((item) => typeof item === 'string')) throw new FileError('invalid-params', 'paths must be strings')
    return value
  }


  function refuseInsideItself(source: string, target: string) {
    if (target === source || target.startsWith(source + '/')) throw new FileError('invalid-params', `a folder cannot go inside itself: ${source}`)
  }

  async function transfer(body: Record<string, unknown>, origin: string, move: boolean): Promise<FileEntry[]> {
    const to = await folder(String(body.to))
    const results: FileEntry[] = []
    for (const source of paths(body.paths)) {
      const stats = await stat(source)
      if (stats.isDirectory()) refuseInsideItself(source, to)
      if (move && dirname(local(source)) === local(to)) {
        results.push(entry(source, stats, origin))
        continue
      }
      const names = new Set(await fs.readdir(local(to)))
      const target = join(to, freeName(names, basename(source)))
      if (move) await fs.rename(local(source), local(target))
      else await fs.cp(local(source), local(target), { recursive: true })
      results.push(entry(target, await fs.stat(local(target)), origin))
    }
    return results
  }

  async function operate(operation: string, body: Record<string, unknown>, origin: string): Promise<unknown> {
    switch (operation) {
      case '/copy':
        return transfer(body, origin, false)
      case '/move':
        return transfer(body, origin, true)
      case '/rename': {
        const path = String(body.path)
        await stat(path)
        if (path === '/') throw new FileError('permission-denied', 'a storage root cannot be renamed')
        const name = checkName(body.name)
        const target = join(path.slice(0, path.lastIndexOf('/')) || '/', name)
        if (target !== path && existsSync(local(target))) throw new FileError('exists', `there is already something called ${name}`)
        await fs.rename(local(path), local(target))
        return entry(target, await fs.stat(local(target)), origin)
      }
      case '/delete':
        for (const path of paths(body.paths)) {
          await stat(path)
          if (roots().some((root) => root.path === path)) throw new FileError('permission-denied', 'a storage root cannot be deleted')
          await fs.rm(local(path), { recursive: true })
        }
        return null
      case '/mkdir': {
        const parent = await folder(String(body.parent))
        const name = checkName(body.name)
        const target = join(parent, name)
        if (existsSync(local(target))) throw new FileError('exists', `there is already something called ${name}`)
        await fs.mkdir(local(target))
        return entry(target, await fs.stat(local(target)), origin)
      }
    }
    throw new FileError('not-found', `no such operation: ${operation}`)
  }

  async function readBody(request: IncomingMessage): Promise<Record<string, unknown>> {
    const chunks: Buffer[] = []
    for await (const chunk of request) chunks.push(chunk as Buffer)
    try {
      return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')
    } catch {
      throw new FileError('invalid-params', 'the body must be JSON')
    }
  }

  async function serveRaw(request: IncomingMessage, response: ServerResponse, path: string) {
    const stats = await stat(path)
    if (!stats.isFile()) throw new FileError('no-such-file', `not a file: ${path}`)
    const range = parseRange(request.headers.range, stats.size)
    response.setHeader('Accept-Ranges', 'bytes')
    response.setHeader('Content-Type', mimeTypeOf(path) ?? 'application/octet-stream')
    response.setHeader('Access-Control-Allow-Origin', '*')
    if (range === 'unsatisfiable') {
      response.statusCode = 416
      response.setHeader('Content-Range', `bytes */${stats.size}`)
      response.end()
      return
    }
    const [start, end] = range ?? [0, stats.size - 1]
    response.statusCode = range ? 206 : 200
    if (range) response.setHeader('Content-Range', `bytes ${start}-${end}/${stats.size}`)
    response.setHeader('Content-Length', String(Math.max(0, end - start + 1)))
    if (request.method === 'HEAD' || stats.size === 0) return void response.end()
    createReadStream(local(path), { start, end }).pipe(response)
  }

  const thumbnails = new Map<string, Promise<Buffer | null>>()

  /** A 320px JPEG (640px for a song's cover) by ImageMagick or ffmpeg, as the phone makes with ThumbnailUtils; null when neither manages. */
  function thumbnail(path: string, stats: Stats): Promise<Buffer | null> {
    const key = `${path}|${stats.mtimeMs}`
    let made = thumbnails.get(key)
    if (!made) {
      const file = local(path)
      const type = mimeTypeOf(path)
      const [command, args] = type?.startsWith('video/')
        ? ['ffmpeg', ['-loglevel', 'error', '-ss', '1', '-i', file, '-frames:v', '1', '-vf', 'scale=320:320:force_original_aspect_ratio=decrease', '-f', 'mjpeg', '-']]
        : type?.startsWith('audio/')
          ? ['ffmpeg', ['-loglevel', 'error', '-i', file, '-an', '-frames:v', '1', '-vf', 'scale=640:640:force_original_aspect_ratio=decrease', '-f', 'mjpeg', '-']]
          : ['magick', [`${file}[0]`, '-auto-orient', '-thumbnail', '320x320', '-quality', '82', 'jpg:-']]
      made = new Promise((done) =>
        execFile(command, args, { encoding: 'buffer', maxBuffer: 8 * 1024 * 1024 }, (error, stdout) => done(error || !stdout.length ? null : stdout)),
      )
      thumbnails.set(key, made)
    }
    return made
  }

  async function serveThumbnail(response: ServerResponse, path: string) {
    const stats = await stat(path)
    const bytes = stats.isFile() ? await thumbnail(path, stats) : null
    if (!bytes) throw new FileError('no-such-file', `no thumbnail for ${path}`)
    response.setHeader('Content-Type', 'image/jpeg')
    response.setHeader('Cache-Control', 'private, max-age=31536000, immutable')
    response.setHeader('Access-Control-Allow-Origin', '*')
    response.end(bytes)
  }

  return {
    name: 'halone-files',
    configureServer(server) {
      server.middlewares.use(FILES_PREFIX, async (request, response) => {
        const url = new URL(request.url ?? '/', 'http://localhost')
        const origin = `http://${request.headers.host}`
        try {
          if (url.pathname.startsWith('/thumb/')) {
            return await serveThumbnail(response, decodeURIComponent(url.pathname.slice('/thumb'.length)))
          }
          if (url.pathname.startsWith('/raw/')) {
            return await serveRaw(request, response, decodeURIComponent(url.pathname.slice('/raw'.length)))
          }
          if (request.method === 'POST') {
            const result = await operate(url.pathname, await readBody(request), origin)
            response.setHeader('Content-Type', 'application/json')
            return void response.end(JSON.stringify(result))
          }
          const path = url.searchParams.get('path') ?? '/'
          const body =
            url.pathname === '/roots' ? roots()
            : url.pathname === '/list' ? await list(path, origin)
            : url.pathname === '/stat' ? entry(path, await stat(path), origin)
            : null
          if (body === null) throw new FileError('not-found', `no such endpoint: ${url.pathname}`)
          response.setHeader('Content-Type', 'application/json')
          response.end(JSON.stringify(body))
        } catch (error) {
          const code = error instanceof FileError ? error.code : 'internal'
          response.statusCode = code === 'internal' ? 500 : 404
          response.setHeader('Content-Type', 'application/json')
          response.end(JSON.stringify({ code, message: error instanceof Error ? error.message : String(error) }))
        }
      })
    },
  }
}

/** [name], or the first of `name (2)`, `name (3)`… not in [taken], keeping the extension, as `SharedFiles.freeName` does. */
export function freeName(taken: Set<string>, name: string): string {
  if (!taken.has(name)) return name
  const dot = name.lastIndexOf('.') > 0 ? name.lastIndexOf('.') : name.length
  for (let n = 2; ; n++) {
    const candidate = `${name.slice(0, dot)} (${n})${name.slice(dot)}`
    if (!taken.has(candidate)) return candidate
  }
}

/** One `bytes=start-end` range, which is all a media element asks for. */
export function parseRange(header: string | undefined, size: number): [number, number] | 'unsatisfiable' | null {
  const match = header?.match(/^bytes=(\d*)-(\d*)$/)
  if (!match) return null
  const [, from, to] = match
  if (!from && !to) return null
  let start: number
  let end: number
  if (!from) {
    start = Math.max(0, size - Number(to))
    end = size - 1
  } else {
    start = Number(from)
    end = to ? Math.min(Number(to), size - 1) : size - 1
  }
  if (start >= size || start > end) return 'unsatisfiable'
  return [start, end]
}
