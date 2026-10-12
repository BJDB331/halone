<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { eyeAspect } from '@halone/sdk/files'
import { useSimulator } from '../injection'
import { DISPLAY_HEIGHT_PX, DISPLAY_WIDTH_PX, perspectivePx } from '../shell/geometry'

const { shell, media } = useSimulator()
const canvas = ref<HTMLCanvasElement>()
const video = ref<HTMLVideoElement>()

watch(video, (element) => media.attachVideo(element ?? null), { flush: 'post' })

const PROJECTIONS = { flat: 0, 'equirect-180': 1, 'equirect-360': 2 }
const STEREO = { mono: 0, 'side-by-side': 1, 'top-bottom': 2 }

const VERTEX = `#version 300 es
in vec2 aPosition;
out vec2 vPx;
uniform vec2 uHalfSize;
void main() {
  vPx = vec2(aPosition.x, -aPosition.y) * uHalfSize;
  gl_Position = vec4(aPosition, 0.0, 1.0);
}`

// The inverse of the stage's CSS camera, as pointerAngles() in geometry.ts,
// then the frame's own projection. The simulator is mono: it shows the left eye.
const FRAGMENT = `#version 300 es
precision highp float;
in vec2 vPx;
out vec4 fragColor;
uniform sampler2D uFrame;
uniform float uFocalPx;
uniform vec2 uHead;
uniform float uAnchor;
uniform int uProjection;
uniform int uStereo;
uniform float uScreenHalfTan;
uniform float uEyeAspect;
uniform float uSphereZoom;
const float PI = 3.14159265;
vec3 widen(vec3 d) {
  float c = -d.z;
  float s = length(d.xy);
  if (s < 1e-6) return d;
  float angle = min(acos(c) / uSphereZoom, PI);
  return vec3(d.xy / s * sin(angle), -cos(angle));
}
void main() {
  float yaw = -uHead.x;
  float pitch = -uHead.y;
  vec3 v = normalize(vec3(vPx, -uFocalPx));
  if (uProjection != 0 && uSphereZoom < 1.0) v = widen(v);
  float y = v.y * cos(pitch) - v.z * sin(pitch);
  float z = v.y * sin(pitch) + v.z * cos(pitch);
  float x = v.x * cos(yaw) + z * sin(yaw);
  float zw = -v.x * sin(yaw) + z * cos(yaw);
  float lon = atan(x, -zw) - uAnchor;
  lon = mod(lon + PI, 2.0 * PI) - PI;
  float lat = asin(clamp(-y / length(vec3(x, y, zw)), -1.0, 1.0));
  vec2 uv;
  if (uProjection == 2) {
    uv = vec2(0.5 + lon / (2.0 * PI), 0.5 - lat / PI);
  } else if (uProjection == 1) {
    if (abs(lon) > 0.5 * PI) discard;
    uv = vec2(0.5 + lon / PI, 0.5 - lat / PI);
  } else {
    vec3 d = vec3(cos(lat) * sin(lon), sin(lat), cos(lat) * cos(lon));
    if (d.z <= 0.0) discard;
    vec2 p = d.xy / d.z;
    uv = vec2(0.5 + p.x / (2.0 * uScreenHalfTan), 0.5 - p.y * uEyeAspect / (2.0 * uScreenHalfTan));
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) discard;
  }
  if (uStereo == 1) uv.x *= 0.5;
  if (uStereo == 2) uv.y *= 0.5;
  fragColor = texture(uFrame, uv);
}`

let frame = 0

onMounted(() => {
  const gl = canvas.value!.getContext('webgl2', { premultipliedAlpha: true, alpha: true })
  if (!gl) return
  const program = link(gl)
  const buffer = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW)
  const position = gl.getAttribLocation(program, 'aPosition')
  gl.enableVertexAttribArray(position)
  gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0)
  const texture = gl.createTexture()
  gl.bindTexture(gl.TEXTURE_2D, texture)
  for (const [name, value] of [
    [gl.TEXTURE_MIN_FILTER, gl.LINEAR],
    [gl.TEXTURE_MAG_FILTER, gl.LINEAR],
    [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE],
    [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE],
  ] as const) gl.texParameteri(gl.TEXTURE_2D, name, value)
  const uniform = (name: string) => gl.getUniformLocation(program, name)
  gl.useProgram(program)
  gl.uniform2f(uniform('uHalfSize'), DISPLAY_WIDTH_PX / 2, DISPLAY_HEIGHT_PX / 2)
  gl.uniform1f(uniform('uFocalPx'), perspectivePx)
  const radians = (degrees: number) => (degrees * Math.PI) / 180

  const draw = () => {
    frame = requestAnimationFrame(draw)
    gl.viewport(0, 0, DISPLAY_WIDTH_PX, DISPLAY_HEIGHT_PX)
    gl.clearColor(0, 0, 0, 0)
    gl.clear(gl.COLOR_BUFFER_BIT)
    const element = video.value
    const state = media.state
    if (!state.open || !element || element.readyState < 2) return
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, element)
    gl.uniform2f(uniform('uHead'), radians(shell.state.head.yawDegrees), radians(shell.state.head.pitchDegrees))
    gl.uniform1f(uniform('uAnchor'), radians(state.anchorYawDegrees))
    gl.uniform1i(uniform('uProjection'), PROJECTIONS[state.projection])
    gl.uniform1i(uniform('uStereo'), STEREO[state.stereo])
    gl.uniform1f(uniform('uScreenHalfTan'), Math.tan(radians(shell.state.settings['media.screenWidthDegrees'] / 2)))
    gl.uniform1f(uniform('uEyeAspect'), eyeAspect(element.videoWidth, element.videoHeight, state.stereo))
    gl.uniform1f(uniform('uSphereZoom'), shell.state.settings['media.sphereZoom'])
    gl.drawArrays(gl.TRIANGLES, 0, 3)
  }
  frame = requestAnimationFrame(draw)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  media.attachVideo(null)
})

function link(gl: WebGL2RenderingContext): WebGLProgram {
  const program = gl.createProgram()!
  for (const [type, source] of [
    [gl.VERTEX_SHADER, VERTEX],
    [gl.FRAGMENT_SHADER, FRAGMENT],
  ] as const) {
    const shader = gl.createShader(type)!
    gl.shaderSource(shader, source)
    gl.compileShader(shader)
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader) ?? 'shader')
    gl.attachShader(program, shader)
  }
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program) ?? 'link')
  return program
}
</script>

<template>
  <canvas ref="canvas" class="immersive" :width="DISPLAY_WIDTH_PX" :height="DISPLAY_HEIGHT_PX" />
  <video v-if="media.state.url" ref="video" :key="media.state.url" :src="media.state.url" class="source" playsinline />
</template>

<style scoped>
.immersive {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.source {
  display: none;
}
</style>
