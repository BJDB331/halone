# Halone web environment

Every UI surface of Halone, including the launcher and system apps, is a web app
that talks to the shell over `@halone/sdk`. This workspace holds the system
apps and a **desktop simulator** of the shell, so apps can be built and
debugged without the phone or the glasses. The SDK and the `halone` command
line are in the `../sdk` submodule
([halone-sdk](https://github.com/HaloneXR/halone-sdk)).

```sh
cd web
pnpm run sdk    # install and build ../sdk, which @halone/sdk resolves to
pnpm install
pnpm dev        # simulator on http://127.0.0.1:5173, system apps on :5174
pnpm test       # simulator window manager and system app helpers
pnpm typecheck
pnpm build      # apps/system/dist is what the APK will serve from https://halone.local/
```

## Layout

```
../sdk/              submodule: @halone/sdk (protocol types, app-side client, host-side
                     endpoint) and @halone/cli (`halone dev | pack | install`)
packages/ui/         @halone/ui: shared theme for additive optics
packages/scene/      @halone/scene: core-compositor's scene compiled to JS (generated, committed)
apps/system/         system apps, one Vite multi-page build (launcher, settings, about, files, images, video)
  public/apps.json   the app registry the shell reads
apps/simulator/      desktop stand-in for the native shell
  src/shell/         window manager over @halone/scene, bridge: plain TS, unit-tested
  src/components/    stage, panels, debug sidebar
```

## Halone apps (third-party)

A Halone app is a Vite project with `public/halone.json` (the manifest and
permissions are below). Examples are in
[halone-examples](https://github.com/HaloneXR/halone-examples), and the SSH
client in [halone-ssh](https://github.com/HaloneXR/halone-ssh). Install the CLI
with `npm install -g @halone/cli` (or `cd ../sdk/packages/cli && npm link`
after `pnpm run sdk`, to use the submodule's), turn on **Settings → Developer →
Developer mode** in the simulator or on the glasses, then from the app's
folder:

```sh
halone dev                  # serve with hot reload and run it in the simulator (pnpm dev running)
halone dev --device         # the same on the glasses, through adb reverse
halone pack                 # vite build, then <id>-<version>.halone
halone install x.halone --device --launch
halone uninstall <id> [--device] / halone list [--device]
```

A dev app is granted its manifest's permissions without a prompt and goes
when `halone dev` stops or developer mode is turned off. Calls outside the
grant fail with `permission-denied`. The simulator relays `sockets.*` through
its dev server; DevTools for an app on the glasses are at `chrome://inspect`.

### The manifest

```json
{
  "id": "dev.example.ssh",
  "name": "SSH",
  "version": "1.0.0",
  "description": "Terminal sessions over SSH",
  "icon": "icon.png",
  "entry": "index.html",
  "window": { "width": 1024, "height": 700 },
  "permissions": ["network.tcp", "clipboard"],
  "opens": [],
  "color": "#1f3a5f",
  "minHalone": "2026.10.12"
}
```

- `id`: reverse-DNS, lowercase, used as the origin's host label and the
  install folder; system app ids and `web-` (PWAs) are reserved.
- `version`: `major.minor.patch`; an update needs a higher one.
- `icon`: a PNG in the package, up to 1 MB; 192 px or more.
- `window`: the initial size, 200 to 4096 px each way.
- `color`: the launcher tile's colour, `#rrggbb`; left out, the launcher takes
  it from the icon.
- `minHalone`: the oldest Halone the app runs on, as `major.minor.patch`
  (Halone's versions are dates, such as `2026.10.12`). An older Halone refuses
  to install it, with the error `incompatible`. The simulator does not check it.
- Unknown permissions are an error, so a typo cannot install an app that then
  fails at run time.

The package is a zip with the extension `.halone`, with `halone.json` at its
root. It is unsigned: opening one shows the install prompt with the name,
where it came from and the permissions. An update must keep the id, and asks
again only for permissions it adds.

### Permissions

Every app may, without asking: know about the system, manage its own window,
show menus and dialogs over itself, write the clipboard, open a URL in the
browser, and use the immersive video player for its own files.

| Permission | Grants | Prompt wording |
|---|---|---|
| `network.tcp` | `sockets.*` (TCP, with TLS) | Connect to any device or server |
| `network.udp` | reserved, nothing uses it yet | Send and receive network packets |
| `clipboard` | `clipboard.read` | Read what you copy |
| `files` | `files.*`, `apps.open` | Read and change your files |
| `sensors` | head pose stream, phone motion | Know where you are looking |
| `windows` | `windows.list/focus`, other windows' events | See and switch your open windows |
| `apps` | `apps.list/launch` | See and open your apps |
| `screen` | `screen.capture`, recording | Capture what you see |

Settings, browser history, bookmarks and downloads are never available to
third-party apps.

## The simulator

It renders what one eye of the glasses sees: a 1920x1080 view at the 22.58°
vertical field an Air 2 Pro's calibration renders with, and windows as live
iframes on the 4 m cylinder. Panels are sized as natively, at 75% of the 19°
symmetric figure, so an 800px panel subtends 14.25°, and the ~39° horizontal
field fits about one and a half panels, as on the glasses.

- **Look around**: drag the background, or arrow keys (shift for 10°).
- **Move a window**: drag its bar. Moving a follower keeps it at that offset
  from the arrangement; moving a pinned one moves its place in the room.
- **Keys**: `R` recenter, `L` launcher, `` ` `` toggles the sidebar.
- **Sidebar**: top-down minimap (FoV wedge, panels, arrangement anchor, green
  while following), app launch buttons, URL opener, window list, live settings,
  and a **bridge log** of every request, response and event per window.
- **Room (additive)** blends panels over a faux room with `mix-blend-mode:
  screen`, which is how the optics combine them: black disappears and white
  areas are heavy. Worth a look before shipping any bright UI.

For scripted debugging, `window.__halone` in the simulator page exposes the
`shell` (state and every window-management operation) and the `bridge`
(including `traffic`).

System apps are served from a different origin than the simulator on purpose,
so an app cannot reach into the shell's DOM and has to go through the bridge,
as it will on the device. Opening an app directly at :5174 shows "not running
inside Halone" after 5 s.

## The bridge

One `MessageChannel` per app window. The handshake is the same in the
simulator and on Android, which is the point of it:

1. The host posts `{ halone: 'connect', protocolVersion, windowId }` (as JSON
   text: a `WebMessagePort` carries strings only, so every message is JSON on
   the wire) to the app's window with the port transferred. On the device that is
   `WebView.postWebMessage` once per page load. In the simulator the app first
   posts `{ halone: 'hello' }` to its parent and the host answers, so a page never
   receives a port it would ignore.
2. Requests are `{ type: 'req', id, method, params }`, answered by
   `{ type: 'res', id, ok, result | error }`. Events are
   `{ type: 'evt', name, data }`.

The window identity comes from the port, not from anything the app says, so
`windows.close()` and `windows.setTitle()` act on the caller. Only `app`
windows get a port. `web` windows (arbitrary URLs, Gecko on the device) never
do: system apps are trusted, arbitrary web content is not.

`../sdk/packages/sdk/src/protocol.ts` is the contract; the Kotlin host in
`android/core-shell` (`BridgeHost`, `Shell`) implements it and runs on the
device. Headings on the wire are compass-style (positive right); the Kotlin
side negates native yaw at the boundary. Methods today:

| Namespace | Methods |
|---|---|
| `system` | `info`, `ping` |
| `apps` | `list` (`includeHidden` for apps only opened for a file or by the shell), `launch` (single instance: launching an open app focuses it), `open` (a file, in the app whose `opens` matches its type) |
| `windows` | `list`, `self`, `openUrl`, `focus`, `close`, `setPinned`, `setTitle`, `openedFile`, `setStereoRegion`, `resize`, `setSizeLimits`, `setAutoHide` |
| `files` | `access`, `requestAccess`, `roots`, `list`, `stat` |
| `media` | `open`, `state`, `play`, `pause`, `seek`, `setLayout`, `setRotatable`, `recenter`, `close` |
| `screen` | `capture`, `recording`, `startRecording`, `stopRecording` (the left eye, to `Pictures/Halone` and `Movies/Halone`) |
| `scene` | `recenter`, `headPose` |
| `settings` | `get`, `set` (validated host-side) |

Events: `windows.changed`, `settings.changed`, `window.focusChanged`,
`window.fileOpened`, `files.accessChanged`, `media.changed`,
`screen.recordingChanged`.

### Files

Bytes never cross the bridge. Every `FileEntry` carries a `url` a system app
can put straight into `<img>`, `<video>` or `fetch`; the host serves it with
range requests (`/__files/raw/…` on the simulator). On the device that is
`FileServer`, loopback HTTP behind a per-session token, not
`shouldInterceptRequest`: WebView reaches a range in an intercepted stream by
reading through everything before it, which for the index at the end of a
3 GB video takes half a minute. The app's network security config allows
cleartext to `127.0.0.1` only.
Paths are the host's own and opaque to apps; `parentPath`, `baseName`,
`mimeMatches`, `layoutFromName` and `eyeAspect` in `@halone/sdk` are the helpers.
On the phone, reading shared storage needs "all files access", which
`files.requestAccess()` opens the setting for.

The simulator's storage is `apps/simulator/sample-files` (regenerate with
`apps/simulator/scripts/make-samples.sh`: flat, 360° and side-by-side test
clips, and pictures), or any folder named by `HALONE_FILES`.

### Shaping a window

`windows.resize` changes the caller's size within its limits, keeping its top
or bottom edge (`anchor`) where it is; `windows.setSizeLimits` overrides the
limits its page declared, and `null` restores them. The Video window locks
itself to just its controls this way while it plays immersively.

`windows.setStereoRegion` marks part of the caller's window, in its own
pixels, as a side-by-side or top-bottom picture: each eye is shown its own half
stretched over the whole region, and the rest of the window stays as it is.
Lay the picture out at one eye's shape (`eyeAspect`, `fitBox`) and keep UI out
of the region. The simulator only outlines it.

`windows.setAutoHide(on)` is the `windows.autoHide` setting: every window fades
out once the pointer has been off all of them for
`windows.autoHideDelaySeconds`, and they come back as soon as it points where
one is. The pointer decides, not the gaze, and the dock is not a window.

### The immersive player

`halone.media` plays one video at a time behind every window, decoded by the
host at the file's own resolution: on a big screen (`flat`, 6 m away and
`media.screenWidthDegrees` wide), or around the wearer (`equirect-180`,
`equirect-360`, zoomed out by `media.sphereZoom` around the gaze, and a 2D one
`media.sphereDistanceMetres` away rather than at infinity), each `mono`, `side-by-side` or `top-bottom`, the left eye on
the left or on top. The window that opens it owns it; only it can drive it, it
alone hears `media.changed`, and closing it closes the player. Ordinary 2D
video is just a `<video>` in the page. The simulator is mono and shows the left
eye.

## Writing a system app

```ts
import { useHalone } from '@halone/sdk'

const halone = useHalone()
await halone.windows.openUrl('https://example.com')
const off = halone.on('settings.changed', (settings) => { /* ... */ })
```

`apps/system/src/shared/host.ts` has Vue composables (`useHost`, `useWindows`,
`useSettings`, `useFocus`) that keep reactive state in sync with the events.
Add the page to `apps/system/vite.config.ts` and an entry to
`public/apps.json`. Design for the optics: near-black surfaces, emphasis by
light rather than fill, and nothing under ~20px (one degree is ~56 CSS px).

## Simulator vs device

Deliberately simpler than `core-compositor`, and worth knowing before trusting
it for feel:

- **Following and collision are the native code.** The scene model lives in
  `android/core-scene` (Kotlin Multiplatform) and `@halone/scene` is that
  Kotlin compiled to JS, so the band, drift, catch-up, look-back, focus hold
  and the collision policies are the glasses' own. After changing anything
  in `core-scene`, run `./gradlew :core-scene:syncWebPackage` and commit
  `packages/scene/dist`; Android's unit tests fail while it is stale.
  `test/scene.conformance.test.ts` checks the compiled package against
  traces of the JVM build (scenarios in `fixtures/scene`). The shell negates
  yaw at the boundary, as `Shell.kt` does.
- **Focus follows the hit test**: every frame the mouse's ray through the
  display (or the head in gaze mode) is hit-tested against the scene, as the
  phone pointer is natively. Recenter makes the current heading forward, as
  the tracker does. Still judge feel on the glasses: the mouse is not a head.
- **Angular size is constant per CSS pixel** on both sides: a 1024px-wide app
  is narrower than a 1280px one.
- **Mono**, no lens distortion, no prediction. `panel.distanceMetres` has no
  visible effect in mono, by design: it is a comfort setting.
- Many sites refuse to be framed (`X-Frame-Options`), so some web windows show
  an error here that Gecko would not.
