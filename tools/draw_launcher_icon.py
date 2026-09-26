#!/usr/bin/env python3
"""Draws Saldo's mark: the launcher icon's layers and every drawing made from them.

    python3 tools/draw_launcher_icon.py

The mark is Chiaro's ring, as Passo carries it (committente, 26 set 2026, ADR 53): the same
ring (radius 21, stroke 10) on the same warm white, cut by an amber emblem the way Chiaro's
sun and Passo's shoe print cut theirs. Saldo's emblem is a coin, at the lower right, where
neither sister has hers. The ring is two halves, because a saldo is what comes in against what
goes out: the upper half in the brand's sea green (income), the lower in a soft brick
(expenses). The coin sits where the expenses begin, and a thin cut marks the other junction,
so the two halves survive in the themed (monochrome) icon too. Each half runs light to dark
clockwise; the halves differ in hue and in lightness, so they stay apart under deuteranopia
and in grey.

Re-running this script IS the drawing. It writes, into app/src/main/res/drawable:
- ic_launcher_foreground.xml, ic_launcher_monochrome.xml: the adaptive icon's layers;
- ic_app_icon_ring.xml, ic_app_icon_coin.xml: the same foreground in two pieces, for the
  welcome page's animation (the coin drops into its place on the ring);
- ic_stat_saldo.xml: the status-bar icon, the mark restated for 24 dp.
None of them is edited by hand. Only the standard library is used.
"""

import math
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"

CENTRE = 54.0
RING_RADIUS = 21.0  # Chiaro's
RING_WIDTH = 10.0  # Chiaro's
COIN_ANGLE = 40.0  # degrees clockwise from three o'clock: Chiaro's sun is at -40, Passo's print at 220
JOIN_ANGLE = 220.0  # the other junction of the two halves, opposite the coin
COIN_RADIUS = 8.2  # Chiaro's sun
GAP_RADIUS = 11.2  # Chiaro's: the clear band around the emblem, where it cuts the ring
RIM_OUTER, RIM_INNER = 6.3, 5.0  # the coin's rim, a thin ring of the ground
SLIT_WIDTH = 2.6  # the cut at the other junction: Passo's gap, as a straight band

INCOME = ("5CC2BA", "12807D")  # sea green, light to dark along the half
EXPENSE = ("EFA595", "CB6653")  # soft brick, light to dark along the half
AMBER = ("FFC658", "EF8618")  # the family's emblem amber (Chiaro's sun, Passo's print)

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Written by tools/draw_launcher_icon.py: change the script and run it, never this file.
     {what} -->
"""


def f(v):
    return f"{v:.2f}"


def point(deg, r=RING_RADIUS, c=CENTRE):
    a = math.radians(deg)
    return c + r * math.cos(a), c + r * math.sin(a)


def circle(cx, cy, r):
    return f"M {f(cx - r)},{f(cy)} a {f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a {f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 z"


def mix(a, b, t):
    ca = [int(a[i:i + 2], 16) for i in (0, 2, 4)]
    cb = [int(b[i:i + 2], 16) for i in (0, 2, 4)]
    return "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(ca, cb))


def slit(deg, c=CENTRE, inner=12.0, outer=30.0, width=SLIT_WIDTH):
    ux, uy = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    px, py = -uy, ux
    corners = [
        (c + ux * r + px * s * width / 2, c + uy * r + py * s * width / 2)
        for r, s in ((inner, -1), (outer, -1), (outer, 1), (inner, 1))
    ]
    return "M " + " L ".join(f"{f(x)},{f(y)}" for x, y in corners) + " Z"


def ring_clip(c=CENTRE, ring=RING_RADIUS, gap=GAP_RADIUS, slit_w=SLIT_WIDTH, half=RING_WIDTH):
    """The canvas less the band around the coin and the cut at the other junction."""
    cx, cy = point(COIN_ANGLE, ring, c)
    size = 2 * c
    return (
        f"M 0,0 H {f(size)} V {f(size)} H 0 Z {circle(cx, cy, gap)} "
        f"{slit(JOIN_ANGLE, c, ring - half, ring + half, slit_w)}"
    )


def sweep_items():
    """The two halves as one sweep: each light to dark clockwise, the seams in the cuts."""

    def income(a):
        return mix(INCOME[0], INCOME[1], ((a - JOIN_ANGLE) % 360) / 180)

    stops = [
        (0.0, income(0.0)),
        (COIN_ANGLE, INCOME[1]),
        (COIN_ANGLE + 0.2, EXPENSE[0]),
        (JOIN_ANGLE, EXPENSE[1]),
        (JOIN_ANGLE + 0.2, INCOME[0]),
        (360.0, income(360.0)),
    ]
    return "\n".join(
        f'                <item android:offset="{d / 360:.4f}" android:color="#FF{c}"/>' for d, c in stops
    )


def ring_group():
    return f"""<group>
    <clip-path android:pathData="{ring_clip()}"/>
    <path android:pathData="{circle(CENTRE, CENTRE, RING_RADIUS)}" android:strokeWidth="{f(RING_WIDTH)}" android:fillColor="#00000000">
        <aapt:attr name="android:strokeColor">
            <gradient android:type="sweep" android:centerX="{f(CENTRE)}" android:centerY="{f(CENTRE)}">
{sweep_items()}
            </gradient>
        </aapt:attr>
    </path>
</group>"""


def coin_d(c=CENTRE, ring=RING_RADIUS, outer=COIN_RADIUS, rim=(RIM_OUTER, RIM_INNER)):
    x, y = point(COIN_ANGLE, ring, c)
    d = circle(x, y, outer)
    if rim:
        d += " " + circle(x, y, rim[0]) + " " + circle(x, y, rim[1])
    return d


def coin_path():
    x, y = point(COIN_ANGLE)
    return f"""<path android:fillType="evenOdd" android:pathData="{coin_d()}">
    <aapt:attr name="android:fillColor">
        <gradient android:type="linear" android:startX="{f(x - 6)}" android:startY="{f(y - 6)}" android:endX="{f(x + 6)}" android:endY="{f(y + 6)}">
            <item android:offset="0" android:color="#FF{AMBER[0]}"/>
            <item android:offset="1" android:color="#FF{AMBER[1]}"/>
        </gradient>
    </aapt:attr>
</path>"""


def vector(body, size=108, viewport=108, aapt=True):
    ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    return f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{viewport}"
    android:viewportHeight="{viewport}">
{body}
</vector>
"""


def write(name, what, text):
    (RES / name).write_text(HEADER.format(what=what) + text)


def main():
    write(
        "ic_launcher_foreground.xml",
        "The mark: Chiaro's ring in two halves (income above, expenses below), cut by an amber\n"
        "     coin at the lower right and by a thin band at the other junction. Everything sits\n"
        "     inside the 33-unit safe circle of every launcher mask (ring edge 26, coin edge 29.2).",
        vector(ring_group() + "\n" + coin_path()),
    )
    mono = f"""<group>
    <clip-path android:pathData="{ring_clip()}"/>
    <path android:pathData="{circle(CENTRE, CENTRE, RING_RADIUS)}" android:strokeWidth="{f(RING_WIDTH)}" android:strokeColor="#FF000000" android:fillColor="#00000000"/>
</group>
<path android:fillType="evenOdd" android:fillColor="#FF000000" android:pathData="{coin_d()}"/>"""
    write(
        "ic_launcher_monochrome.xml",
        "The themed icon (Android 13+): the foreground's shapes in one ink, which the system\n"
        "     tints. The cut keeps the two halves apart where the colours are gone.",
        vector(mono, aapt=False),
    )
    write(
        "ic_app_icon_ring.xml",
        "The welcome page's first piece: the foreground's ring alone, on the same canvas.",
        vector(ring_group()),
    )
    write(
        "ic_app_icon_coin.xml",
        "The welcome page's second piece: the foreground's coin alone, on the same canvas.",
        vector(coin_path()),
    )
    # The status bar: 24 units, the ring and the coin scaled by 24/60 about the centre, with
    # the strokes nudged where 24 dp asks (Chiaro's rule: under 3.5 a stroke breaks up). The
    # coin is a plain disc: a rim at this size is a smudge.
    k = 24 / 60
    c, ring, width = 12.0, RING_RADIUS * k + 0.1, 3.6
    stat = f"""<group>
    <clip-path android:pathData="{ring_clip(c, ring, GAP_RADIUS * k + 0.2, 1.3, width)}"/>
    <path android:pathData="{circle(c, c, ring)}" android:strokeWidth="{f(width)}" android:strokeColor="#FFFFFFFF" android:fillColor="#00000000"/>
</group>
<path android:fillColor="#FFFFFFFF" android:pathData="{coin_d(c, ring, COIN_RADIUS * k + 0.1, None)}"/>"""
    write(
        "ic_stat_saldo.xml",
        "Status-bar icon: the system renders only the alpha channel. The launcher icon's\n"
        "     mark restated for 24 dp, as Chiaro's and Passo's are: the ring, its two cuts, the coin.",
        vector(stat, size=24, viewport=24, aapt=False),
    )


if __name__ == "__main__":
    main()
