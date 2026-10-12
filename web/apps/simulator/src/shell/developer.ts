import { checkManifest } from '@halone/sdk/package'
import type { DevAnswer, DevRequest } from '../../server/dev'
import type { Shell } from './shell'

/** Answers `@halone/cli`, which reaches this page through the dev server (`server/dev.ts`). */
export function listenForDeveloper(shell: Shell) {
  const hot = import.meta.hot
  if (!hot) return
  hot.on('halone:dev', (request: DevRequest) => {
    let answer: DevAnswer
    try {
      answer = { requestId: request.requestId, ok: true, ...run(shell, request) }
    } catch (error) {
      const code = (error as { code?: string }).code ?? 'internal'
      answer = { requestId: request.requestId, ok: false, code, message: error instanceof Error ? error.message : String(error) }
    }
    hot.send('halone:dev-answer', answer)
  })
}

function run(shell: Shell, request: DevRequest): Record<string, unknown> {
  switch (request.command) {
    case 'dev': {
      const window = shell.installDevApp(checkManifest(request.manifest, shell.reservedIds), request.url!)
      return { id: window.appId, windowId: window.id }
    }
    case 'uninstall':
      shell.uninstall(request.id ?? '')
      return {}
    case 'list':
      return { apps: shell.packageApps.apps.map((app) => ({ id: app.manifest.id, version: app.manifest.version, dev: app.dev })) }
  }
}
