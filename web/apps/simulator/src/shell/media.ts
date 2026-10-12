import { reactive } from 'vue'
import { HaloneError, type MediaLayout, type MediaState, type WindowId } from '@halone/sdk'

export interface SimMediaState extends MediaState {
  ownerId: WindowId | null
  url: string | null
  /** Compass heading of the screen, or of the front of the sphere. */
  anchorYawDegrees: number
}

/**
 * The simulator's immersive player. The video element lives in the stage,
 * which attaches it here; without one (in tests) the commands only move the
 * state, so the protocol can be exercised headless.
 */
export class SimMedia {
  readonly state = reactive<SimMediaState>(closedState())
  private video: HTMLVideoElement | null = null
  private listener: (ownerId: WindowId, state: MediaState) => void = () => {}

  onChange(listener: (ownerId: WindowId, state: MediaState) => void) {
    this.listener = listener
  }

  snapshot(): MediaState {
    const { ownerId: _owner, url: _url, anchorYawDegrees: _anchor, ...state } = this.state
    return { ...state }
  }

  open(ownerId: WindowId, path: string, url: string, layout: MediaLayout, headYawDegrees: number, startSeconds = 0): MediaState {
    const previous = this.state.ownerId
    if (previous && previous !== ownerId) this.close()
    Object.assign(this.state, closedState(), {
      ownerId,
      open: true,
      path,
      url,
      ...layout,
      anchorYawDegrees: headYawDegrees,
      positionSeconds: startSeconds,
      playing: true,
    })
    this.notify()
    return this.snapshot()
  }

  attachVideo(video: HTMLVideoElement | null) {
    this.video = video
    if (!video) return
    video.currentTime = this.state.positionSeconds
    const update = () => {
      this.state.positionSeconds = video.currentTime
      this.state.durationSeconds = Number.isFinite(video.duration) ? video.duration : 0
      this.state.videoWidthPx = video.videoWidth
      this.state.videoHeightPx = video.videoHeight
      this.state.playing = !video.paused
      this.state.ended = video.ended
      this.notify()
    }
    for (const name of ['loadedmetadata', 'timeupdate', 'play', 'pause', 'ended', 'seeked']) video.addEventListener(name, update)
    video.addEventListener('error', () => {
      this.state.error = video.error?.message || 'the video could not be played'
      this.state.playing = false
      this.notify()
    })
    if (this.state.playing) void video.play().catch(() => update())
  }

  play(caller: WindowId) {
    this.own(caller)
    this.state.playing = true
    this.state.ended = false
    if (this.video) void this.video.play()
    else this.notify()
  }

  pause(caller: WindowId) {
    this.own(caller)
    this.state.playing = false
    if (this.video) this.video.pause()
    else this.notify()
  }

  seek(caller: WindowId, seconds: number) {
    this.own(caller)
    const duration = this.state.durationSeconds || Infinity
    this.state.positionSeconds = Math.max(0, Math.min(seconds, duration))
    if (this.video) this.video.currentTime = this.state.positionSeconds
    else this.notify()
  }

  setLayout(caller: WindowId, layout: MediaLayout) {
    this.own(caller)
    Object.assign(this.state, layout)
    this.notify()
  }

  setRotatable(caller: WindowId, rotatable: boolean) {
    this.own(caller)
    this.state.rotatable = rotatable
    this.notify()
  }

  /** A drag on empty space, while the player allows it, turns the video by [degrees]. */
  rotateBy(degrees: number): boolean {
    if (!this.state.open || !this.state.rotatable) return false
    this.state.anchorYawDegrees += degrees
    return true
  }

  recenter(caller: WindowId, headYawDegrees: number) {
    this.own(caller)
    this.state.anchorYawDegrees = headYawDegrees
  }

  closeFor(caller: WindowId) {
    this.own(caller)
    this.close()
  }

  /** Closing the window that owns the player closes the player. */
  windowClosed(id: WindowId) {
    if (this.state.ownerId === id) this.close()
  }

  private close() {
    const owner = this.state.ownerId
    this.video?.pause()
    Object.assign(this.state, closedState())
    if (owner) this.listener(owner, this.snapshot())
  }

  private own(caller: WindowId) {
    if (!this.state.open) throw new HaloneError('no-media', 'no video is open')
    if (this.state.ownerId !== caller) throw new HaloneError('not-owner', 'another window owns the player')
  }

  private notify() {
    if (this.state.ownerId) this.listener(this.state.ownerId, this.snapshot())
  }
}

function closedState(): SimMediaState {
  return {
    ownerId: null,
    url: null,
    anchorYawDegrees: 0,
    open: false,
    path: null,
    playing: false,
    ended: false,
    positionSeconds: 0,
    durationSeconds: 0,
    videoWidthPx: 0,
    videoHeightPx: 0,
    projection: 'flat',
    stereo: 'mono',
    rotatable: false,
    error: null,
  }
}
