# XREAL One Pro motion capture

`one-pro-motions.csv.gz` is real sensor data from an XREAL One Pro worn by a
test-kit tester (session 2026-10-19, test kit 0.6.0, Galaxy SM-S948U1), used
here with the tester's permission. It holds only IMU samples: no calibration,
serial numbers or anything else from the session.

Each segment is one guided movement, from the beep that started it to the end
of its window, in the order the test kit asked for them:

| Segment | What the tester did |
|---|---|
| `rest` | glasses lying still on a table |
| `level` | worn, looking straight ahead, still |
| `turn-left`, `turn-right` | turn the head, pause, back to the middle |
| `look-up`, `look-down` | look up or down, hold, back |
| `tilt-left`, `tilt-right` | ear towards the shoulder, hold, back |

Columns: `segment`, `t_ns` (device clock, from the beep), then gyro `gx gy gz`
in rad/s and accelerometer `ax ay az` in m/s², both in the glasses' **hardware**
frame, exactly as the report stream carries them (`XrealOneReport` offsets
`0x1C` and `0x28`). The canonical-frame mapping is the thing under test.
