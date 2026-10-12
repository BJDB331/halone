import type { IncomingMessage } from 'node:http'
import type { Plugin, ViteDevServer } from 'vite'

export const DEV_PREFIX = '/__halone/dev'
const MANIFEST_FILE = 'halone.json'
const ANSWER_MS = 5000

export interface DevRequest {
  requestId: number
  command: 'dev' | 'uninstall' | 'list'
  url?: string
  id?: string
  manifest?: unknown
}

export interface DevAnswer {
  requestId: number
  ok: boolean
  [key: string]: unknown
}

async function body(request: IncomingMessage): Promise<Record<string, unknown>> {
  let text = ''
  for await (const chunk of request) text += chunk
  return text ? JSON.parse(text) : {}
}

/**
 * What `@halone/cli` asks the simulator, as `POST /__halone/dev` with
 * `{ command, url?, id? }`: the request goes to the simulator page over
 * Vite's socket, and the page's answer comes back as the response. A dev
 * app's manifest is fetched from its server's root here, where there is no
 * CORS to get in the way, and checked by the page. The config runs in plain
 * Node, which cannot load the SDK's sources, so nothing here imports them.
 */
export function devPlugin(): Plugin {
  let nextRequest = 1
  const waiting = new Map<number, (answer: DevAnswer) => void>()

  function ask(server: ViteDevServer, request: Omit<DevRequest, 'requestId'>): Promise<DevAnswer> {
    const requestId = nextRequest++
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        waiting.delete(requestId)
        resolve({ requestId, ok: false, code: 'no-simulator', message: 'no simulator page answered; is it open in a browser?' })
      }, ANSWER_MS)
      waiting.set(requestId, (answer) => {
        clearTimeout(timer)
        resolve(answer)
      })
      server.ws.send('halone:dev', { ...request, requestId })
    })
  }

  return {
    name: 'halone-dev',
    configureServer(server) {
      server.ws.on('halone:dev-answer', (answer: DevAnswer) => {
        waiting.get(answer.requestId)?.(answer)
        waiting.delete(answer.requestId)
      })
      server.middlewares.use(DEV_PREFIX, async (request, response) => {
        response.setHeader('content-type', 'application/json')
        if (request.method !== 'POST') {
          response.statusCode = 405
          response.end(JSON.stringify({ ok: false, code: 'method', message: 'POST a command' }))
          return
        }
        let answer: unknown
        try {
          const { command, url, id } = (await body(request)) as { command?: string; url?: string; id?: string }
          if (command === 'dev') {
            if (!url || !/^https?:\/\//.test(url)) throw new Error('url must be the dev server page')
            const manifest = await fetch(new URL(`/${MANIFEST_FILE}`, url)).then((r) => {
              if (!r.ok) throw new Error(`${MANIFEST_FILE} answered ${r.status} at ${url}`)
              return r.json()
            })
            answer = await ask(server, { command, url, manifest })
          } else if (command === 'uninstall' || command === 'list') {
            answer = await ask(server, { command, id })
          } else {
            throw new Error(`no such command: ${command}`)
          }
        } catch (error) {
          answer = { ok: false, code: 'invalid', message: error instanceof Error ? error.message : String(error) }
        }
        response.end(JSON.stringify(answer))
      })
    },
  }
}
