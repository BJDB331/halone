#!/usr/bin/env python3
"""Writes a two-picture MPO from a left and a right JPEG, with the MP index a 3DS writes."""
import struct
import sys

left, right, out = (open(sys.argv[1], 'rb').read(), open(sys.argv[2], 'rb').read(), sys.argv[3])
assert left[:2] == b'\xff\xd8' and right[:2] == b'\xff\xd8'

IFD_ENTRIES = 3
ENTRIES_OFFSET = 8 + 2 + IFD_ENTRIES * 12 + 4
TIFF_LENGTH = ENTRIES_OFFSET + 2 * 16
SEGMENT_LENGTH = 2 + 4 + TIFF_LENGTH
TIFF_START = 2 + 4 + 4
first_size = len(left) + 2 + SEGMENT_LENGTH


def segment():
    tiff = b'II*\x00' + struct.pack('<I', 8)
    tiff += struct.pack('<H', IFD_ENTRIES)
    tiff += struct.pack('<HHI4s', 0xB000, 7, 4, b'0100')
    tiff += struct.pack('<HHII', 0xB001, 4, 1, 2)
    tiff += struct.pack('<HHII', 0xB002, 7, 32, ENTRIES_OFFSET)
    tiff += struct.pack('<I', 0)
    tiff += struct.pack('<IIIHH', 0x20030000, first_size, 0, 0, 0)
    tiff += struct.pack('<IIIHH', 0x00020002, len(right), first_size - TIFF_START, 0, 0)
    assert len(tiff) == TIFF_LENGTH
    return b'\xff\xe2' + struct.pack('>H', SEGMENT_LENGTH) + b'MPF\x00' + tiff


with open(out, 'wb') as file:
    file.write(b'\xff\xd8' + segment() + left[2:] + right)
