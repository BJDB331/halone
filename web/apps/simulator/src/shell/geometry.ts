export const DISPLAY_WIDTH_PX = 1920
export const DISPLAY_HEIGHT_PX = 1080
/**
 * What an Air 2 Pro's own calibration renders with. Panels are still sized, as
 * natively, at 75% of the 19° symmetric figure, so 800px subtends 14.25° and
 * fills about 63% of this view.
 */
export const VERTICAL_FOV_DEGREES = 22.58
export const SIZING_FOV_DEGREES = 19
export const REFERENCE_PANEL_HEIGHT_PX = 800
export const PANEL_VERTICAL_FILL = 0.75

const toRadians = (degrees: number) => (degrees * Math.PI) / 180
const toDegrees = (radians: number) => (radians * 180) / Math.PI

export const perspectivePx = DISPLAY_HEIGHT_PX / 2 / Math.tan(toRadians(VERTICAL_FOV_DEGREES / 2))

export const horizontalFovDegrees = toDegrees(2 * Math.atan((DISPLAY_WIDTH_PX / 2) / perspectivePx))

export const referencePanelDegrees = SIZING_FOV_DEGREES * PANEL_VERTICAL_FILL

export const panelDistancePx = REFERENCE_PANEL_HEIGHT_PX / 2 / Math.tan(toRadians(referencePanelDegrees / 2))

export const pxPerDegree = REFERENCE_PANEL_HEIGHT_PX / (2 * toDegrees(Math.atan(REFERENCE_PANEL_HEIGHT_PX / 2 / panelDistancePx)))

export function angularHalfWidthDegrees(widthPx: number) {
  return toDegrees(Math.atan(widthPx / 2 / panelDistancePx))
}

export function angularHalfHeightDegrees(heightPx: number) {
  return toDegrees(Math.atan(heightPx / 2 / panelDistancePx))
}

export function wrapDegrees(angle: number) {
  const wrapped = ((((angle + 180) % 360) + 360) % 360) - 180
  return wrapped === -180 ? 180 : wrapped
}

/**
 * Where a point on the display, in display pixels from its centre, looks in
 * the room: the inverse of the stage's CSS camera, so a hit test there lands
 * on what is drawn under it.
 */
export function pointerAngles(headYawDegrees: number, headPitchDegrees: number, dxPx: number, dyPx: number) {
  const yaw = -toRadians(headYawDegrees)
  const pitch = -toRadians(headPitchDegrees)
  const y = dyPx * Math.cos(pitch) + perspectivePx * Math.sin(pitch)
  const z = dyPx * Math.sin(pitch) - perspectivePx * Math.cos(pitch)
  const x = dxPx * Math.cos(yaw) + z * Math.sin(yaw)
  const zWorld = -dxPx * Math.sin(yaw) + z * Math.cos(yaw)
  const length = Math.hypot(x, y, zWorld)
  return { yawDegrees: toDegrees(Math.atan2(x, -zWorld)), pitchDegrees: toDegrees(Math.asin(-y / length)) }
}
