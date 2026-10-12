import { HaloneError, type WindowId } from '@halone/sdk'
import type { SocketMessage, SocketRequest } from '../../server/sockets'

const MAX_PER_WINDOW = 16

/** The page's end of the dev server's TCP relay (`server/sockets.ts`), shaped like `Sockets.kt`. */
export class SimSockets {
  private nextKey = 1
  private nextWrite = 1
  private readonly owners = new Map<number, WindowId>()
  private readonly connecting = new Map<number, { resolve: (id: number) => void; reject: (error: Error) => void }>()
  private readonly writing = new Map<number, { resolve: () => void; reject: (error: Error) => void }>()

  /** A socket event for window [owner]: `socket.data` or `socket.closed`. */
  onEvent: ((owner: WindowId, name: 'socket.data' | 'socket.closed', data: { socketId: number; data?: string; error?: string | null }) => void) | null =
    null

  constructor(private readonly hot = import.meta.hot) {
    hot?.on('halone:socket', (message: SocketMessage) => this.receive(message))
  }

  private send(request: SocketRequest) {
    if (!this.hot) throw new HaloneError('unavailable', 'sockets need the simulator dev server')
    this.hot.send('halone:socket', request)
  }

  connect(owner: WindowId, host: string, port: number, timeoutMs = 10_000, tls = false): Promise<{ socketId: number }> {
    if (typeof host !== 'string' || !host) throw new HaloneError('invalid-params', 'host is empty')
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new HaloneError('invalid-params', 'port must be between 1 and 65535')
    if ([...this.owners.values()].filter((o) => o === owner).length >= MAX_PER_WINDOW) {
      throw new HaloneError('too-many-sockets', `a window may have ${MAX_PER_WINDOW} sockets open`)
    }
    const key = this.nextKey++
    this.send({ op: 'connect', key, host, port, timeoutMs: Math.min(Math.max(1, timeoutMs), 60_000), tls })
    return new Promise((resolve, reject) => {
      this.connecting.set(key, {
        resolve: (id) => {
          this.owners.set(id, owner)
          resolve({ socketId: id })
        },
        reject: (error) => reject(new HaloneError('connect-failed', `cannot connect to ${host}:${port}: ${error.message}`)),
      })
    })
  }

  private owned(owner: WindowId, socketId: number) {
    if (this.owners.get(socketId) !== owner) throw new HaloneError('no-such-socket', `no such socket: ${socketId}`)
  }

  write(owner: WindowId, socketId: number, data: string): Promise<void> {
    this.owned(owner, socketId)
    const seq = this.nextWrite++
    this.send({ op: 'write', key: socketId, seq, data })
    return new Promise((resolve, reject) => this.writing.set(seq, { resolve, reject }))
  }

  ack(owner: WindowId, socketId: number) {
    this.owned(owner, socketId)
    this.send({ op: 'ack', key: socketId })
  }

  close(owner: WindowId, socketId: number) {
    this.owned(owner, socketId)
    this.owners.delete(socketId)
    this.send({ op: 'close', key: socketId })
  }

  /** Closes the sockets of a window that closed. */
  windowClosed(owner: WindowId) {
    for (const [id, o] of this.owners) if (o === owner) this.close(owner, id)
  }

  private receive(message: SocketMessage) {
    switch (message.op) {
      case 'connected': {
        const pending = this.connecting.get(message.key)
        this.connecting.delete(message.key)
        if (message.error) pending?.reject(new Error(message.error))
        else pending?.resolve(message.key)
        break
      }
      case 'written': {
        const pending = this.writing.get(message.seq)
        this.writing.delete(message.seq)
        if (message.error) pending?.reject(new HaloneError('socket-closed', `socket ${message.key} is closed`))
        else pending?.resolve()
        break
      }
      case 'data': {
        const owner = this.owners.get(message.key)
        if (owner) this.onEvent?.(owner, 'socket.data', { socketId: message.key, data: message.data })
        break
      }
      case 'closed': {
        const owner = this.owners.get(message.key)
        this.owners.delete(message.key)
        if (owner) this.onEvent?.(owner, 'socket.closed', { socketId: message.key, error: message.error })
        break
      }
    }
  }
}
