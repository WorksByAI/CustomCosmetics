# SPDX-License-Identifier: AGPL-3.0-or-later
"""Original grayscale material atlas; recolored in game, without third-party assets."""
from pathlib import Path
import struct
import zlib
import math

def atlas_pixels():
    pixels = bytearray()
    for y in range(64):
        for x in range(64):
            tile = (y // 16) * 4 + x // 16
            u, v = (x % 16) // 4, (y % 16) // 4
            if tile < 4:  # four broad fabric tones, no single-pixel grain
                shade = (224, 232, 240, 248)[(u + v + tile) % 4]
            elif tile < 8:  # broad feather stripe
                shade = (208, 232, 248, 224)[u]
            elif tile < 12:  # simple metal edge highlight
                shade = (220, 236, 252, 236)[u]
            else:  # large membrane/leaf color blocks
                shade = (208, 224, 240, 232)[(u // 2 + v // 2 + tile) % 4]
            pixels.extend((shade, shade, shade, 255))
    return bytes(pixels)

def detailed_atlas_pixels():
    pixels = bytearray()
    for y in range(256):
        for x in range(256):
            tile = (y // 64) * 4 + x // 64
            u, v = x % 64 / 63, y % 64 / 63
            if tile < 4:  # cloth folds, broad rather than noisy grain
                shade = 211 + int(32 * math.sin(u * math.pi)) + int(5 * v)
            elif tile < 8:  # feather ridge and darker sculpted edge
                shade = 197 + int(53 * (1 - abs(u - .5) * 2))
            elif tile < 12:  # contrasting satin/metal bevels
                shade = 206 + int(45 * max(0, 1 - abs(u - .35) * 1.65))
            else:  # membrane shading without fine mottling
                shade = 206 + int(33 * math.sin(u * math.pi)) + int(9 * v)
            pixels.extend((shade, shade, shade, 255))
    return bytes(pixels)

def png(data=None, size=64):
    data = atlas_pixels() if data is None else data
    rows = b"".join(b"\x00" + data[y * size * 4:(y + 1) * size * 4] for y in range(size))
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")

if __name__ == "__main__":
    target = Path(__file__).resolve().parents[1] / "src/main/resources/assets/customcosmetics/textures/accessories/materials.png"
    target.write_bytes(png())
    target.with_name('detailed-materials.png').write_bytes(png(detailed_atlas_pixels(), 256))
    print("Original pixel and detailed accessory material atlases generated")
