#!/usr/bin/env python3
"""Cuts a `taprecord` session into the test fixture for TapDetector: each cued
tap with a little context, labelled with the side and how many taps it holds,
and the movements a detector must ignore.

    python3 tools/tap-fixture.py taps-<stamp> android/core-glasses/src/test/resources/taps
"""
import csv
import gzip
import os
import sys

import numpy as np

stem, out = sys.argv[1], sys.argv[2]
d = np.loadtxt(f"{stem}-imu.csv", delimiter=",", skiprows=1)
host = d[:, 0]
marks = [(int(r["host_ns"]), r["label"]) for r in csv.DictReader(open(f"{stem}-marks.csv"))]

k = np.ones(20) / 20
a = d[:, 5:8]
mag = np.linalg.norm(a - np.stack([np.convolve(a[:, i], k, mode="same") for i in range(3)], 1), axis=1)


def rows(i0, i1, segment):
    return [f"{int(d[i, 1])},{d[i, 5]:.3f},{d[i, 6]:.3f},{d[i, 7]:.3f},{segment}" for i in range(i0, i1)]


positives, counts = [], {"L": 0, "R": 0, "D": 0}
for at, label in marks:
    if not label.startswith("tap"):
        continue
    kind = "D" if "double" in label else ("L" if "left" in label else "R")
    window = (host >= at) & (host < at + 1.6e9)
    # The first tap of a double, not the stronger one, or the window misses it.
    peak = int(np.argmax(mag * window)) if kind != "D" else int(np.argmax((mag > 10) & window))
    counts[kind] += 1
    segment = f"{kind}{counts[kind]}-{'2' if kind == 'D' else '1'}{'L' if kind != 'R' else 'R'}"
    positives += rows(peak - 150, peak + (400 if kind == "D" else 150), segment)

negatives = []
starts = {l[:-6]: at for at, l in marks if l.endswith("-start")}
ends = {l[:-4]: at for at, l in marks if l.endswith("-end")}
for name in ("adjust", "talk", "turn"):
    window = np.where((host > starts[name]) & (host < ends[name]))[0]
    negatives += rows(window[0], window[-1], name)

os.makedirs(out, exist_ok=True)
for name, body in (("taps.csv.gz", positives), ("movements.csv.gz", negatives)):
    with gzip.open(os.path.join(out, name), "wt") as f:
        f.write("device_ns,ax,ay,az,segment\n" + "\n".join(body) + "\n")
print(f"{len(positives)} tap rows, {len(negatives)} movement rows -> {out}")
