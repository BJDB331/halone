#!/usr/bin/env python3
"""Analyse a `taprecord` session: what a tap on the frame looks like, and how a
threshold detector does against the movements it must ignore.

    adb pull /storage/emulated/0/Android/data/dev.halone.app/files/taps-<stamp>-imu.csv
    adb pull /storage/emulated/0/Android/data/dev.halone.app/files/taps-<stamp>-marks.csv
    python3 tools/tap-analysis.py taps-<stamp>   # needs numpy
"""
import csv
import sys

import numpy as np

HIGH_PASS_MS = 20
ACCEL_THRESHOLD = 10.0
GYRO_THRESHOLD = 0.0
REFRACTORY_S = 0.1
CUE_WINDOW_S = 1.6

stem = sys.argv[1]
d = np.loadtxt(f"{stem}-imu.csv", delimiter=",", skiprows=1)
t = (d[:, 0] - d[0, 0]) / 1e9
gyro, accel = d[:, 2:5], d[:, 5:8]
marks = [((int(r["host_ns"]) - d[0, 0]) / 1e9, r["label"]) for r in csv.DictReader(open(f"{stem}-marks.csv"))]


def high_pass(x):
    n = int(HIGH_PASS_MS * len(t) / t[-1] / 1000)
    kernel = np.ones(n) / n
    return x - np.stack([np.convolve(x[:, i], kernel, mode="same") for i in range(3)], 1)


a_hp, g_hp = high_pass(accel), high_pass(gyro)
a_mag, g_mag = np.linalg.norm(a_hp, axis=1), np.linalg.norm(g_hp, axis=1)
taps = [(at, label) for at, label in marks if label.startswith("tap")]
starts = {label[:-6]: at for at, label in marks if label.endswith("-start")}
ends = {label[:-4]: at for at, label in marks if label.endswith("-end")}

print(f"{len(t)} samples at {len(t) / t[-1]:.0f} Hz over {t[-1]:.0f} s\n")
print("cue               peak a_hp  g_hp near peak  side")
for at, label in taps:
    window = (t >= at) & (t < at + CUE_WINDOW_S)
    i = int(np.argmax(a_mag * window))
    side = "left (+X)" if a_hp[i, 0] > 0 else "right (-X)"
    print(f"{label:16s}  {a_mag[i]:8.1f}  {g_mag[max(0, i - 10):i + 10].max():14.2f}  {side}")

print("\nmovement  peak a_hp  peak g_hp")
for name in starts:
    window = (t > starts[name]) & (t < ends[name])
    print(f"{name:8s}  {a_mag[window].max():9.2f}  {g_mag[window].max():9.2f}")

events, last = [], -1e9
for i in np.where(a_mag > ACCEL_THRESHOLD)[0]:
    if t[i] - last < REFRACTORY_S:
        continue
    last = t[i]
    if g_mag[max(0, i - 10):i + 10].max() > GYRO_THRESHOLD:
        events.append(t[i])

correct = sum(
    len([e for e in events if at < e < at + CUE_WINDOW_S]) == (2 if "double" in label else 1) for at, label in taps
)
false = sum(len([e for e in events if starts[k] < e < ends[k]]) for k in starts)
print(f"\ndetector a>{ACCEL_THRESHOLD} and g>{GYRO_THRESHOLD}, {REFRACTORY_S * 1000:.0f} ms refractory: "
      f"{correct}/{len(taps)} cues counted right, {false} false events during movements")
