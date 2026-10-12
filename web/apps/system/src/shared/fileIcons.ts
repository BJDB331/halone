import { isImage, isVideo, type FileEntry } from '@halone/sdk'

const ARCHIVES = ['zip', 'x-tar', 'gzip', 'x-gzip', 'x-7z-compressed', 'vnd.rar', 'x-rar-compressed', 'x-bzip2', 'x-xz', 'x-halone']

/** The Papirus icon for what [entry] holds, from `/icons/files/`. */
export function fileIconUrl(entry: Pick<FileEntry, 'kind' | 'mimeType'>): string {
  const mime = entry.mimeType ?? ''
  const kind = (() => {
    if (entry.kind === 'directory') return 'folder'
    if (isImage(entry)) return 'image'
    if (isVideo(entry)) return 'video'
    if (mime.startsWith('audio/')) return 'audio'
    if (mime === 'application/pdf') return 'pdf'
    if (mime === 'text/html' || mime === 'application/xhtml+xml') return 'html'
    if (mime.startsWith('text/') || mime === 'application/json') return 'text'
    if (mime === 'application/vnd.android.package-archive') return 'apk'
    if (mime.startsWith('application/') && ARCHIVES.includes(mime.slice('application/'.length))) return 'archive'
    return 'unknown'
  })()
  return `/icons/files/${kind}.svg`
}
