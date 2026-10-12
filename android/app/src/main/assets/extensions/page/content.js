'use strict'

const TEXT_INPUTS = new Set(['text', 'search', 'url', 'email', 'tel', 'password', 'number', ''])

let port = null
let target = null

function connect() {
  try {
    port = browser.runtime.connectNative('halone')
  } catch (error) {
    port = null
    return
  }
  port.onMessage.addListener(onMessage)
  port.onDisconnect.addListener(() => (port = null))
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', reportPage, { once: true })
  else reportPage()
}

function reportPage() {
  const link = document.querySelector('link[rel~="icon" i], link[rel="apple-touch-icon" i]')
  const icon = link ? link.href : new URL('/favicon.ico', location.href).href
  send({ type: 'page', url: location.href, icon })
}

function send(message) {
  try {
    port?.postMessage(message)
  } catch (error) {
    port = null
  }
}

function editableOf(element) {
  const field = element?.closest?.('input, textarea')
  if (field && !field.disabled && !field.readOnly && (field.tagName === 'TEXTAREA' || TEXT_INPUTS.has(field.type))) return field
  let node = element
  while (node && node.nodeType === Node.ELEMENT_NODE) {
    if (node.isContentEditable) return node
    node = node.parentElement
  }
  return null
}

function selectedText(field) {
  if (field && 'selectionStart' in field && field.selectionStart !== null) {
    return field.value.substring(field.selectionStart, field.selectionEnd)
  }
  return window.getSelection()?.toString() ?? ''
}

function describe(x, y) {
  const element = document.elementFromPoint(x, y)
  const event = new MouseEvent('contextmenu', { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 2, buttons: 2 })
  if (element && !element.dispatchEvent(event)) return { handled: true }

  const link = element?.closest?.('a[href]')
  const image = element?.closest?.('img')
  const media = element?.closest?.('video, audio')
  const field = editableOf(element)
  target = field
  const isPassword = field?.type === 'password'
  return {
    handled: false,
    link: link?.href ?? null,
    linkText: link?.textContent?.trim().slice(0, 200) ?? null,
    image: image ? image.currentSrc || image.src || null : null,
    media: media ? media.currentSrc || media.src || null : null,
    mediaKind: media ? media.tagName.toLowerCase() : null,
    editable: !!field,
    selection: isPassword ? '' : selectedText(field).slice(0, 100000),
  }
}

function middleClick(x, y) {
  const element = document.elementFromPoint(x, y)
  if (!element) return { link: null }
  const init = { bubbles: true, cancelable: true, composed: true, clientX: x, clientY: y, button: 1, view: window }
  element.dispatchEvent(new MouseEvent('mousedown', { ...init, buttons: 4 }))
  element.dispatchEvent(new MouseEvent('mouseup', init))
  const open = element.dispatchEvent(new MouseEvent('auxclick', init))
  const link = element.closest?.('a[href]')
  return { link: open && link && /^https?:$/.test(link.protocol) ? link.href : null }
}

function edit(command, value) {
  if (target && document.activeElement !== target) target.focus()
  document.execCommand(command, false, value)
}

// Keyboard copy, cut and paste: the page gets its own clipboard events first,
// which keys typed from outside never fire, and the text goes through the shell.
function clipboardEvent(type, data) {
  const element = document.activeElement || document.body
  return !element.dispatchEvent(new ClipboardEvent(type, { clipboardData: data, bubbles: true, cancelable: true }))
}

function copyKey(cut) {
  const field = editableOf(document.activeElement)
  const data = new DataTransfer()
  const taken = clipboardEvent(cut ? 'cut' : 'copy', data)
  const text = taken ? data.getData('text/plain') : field?.type === 'password' ? '' : selectedText(field)
  if (cut && !taken && field && text) document.execCommand('delete')
  return text
}

function pasteKey(text) {
  const data = new DataTransfer()
  data.setData('text/plain', text)
  if (clipboardEvent('paste', data)) return
  if (editableOf(document.activeElement)) document.execCommand('insertText', false, text)
}

function onMessage(message) {
  switch (message.type) {
    case 'context':
      send({ type: 'context', id: message.id, ...describe(message.x, message.y) })
      break
    case 'middle':
      send({ type: 'middle', id: message.id, ...middleClick(message.x, message.y) })
      break
    case 'paste':
      edit('insertText', message.text)
      break
    case 'cut':
      edit('delete')
      break
    case 'copyKey':
      send({ type: 'copied', id: message.id, text: copyKey(message.cut) })
      break
    case 'pasteKey':
      if (message.text) pasteKey(message.text)
      break
    case 'selectAll':
      if (target?.select) target.select()
      else edit('selectAll')
      break
  }
}

connect()
