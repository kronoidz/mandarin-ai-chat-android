#!/usr/bin/env python3
"""Extract kMandarin (pinyin) readings from the Unihan database and
generate a compact binary dictionary for embedding in the Android app.

Usage:
    python3 tools/generate_pinyin_dict.py [unihan_readings_path] [output_path]

Defaults:
    unihan_readings_path = /home/ale/projects/Unihan/Unihan_Readings.txt
    output_path          = app/src/main/assets/pinyin_dict.bin

Binary format (little-endian, repeated per entry):
    uint32    codepoint
    uint8     pinyin_utf8_byte_length
    uint8[]   pinyin_utf8_bytes
"""

import os
import re
import struct
import sys


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)

    unihan_path = sys.argv[1] if len(sys.argv) > 1 else \
        os.path.join(os.path.dirname(project_root), "Unihan", "Unihan_Readings.txt")
    output_path = sys.argv[2] if len(sys.argv) > 2 else \
        os.path.join(project_root, "app", "src", "main", "assets", "pinyin_dict.bin")

    entries = []
    with open(unihan_path, "r", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            parts = line.strip().split("\t")
            if len(parts) < 3 or parts[1] != "kMandarin":
                continue
            codepoint_str = parts[0]  # e.g. "U+4E00"
            pinyin = parts[2].strip()
            try:
                codepoint = int(codepoint_str[2:], 16)
            except ValueError:
                continue
            entries.append((codepoint, pinyin))

    entries.sort(key=lambda e: e[0])

    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    with open(output_path, "wb") as f:
        for cp, py in entries:
            py_bytes = py.encode("utf-8")
            f.write(struct.pack("<I", cp))
            f.write(struct.pack("B", len(py_bytes)))
            f.write(py_bytes)

    print(f"Wrote {len(entries)} entries to {output_path}")


if __name__ == "__main__":
    main()
