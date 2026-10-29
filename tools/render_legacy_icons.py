"""Renders the launcher icon bitmaps for Android 7.x from the same geometry as the adaptive icon
(res/drawable/ic_launcher_*.xml). Android 8+ uses the adaptive icon; run this again whenever the
mark changes. Needs Pillow: python tools/render_legacy_icons.py
"""
import math
import os

from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SUPERSAMPLE = 8

GROUND = (0x14, 0x10, 0x19, 255)
VIOLET = (0xC7, 0x9B, 0xFF, 255)
AMBER = (0xF5, 0xB8, 0x3D, 255)
TEAL = (0x2E, 0xD9, 0xC3, 255)

# On the 108-unit adaptive canvas: (centre x, radius, colour); every arc spans -45..45 degrees around y = 54.
DOT = (38.0, 54.0, 6.0)
ARCS = [(40.0, 14.0, VIOLET), (42.05, 25.0, AMBER), (44.1, 36.0, TEAL)]
STROKE = 5.5


def render(size, round_shape):
    # A legacy icon shows the adaptive canvas's inner 72 units.
    unit = size * SUPERSAMPLE / 72.0
    px = size * SUPERSAMPLE
    img = Image.new("RGBA", (px, px), GROUND)
    draw = ImageDraw.Draw(img)

    def at(x, y):
        return ((x - 18) * unit, (y - 18) * unit)

    cx, cy, r = DOT
    x0, y0 = at(cx - r, cy - r)
    x1, y1 = at(cx + r, cy + r)
    draw.ellipse([x0, y0, x1, y1], fill=VIOLET)
    half = STROKE / 2
    for acx, ar, colour in ARCS:
        x0, y0 = at(acx - ar - half, 54 - ar - half)
        x1, y1 = at(acx + ar + half, 54 + ar + half)
        draw.arc([x0, y0, x1, y1], -45, 45, fill=colour, width=round(STROKE * unit))
        # Round caps.
        for angle in (-45, 45):
            ex = acx + ar * math.cos(math.radians(angle))
            ey = 54 + ar * math.sin(math.radians(angle))
            a0, b0 = at(ex - half, ey - half)
            a1, b1 = at(ex + half, ey + half)
            draw.ellipse([a0, b0, a1, b1], fill=colour)

    mask = Image.new("L", (px, px), 0)
    # The round icon's circle fills the image (launchers and lint expect that); the square one is inset a little.
    inset = 0 if round_shape else round(px * 0.04)
    box = [inset, inset, px - inset - 1, px - inset - 1]
    if round_shape:
        ImageDraw.Draw(mask).ellipse(box, fill=255)
    else:
        ImageDraw.Draw(mask).rounded_rectangle(box, radius=round(px * 0.22), fill=255)
    img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)


for density, size in SIZES.items():
    folder = os.path.join(RES, "mipmap-" + density)
    render(size, False).save(os.path.join(folder, "ic_launcher.png"))
    render(size, True).save(os.path.join(folder, "ic_launcher_round.png"))
print("rendered", ", ".join(SIZES))
