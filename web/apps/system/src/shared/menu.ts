import type { MenuItem, HaloneClient } from '@halone/sdk'

export type MenuAction = { id: string; label: string; disabled?: boolean; danger?: boolean; run: () => unknown } | 'separator'

/** Shows the shell's context menu over this window at ([x], [y]) and runs what the wearer picks. */
export async function showMenu(halone: HaloneClient, actions: MenuAction[], x: number, y: number) {
  const items: MenuItem[] = actions.map((action) =>
    action === 'separator' ? { separator: true } : { id: action.id, label: action.label, disabled: action.disabled, danger: action.danger },
  )
  const chosen = await halone.menu.show(items, Math.round(x), Math.round(y))
  const action = actions.find((candidate) => candidate !== 'separator' && candidate.id === chosen)
  if (action && action !== 'separator') await action.run()
}

const TEXT_INPUTS = new Set(['text', 'search', 'url', 'email', 'tel', 'password', 'number'])

type Field = HTMLInputElement | HTMLTextAreaElement

function fieldOf(target: EventTarget | null): Field | null {
  const element = target instanceof Element ? target.closest('input, textarea') : null
  if (element instanceof HTMLTextAreaElement) return element.disabled ? null : element
  if (element instanceof HTMLInputElement && TEXT_INPUTS.has(element.type) && !element.disabled) return element
  return null
}

function replaceSelection(field: Field, text: string) {
  const start = field.selectionStart ?? field.value.length
  const end = field.selectionEnd ?? start
  field.focus()
  field.setRangeText(text, start, end, 'end')
  field.dispatchEvent(new Event('input', { bubbles: true }))
}

let installed = false

/**
 * Gives every text field Cut, Copy, Paste and Select all, through the
 * shell's clipboard. A page's own `contextmenu` handler runs first and wins
 * by calling `preventDefault`.
 */
export function installTextMenu(halone: HaloneClient) {
  if (installed) return
  installed = true
  document.addEventListener('contextmenu', async (event) => {
    if (event.defaultPrevented) return
    const field = fieldOf(event.target)
    if (!field) return
    event.preventDefault()
    const start = field.selectionStart ?? 0
    const end = field.selectionEnd ?? 0
    const selected = field.value.slice(start, end)
    const secret = field instanceof HTMLInputElement && field.type === 'password'
    const clipboard = await halone.clipboard.read().catch(() => null)
    const readOnly = field.readOnly
    await showMenu(
      halone,
      [
        {
          id: 'cut',
          label: 'Cut',
          disabled: !selected || readOnly || secret,
          run: async () => {
            await halone.clipboard.writeText(selected)
            field.setSelectionRange(start, end)
            replaceSelection(field, '')
          },
        },
        { id: 'copy', label: 'Copy', disabled: !selected || secret, run: () => halone.clipboard.writeText(selected) },
        {
          id: 'paste',
          label: 'Paste',
          disabled: !clipboard?.text || readOnly,
          run: () => {
            field.setSelectionRange(start, end)
            replaceSelection(field, clipboard!.text!)
          },
        },
        'separator',
        {
          id: 'select-all',
          label: 'Select all',
          disabled: !field.value,
          run: () => {
            field.focus()
            field.select()
          },
        },
      ],
      event.clientX,
      event.clientY,
    ).catch(() => {})
  })
}
