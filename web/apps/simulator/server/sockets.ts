import { connect as tcp, type Socket } from 'node:net'
import { connect as tls } from 'node:tls'
import type { Plugin } from 'vite'

/** As `Sockets.kt`: events a page has not acknowledged before the socket stops reading. */
const WINDOW = 8

export type SocketRequest =
  | { op: 'connect'; key: number; host: string; port: number; timeoutMs: number; tls: boolean }
  | { op: 'write'; key: number; seq: number; data: string }
  | { op: 'ack' | 'close'; key: number }

export type SocketMessage =
  | { op: 'connected'; key: number; error: string | null }
  | { op: 'written'; key: number; seq: number; error: string | null }
  | { op: 'data'; key: number; data: string }
  | { op: 'closed'; key: number; error: string | null }

interface Relay {
  socket: Socket
  credit: number
  closing: boolean
}

/**
 * TCP for the simulator's apps, which a browser cannot open: the page asks
 * over Vite's socket and the dev server connects with Node, per page.
 */
export function socketsPlugin(): Plugin {
  return {
    name: 'halone-sockets',
    configureServer(server) {
      const pages = new WeakMap<object, Map<number, Relay>>()
      server.ws.on('halone:socket', (request: SocketRequest, client) => {
        let relays = pages.get(client)
        if (!relays) pages.set(client, (relays = new Map()))
        const send = (message: SocketMessage) => client.send('halone:socket', message)
        const relay = relays.get(request.key)
        switch (request.op) {
          case 'connect': {
            const options = { host: request.host, port: request.port }
            const socket = request.tls ? tls({ ...options, servername: request.host }) : tcp(options)
            const entry: Relay = { socket, credit: WINDOW, closing: false }
            relays.set(request.key, entry)
            const timer = setTimeout(() => socket.destroy(new Error(`timed out after ${request.timeoutMs} ms`)), request.timeoutMs)
            let connected = false
            socket.once(request.tls ? 'secureConnect' : 'connect', () => {
              clearTimeout(timer)
              connected = true
              socket.setNoDelay(true)
              send({ op: 'connected', key: request.key, error: null })
            })
            socket.on('data', (chunk) => {
              send({ op: 'data', key: request.key, data: chunk.toString('base64') })
              if (--entry.credit <= 0) socket.pause()
            })
            socket.on('error', (error) => {
              if (!connected) send({ op: 'connected', key: request.key, error: error.message })
              else if (!entry.closing) send({ op: 'closed', key: request.key, error: error.message })
              entry.closing = true
            })
            socket.on('close', () => {
              clearTimeout(timer)
              if (connected && !entry.closing) send({ op: 'closed', key: request.key, error: null })
              relays.delete(request.key)
            })
            break
          }
          case 'write':
            if (!relay) return send({ op: 'written', key: request.key, seq: request.seq, error: 'socket is closed' })
            relay.socket.write(Buffer.from(request.data, 'base64'), (error) =>
              send({ op: 'written', key: request.key, seq: request.seq, error: error?.message ?? null }),
            )
            break
          case 'ack':
            if (!relay) break
            relay.credit = Math.min(relay.credit + 1, WINDOW)
            if (relay.socket.isPaused()) relay.socket.resume()
            break
          case 'close':
            if (relay) {
              relay.closing = true
              relay.socket.destroy()
            }
            break
        }
      })
    },
  }
}
