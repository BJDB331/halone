# XREAL One series protocol

The One (`3318:0438`) and One Pro (`3318:0436`) do not stream sensors over HID
like the Air series. They bring up a USB network link (CDC NCM, and on the 1S
also ECM) and serve TCP on it. Nobody on this project has run this against
hardware yet: everything below comes from the sources at the end, plus one real
One Pro capture (`core-glasses/src/test/resources/xrealone/`).

## Link

| | NCM | ECM |
|---|---|---|
| Glasses | `169.254.2.1` | `169.254.1.1` |
| Host (DHCP from the glasses) | `169.254.2.x/24` | `169.254.1.x/24` |

On Android the link shows up as a `Network` with a link-local address, and
sockets must come from that network's `socketFactory`. No USB permission is
needed. Whether a given phone's kernel brings the link up at all is unknown.

The glasses must be in **Follow** mode with **Stabilizer off**, set from their
own menu. The host can neither read nor change either. With Stabilizer on, one
project reports port 52998 staying closed, and another that the glasses re-centre
the image themselves on top of whatever the host renders.

Ports: `52998` reports, `52999` control, `52996` vsync timestamps (format
unknown), `52997` camera.

## Framing

Every message on both ports:

| Offset | Size | |
|---|---|---|
| 0 | 2 | message id, big-endian |
| 2 | 4 | payload length, big-endian |
| 6 | n | payload |

## Reports (52998)

They stream as soon as the port is open. Message id `0x2836`, payload 128 bytes,
little-endian:

| Payload offset | Type | |
|---|---|---|
| `0x00` | u64 | device id |
| `0x08` | u64 | device timestamp, ns |
| `0x18` | u32 | report type: `0x0B` IMU, `0x04` magnetometer |
| `0x1C` | 3×f32 | gyro, rad/s |
| `0x28` | 3×f32 | accel, m/s² |
| `0x34` | 3×f32 | magnetometer, uncalibrated |
| `0x40` | f32 | temperature, °C |
| `0x45` | u24 | frame counter, per report type |

The fields that don't belong to a report's type are NaN. In the capture, IMU
reports arrive at 1000 Hz (Δt 999.9 µs ±18 µs), magnetometer reports at
400 Hz, and both counters have no gaps.

**Axes, measured on a 1S:** x right, y down, z forward, so the canonical frame
is `(x, −y, −z)`. Not yet confirmed on a One or One Pro, and the sources
disagree: xr-tools documents +X left/+Y up/+Z back, and one Rust driver remaps
to `(−x, −z, −y)`.

The 1S also found the factory `gyro_bias` doesn't match the live offset, and
the magnetometer unusable without a calibration the glasses don't store. The
driver leaves the magnetometer out.

## Control (52999)

Requests and replies put a u32 big-endian transaction id at the start of the
payload. Requests set its top bit, and replies echo it with the top bit clear.
Unsolicited events have no transaction id. The body is protobuf:

- getter: `18 00`
- numeric setter: `1a <len> 08 <varint>`
- reply: `22 <len> {1: status, 2: value}`. A missing status means success and a
  missing value means 0, so `22 00` is success.

| Id | | Notes |
|---|---|---|
| `0x271F` | get config | about 220 KB of JSON (see below) |
| `0x2729` | get id | string |
| `0x271D` | get firmware version | string |
| `0x271B` / `0x271C` | get / set brightness | set takes 0–9 per xr-tools; get never tried |
| `0x2821` / `0x2822` | get / set input mode | 0 = 2D, 1 = full SBS; verified on a 1S |
| `0x272E` | key event | 64 bytes: u32 key, u32 state (1 down, 2 up) |

Published examples:

```
get config  27 1f 00 00 00 06 80 00 00 01 18 00
set SBS     28 22 00 00 00 08 80 00 00 02 1a 02 08 01   → 22 00
get mode    → 22 02 10 01   (SBS)
```

Switching input mode re-plugs the display for about 1.8 s. TCP stays up.

Refresh rate is **not** settable: `0x275E`/`0x275F` (display configuration)
returned an unknown value and `status 10001` on one-xr's One Pro.

**Only the ids above are ever sent** (`XrealOneControl.ALLOWED_REQUESTS`). The
same firmware exposes USB-function and network toggles that can leave the
glasses unreachable. The HID interface carries firmware update and recovery
commands, and nothing in Halone writes to it.

## Config

Same schema as the Air series blob, so `XrealCalibrationParser` reads it as-is:
`FSN`, `IMU.device_1`, `display.k_*_display`, `target_p/q_*_display` (Hamilton,
in the IMU frame), `display_distortion` (61×35 on the One Pro sample, 61×39 on
the 1S), `glasses_version` 7–8.

The One Pro sample has the displays rotated about 35° about x relative to the
IMU, where the Air's are within a degree. `DeviceCalibration.headFromImu`
carries that into tracking and rendering. The bias vectors are read in the
report frame. Unlike the Air, there's no evidence of a separate factory frame.

When the config can't be read the driver falls back to no calibration, so
rendering falls back to the driver's `Optics`, rather than to an Air's numbers.

## Open questions

- Does the link come up on a given Android phone, and as which interface?
- Axis signs and the mount rotation on a One Pro, worn.
- EDID name and mode list per glasses setting; SBS refresh rates.
- Brightness read and range; what events `0x2766`, `0x278A` and `0x2712` mean.
- Display latency, for `predictionConstantMs` (currently the Air's 85 ms).

## Sources

| | License | Used for |
|---|---|---|
| [Skarian/one-xr](https://github.com/Skarian/one-xr) | MIT | Android link selection, control framing, the capture slice |
| [SamiMitwalli/One-Pro-IMU-Retriever-Demo](https://github.com/SamiMitwalli/One-Pro-IMU-Retriever-Demo) | MIT | report stream |
| [rohitsangwan01/xreal_one_driver](https://github.com/rohitsangwan01/xreal_one_driver) | MIT | report offsets |
| [0xcaff/xr-tools](https://github.com/0xcaff/xr-tools) | none | facts only: report layout, ids, config schema |
| [kevindurb/xreal-linux](https://github.com/kevindurb/xreal-linux) | none | facts only: 1S measurements, byte examples |
| [wheaney/XRLinuxDriver](https://github.com/wheaney/XRLinuxDriver) | GPL-3.0 | USB ids only |

No code was copied from the unlicensed or GPL sources.
