#!/usr/bin/env python3
"""Report on an XREAL factory calibration blob.

The blob is **JSON**, not the protobuf the teardown predicted, and it is
complete and self-describing — so this is a report rather than the
field-guessing tool the first draft was. Confirmed against an Air 2 Pro
(FSN E434X00248, config version 5.0, 38862 bytes).

What it prints, and why each number matters:

  * FoV from the display intrinsics. The frustum is **asymmetric** — the
    principal point is ~20 px above centre — so each edge subtends a different
    angle and a symmetric projection cannot reproduce it.
  * IPD and eye pose relative to the IMU, including the mounting rotation. The
    sensor board is not square to the displays.
  * Factory accelerometer and gyroscope bias, and the noise figures the
    vendor's own filter is tuned against.
  * Distortion grid statistics: how far the optics actually move a pixel.

Usage
  walk-calibration.py BLOB               # the report
  walk-calibration.py BLOB --tree        # every key, arrays summarised
  walk-calibration.py BLOB --distortion  # the displacement field as a map
  walk-calibration.py BLOB --json-path display.k_left_display
"""

from __future__ import annotations

import argparse
import json
import math
import sys


def flatten(x, out=None):
    out = [] if out is None else out
    if isinstance(x, list):
        for y in x:
            flatten(y, out)
    else:
        out.append(x)
    return out


def quat_to_euler_deg(q):
    """Hamilton (qx, qy, qz, qw), as the blob's own comment specifies."""
    x, y, z, w = q
    roll = math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y))
    pitch = math.asin(max(-1.0, min(1.0, 2 * (w * y - z * x))))
    yaw = math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z))
    return [math.degrees(v) for v in (roll, pitch, yaw)]


def fov_from_k(k, width, height):
    """Half-angles to each edge, kept separate because they are not equal."""
    fx, cx, fy, cy = k[0], k[2], k[4], k[5]
    left = math.degrees(math.atan(cx / fx))
    right = math.degrees(math.atan((width - cx) / fx))
    up = math.degrees(math.atan(cy / fy))
    down = math.degrees(math.atan((height - cy) / fy))
    diag = 2 * math.degrees(math.atan(math.hypot(width / (2 * fx), height / (2 * fy))))
    return dict(fx=fx, fy=fy, cx=cx, cy=cy, left=left, right=right, up=up,
                down=down, horizontal=left + right, vertical=up + down, diagonal=diag)


def grid(dd):
    """(rows, cols, [(ideal_x, ideal_y, actual_x, actual_y), ...]) row-major."""
    flat = flatten(dd["data"])
    rows, cols = dd["num_row"], dd["num_col"]
    stride = len(flat) // (rows * cols)
    points = [tuple(flat[i:i + stride]) for i in range(0, len(flat), stride)]
    return rows, cols, points


def report(blob) -> None:
    disp = blob["display"]
    width, height = disp["resolution"]

    print(f"FSN {blob['FSN']}   glasses_ID {blob['glasses_ID']}   "
          f"config v{blob['configuration_file_version']}   "
          f"calibrated {blob['last_modified_time']}")
    print(f"{disp['num_of_displays']} displays at {width:.0f}x{height:.0f} each, "
          f"{blob['IMU']['num_of_imus']} IMU, "
          f"{blob['RGB_camera']['num_of_cameras']} RGB / "
          f"{blob['SLAM_camera']['num_of_cameras']} SLAM cameras")

    print("\n--- field of view (from the display intrinsics) ---")
    for side in ("left", "right"):
        f = fov_from_k(disp[f"k_{side}_display"], width, height)
        print(f" {side:5s} H {f['horizontal']:6.3f}°   V {f['vertical']:6.3f}°   "
              f"diag {f['diagonal']:6.3f}°")
        print(f"       edges: left {f['left']:.3f}  right {f['right']:.3f}  "
              f"up {f['up']:.3f}  down {f['down']:.3f}")
        print(f"       fx {f['fx']:.2f}  fy {f['fy']:.2f}  "
              f"principal point {f['cx']:.1f},{f['cy']:.1f} "
              f"({f['cx'] - width / 2:+.1f},{f['cy'] - height / 2:+.1f} from centre)")
    asym = abs(fov_from_k(disp["k_left_display"], width, height)["up"] -
               fov_from_k(disp["k_left_display"], width, height)["down"])
    print(f" vertical asymmetry {asym:.3f}° — a symmetric frustum cannot express this")

    print(f"\n--- geometry, relative to the {disp['target_type']} ---")
    pl, pr = disp["target_p_left_display"], disp["target_p_right_display"]
    print(f" left  eye  {[round(v, 6) for v in pl]} m")
    print(f" right eye  {[round(v, 6) for v in pr]} m")
    print(f" IPD {math.dist(pl, pr) * 1000:.2f} mm")
    for side in ("left", "right"):
        roll, pitch, yaw = quat_to_euler_deg(disp[f"target_q_{side}_display"])
        print(f" {side:5s} display vs {disp['target_type']}: "
              f"roll {roll:+.3f}°  pitch {pitch:+.3f}°  yaw {yaw:+.3f}°")

    imu = blob["IMU"]["device_1"]
    print("\n--- IMU ---")
    ab, gb = imu["accel_bias"], imu["gyro_bias"]
    print(f" accel bias {[round(v, 6) for v in ab]} m/s²  "
          f"(|b| {math.dist(ab, [0, 0, 0]):.4f} = "
          f"{math.dist(ab, [0, 0, 0]) / 9.81 * 100:.2f}% of g)")
    print(f" gyro  bias {[round(v, 7) for v in gb]} rad/s  "
          f"= {[round(math.degrees(v), 4) for v in gb]} °/s")
    print(f" scale accel {imu['scale_accel']} gyro {imu['scale_gyro']} — "
          f"{'pure offset, no scale error' if imu['scale_accel'] == [1.0, 1.0, 1.0] else 'scaled'}")
    print(f" skew  accel {imu['skew_accel']} gyro {imu['skew_gyro']}")
    print(f" noises {imu['imu_noises']}  (gyro n/rw, accel n/rw)")
    print(f" gyro->mag rotation {imu['gyro_q_mag']} = "
          f"{[round(v, 1) for v in quat_to_euler_deg(imu['gyro_q_mag'])]}°")
    print(f" mag bias {imu['mag_bias']} scale {imu['scale_mag']}"
          f"{'   (all identity — no factory mag calibration stored)' if imu['mag_bias'] == [0.0, 0.0, 0.0] else ''}")

    print("\n--- display distortion ---")
    for side in ("left", "right"):
        dd = blob["display_distortion"][f"{side}_display"]
        rows, cols, pts = grid(dd)
        disps = [math.hypot(p[2] - p[0], p[3] - p[1]) for p in pts]
        print(f" {side:5s} type {dd['type']}  {rows}x{cols} grid, {len(pts)} points")
        print(f"       displacement: max {max(disps):.2f} px, mean {sum(disps) / len(disps):.2f} px, "
              f"{max(disps) / width * 100:.2f}% of width")


def tree(node, path="") -> None:
    if isinstance(node, dict):
        for key, value in node.items():
            tree(value, f"{path}.{key}" if path else key)
    elif isinstance(node, list):
        flat = flatten(node)
        nums = [v for v in flat if isinstance(v, (int, float))]
        shape, cur = [], node
        while isinstance(cur, list):
            shape.append(len(cur))
            cur = cur[0] if cur else None
        if len(nums) <= 12:
            print(f"{path:52s} {shape} {[round(v, 6) for v in nums]}")
        else:
            print(f"{path:52s} {shape} n={len(nums)} "
                  f"min={min(nums):.6g} max={max(nums):.6g}")
    else:
        print(f"{path:52s} {node!r}")


def distortion_map(blob) -> None:
    for side in ("left", "right"):
        dd = blob["display_distortion"][f"{side}_display"]
        rows, cols, pts = grid(dd)
        print(f"\n{side} display — displacement magnitude in pixels, "
              f"{rows} rows x {cols} cols")
        for r in range(rows):
            line = []
            for c in range(cols):
                p = pts[r * cols + c]
                line.append("%4.1f" % math.hypot(p[2] - p[0], p[3] - p[1]))
            print("  " + " ".join(line))


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("blob")
    ap.add_argument("--tree", action="store_true", help="every key, arrays summarised")
    ap.add_argument("--distortion", action="store_true",
                    help="the displacement field as a grid of magnitudes")
    ap.add_argument("--json-path", metavar="P", help="print one value, e.g. display.resolution")
    args = ap.parse_args()

    raw = open(args.blob, "rb").read()
    try:
        blob = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        print(f"{args.blob}: {len(raw)} bytes, and it is not valid JSON ({exc}).",
              file=sys.stderr)
        print("Check the raw reply log from the `calib` development action — the blob may have "
              "been assembled with the wrong framing, or truncated.", file=sys.stderr)
        return 1

    if args.json_path:
        node = blob
        for part in args.json_path.split("."):
            node = node[part]
        print(json.dumps(node, indent=2))
        return 0
    if args.tree:
        tree(blob)
        return 0
    if args.distortion:
        distortion_map(blob)
        return 0
    report(blob)
    return 0


if __name__ == "__main__":
    sys.exit(main())
