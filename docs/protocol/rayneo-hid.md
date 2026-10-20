# RayNeo GT-series USB HID protocol

Covers the RayNeo **GT** and **GT Max**, which share a USB identity and a
protocol and differ by a board id.

Assembled from two independent open-source projects that ran a RayNeo GT on
hardware in autumn 2026:

- [Rayneo-Spatial-App](https://github.com/AkshayReddyGujjula/Rayneo-Spatial-App)
  (Apache-2.0) — Windows 11, GT firmware "Sep 7 2026". Its
  `docs/PROTOCOL-NOTES.md` is the primary source.
- [rayneo-gt-spatial-core](https://github.com/davidsrc2/rayneo-gt-spatial-core)
  — macOS on Apple Silicon, GT firmware "Sep 22 2026". It re-measured the
  above and added the anchor-mode and brightness commands.

Halone's driver is written from these notes, not from either project's code.

**Nothing here has been seen by this project yet, and neither source had a GT
Max.** The GT Max entries rest on the board id the first source documents.
Corrections found on hardware should be marked **[measured]**, as in
`xreal-hid.md`.

## Identification

| | Value |
|---|---|
| VID:PID, normal | `3941:AF50` (GT and GT Max) |
| VID:PID, firmware update | `3941:AF51` — not claimed by the driver |
| Interface | vendor HID, usage page `0xFF00`, usage `1`, product string "RayNeo AR Glasses" |
| Board id | `0x40` GT, `0x41` GT Max — byte 2 of every report |

The vendor HID has been seen as `bInterfaceNumber` **0** while the display is
active, and as **5** in a composite configuration that also carried CDC and
DFU interfaces. The driver tries 0, then 5, and keeps whichever answers the
device-info command.

The report descriptor declares 64-byte input and output reports and **no
report ids**. hidapi-based tools write 65 bytes with a leading `0x00`, which
hidapi strips; on a raw Android endpoint the frame starts at the tag.

## Commands

`66 <cmd> <value> 00…`, 64 bytes. Every command is acknowledged.

| cmd | Meaning |
|---|---|
| `00` | Device info. Reply carries the firmware build date as ASCII from byte 23 and the brightness level at byte 41. |
| `01` | Start the IMU stream. |
| `02` | Stop it. The stream **outlives the host process**, so stop it on connect. |
| `08` | Anchor mode: `00` follow, `01` pinned, `02` steady. See below. |
| `09` | Brightness level: `08` is the factory setting, `00` nearly dark. The maximum is not known. |
| `3c`, `3e` | Factory transform and gyro bias, and a thermal bias table. **No reply** on either firmware tried. |
| `07` | No visible effect. |

No command for switching the display to 3D (side-by-side) is known.

### Acknowledgement `99 c8`

| Offset | Content |
|---|---|
| 0–1 | `99 c8` |
| 2 | board id |
| 4–7 | device counter, u32 LE |
| 8 | echoed opcode |

Arrives about 1 ms after the command.

### Anchor modes and the mode button

The GT series runs its own 3DoF on an on-board chip and can stabilise the
image itself: **pinned** holds the screen in place, **steady** smooths it,
**follow** leaves it fixed to the face. Halone does its own world-locking, so
the driver sets **follow** when tracking starts — otherwise every head turn is
compensated twice — and sets **pinned** when it stops, which is what the macOS
project hands back. The current mode cannot be read.

Changing mode recentres pinned; setting pinned while in pinned does nothing.

Pressing the glasses' mode button sends an **unsolicited** `99 c8` echoing
`08`, without saying which mode was chosen. The driver reports it as a button
press and, while tracking, sends follow again.

## IMU report `99 65`

64 bytes, little-endian.

| Offset | Content |
|---|---|
| 0–1 | `99 65` |
| 2 | board id |
| 4 | accel X, Y, Z — float32, m/s² |
| 16 | gyro X, Y, Z — float32, **degrees** per second |
| 28 | temperature — float32, °C |
| 32 | magnetometer X — float32, µT |
| 36 | magnetometer Y — float32, µT |
| 40 | tick — u32, 100 µs per count |
| 48 | a copy of the tick |
| 52 | magnetometer **Z** — float32, µT |

Rate: the tick advances 22, 22, 22, 22, 22, 22, 22, 21 — 21.875 ticks, or
**457 Hz**, not the 476 Hz first reported. Measured on the GT over 20 s with
no gaps.

At rest: |accel| ≈ 9.84 m/s², gyro bias within ±1.5 °/s, |B| ≈ 71 µT, die
about 39 °C. The first ~4 s after stream-on are reported as contaminated.

### Axes

**Forward +X, left +Y, up +Z**, with gravity at rest on +Z — the frame the
macOS project uses with an identity mounting. In Halone's canonical frame that
is `(x, y, z) → (−y, z, −x)`; see `RayneoDriver.AXES`.

**[measured]** on a GT Max: the mapping in the Windows project's notes (right
+X, forward +Y) is a quarter turn out — nodding rolled the scene and the
horizon sat tilted. The signs of the two horizontal axes, and the ~29° board
pitch the macOS project reports, are still to be confirmed.

### Magnetometer

Both projects keep it out of fusion: desk and laptop interference turned it
into a steady yaw spin, and RayNeo's own runtime reportedly ignores it too.
The driver reports it in `magnet` (µT) and leaves `magnetRaw` — which the
heading correction consumes — empty.

## Display

The display enumerates as "SmartGlasses", 1920x1080 at 60 or 120 Hz, mirrored.
In the glasses' 3D mode it offers 3840x1080 side-by-side. Since the host cannot
switch modes, the driver offers no `DisplayControl`, and Halone waits for the
wearer to turn 3D on with the glasses' own controls.

## Optics

| | GT | GT Max |
|---|---|---|
| Panels | | 1920x1080 micro-OLED per eye |
| Optics | birdbath | prism ("Peacock 3.0 Max") |
| Advertised FoV | 46° | 57° or 59°, by source |
| Rendering FoV used | 45.5° | 57° |
| Virtual image distance | ~6 m (derived) | ~6 m (derived) |

On the GT, a rendering FoV tuned by eye landed at a vertical 23° — about 45°
diagonal, within a degree of the box. Nothing equivalent exists for the GT
Max. The image distance is derived from the advertised screen sizes, not
measured.
