#!/usr/bin/env python3
"""Draws Saldo's mark: the launcher icon's layers and every drawing made from them.

    python3 tools/draw_launcher_icon.py

The mark is a ring (committente, 26 set 2026, ADR 53): radius 21, stroke 10, on a warm
white, cut by an amber emblem. Saldo's emblem is two coins, at the lower right: the front one
with its rim, the back one up and to the left of it, a band of the ground between them (ADR 53,
18 ott 2026, the family's icon review: one coin with a rim read as a target, a button; two read
as money, and the emblem is no longer one more disc). The ring is two
halves, because a saldo is what comes in against what
goes out: the upper half in the brand's sea green (income), the lower in a soft brick
(expenses). The coins sit where the expenses begin, and a thin cut marks the other junction,
so the two halves survive in the themed (monochrome) icon too. Each half runs light to dark
clockwise; the halves differ in hue and in lightness, so they stay apart under deuteranopia
and in grey.

Re-running this script IS the drawing. It writes, into app/src/main/res/drawable:
- ic_launcher_foreground.xml, ic_launcher_monochrome.xml: the adaptive icon's layers;
- ic_app_icon_ring.xml, ic_app_icon_coin.xml: the same foreground in two pieces, for the
  welcome page's animation (the coins drop into their place on the ring);
- ic_stat_saldo.xml: the status-bar icon, the two coins alone (no ring: at 24 dp the status bar
  shows the object, the launcher the mark).
None of them is edited by hand. Only the standard library is used.
"""

import math
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"

CENTRE = 54.0
RING_RADIUS = 21.0
RING_WIDTH = 10.0
COIN_ANGLE = 40.0  # degrees clockwise from three o'clock: the lower right
JOIN_ANGLE = 220.0  # the other junction of the two halves, opposite the coins
COIN_RADIUS = 6.9
# Where each coin's centre sits from the emblem's point on the ring: the front one down and to
# the right, the back one up and to the left, so the pair is centred on the point.
FRONT_SHIFT = (1.6, 2.18)
BACK_SHIFT = (-2.76, -2.9)
GAP = 3.0  # the clear band around each coin, where the pair cuts the ring: the sisters' gap
BETWEEN = 1.4  # the band of ground between the front coin and the back one
RIM_OUTER, RIM_INNER = 5.1, 3.9  # the front coin's rim, a thin ring of the ground
SLIT_WIDTH = 2.6  # the cut at the other junction, as a straight band

# Light to dark along each half. Deepened on 18 ott 2026 (the review): the old pair, 5CC2BA to
# 12807D and EFA595 to CB6653, was the palest ring of the family (Oklab L 0.68 on average); this
# one is 0.64, between the sisters', and stays apart under deuteranopia (grey-blue against khaki,
# the seams as far apart as before or further).
INCOME = ("43B3AA", "0E7672")  # sea green
EXPENSE = ("E99280", "C05741")  # soft brick
AMBER = ("FFC658", "EF8618")  # the emblem's amber
AMBER_REACH = 7.0  # how far from the emblem's point the amber runs, along the diagonal

# The status bar's coins, on the 24-unit grid: front and back centres, radius, rim.
STAT_FRONT, STAT_BACK, STAT_RADIUS = (14.6, 14.4), (9.2, 9.4), 6.4
STAT_RIM = (4.8, 3.5)

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
    # Walked against the canvas (negative area), so it is a hole under the nonzero rule: walked
    # the canvas's way it filled itself back in, and the slit never showed until 18 ott 2026.
    if sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(corners, corners[1:] + corners[:1])) > 0:
        corners.reverse()
    return "M " + " L ".join(f"{f(x)},{f(y)}" for x, y in corners) + " Z"


def coin_centres():
    x, y = point(COIN_ANGLE)
    return (x + FRONT_SHIFT[0], y + FRONT_SHIFT[1]), (x + BACK_SHIFT[0], y + BACK_SHIFT[1])


def ring_clips():
    """The canvas less the band round each coin and the cut at the other junction. Two groups:
    nested clips intersect, so the ring keeps what lies outside both bands (in one clip, the two
    overlapping holes would fill each other in)."""
    front, back = coin_centres()
    canvas = "M 0,0 H 108 V 108 H 0 Z"
    return [
        f"{canvas} {circle(*front, COIN_RADIUS + GAP)} {slit(JOIN_ANGLE, CENTRE, RING_RADIUS - RING_WIDTH, RING_RADIUS + RING_WIDTH)}",
        f"{canvas} {circle(*back, COIN_RADIUS + GAP)}",
    ]


def clipped(clips, body):
    opened = "".join(f'<group>\n    <clip-path android:pathData="{c}"/>\n' for c in clips)
    return opened + body + "\n" + "</group>\n" * len(clips)


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


def ring_group(stroke=None):
    stroke = stroke or f""">
        <aapt:attr name="android:strokeColor">
            <gradient android:type="sweep" android:centerX="{f(CENTRE)}" android:centerY="{f(CENTRE)}">
{sweep_items()}
            </gradient>
        </aapt:attr>
    </path>"""
    return clipped(
        ring_clips(),
        f'    <path android:pathData="{circle(CENTRE, CENTRE, RING_RADIUS)}" android:strokeWidth="{f(RING_WIDTH)}" android:fillColor="#00000000"{stroke}',
    )


def coins(fill, front, back, radius, rim, between, canvas):
    """The back coin, less the band round the front one, then the front coin with its rim."""
    hole = f"M 0,0 H {f(canvas)} V {f(canvas)} H 0 Z {circle(*front, radius + between)}"
    back_coin = clipped([hole], f'<path android:pathData="{circle(*back, radius)}"{fill}')
    front_d = f"{circle(*front, radius)} {circle(*front, rim[0])} {circle(*front, rim[1])}"
    return back_coin + f'<path android:fillType="evenOdd" android:pathData="{front_d}"{fill}'


def amber():
    x, y = point(COIN_ANGLE)
    return f""">
    <aapt:attr name="android:fillColor">
        <gradient android:type="linear" android:startX="{f(x - AMBER_REACH)}" android:startY="{f(y - AMBER_REACH)}" android:endX="{f(x + AMBER_REACH)}" android:endY="{f(y + AMBER_REACH)}">
            <item android:offset="0" android:color="#FF{AMBER[0]}"/>
            <item android:offset="1" android:color="#FF{AMBER[1]}"/>
        </gradient>
    </aapt:attr>
</path>"""


def coin_path(fill=None):
    front, back = coin_centres()
    return coins(fill or amber(), front, back, COIN_RADIUS, (RIM_OUTER, RIM_INNER), BETWEEN, 108)


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
        "The mark: a ring in two halves (income above, expenses below), cut by two amber\n"
        "     coins at the lower right and by a thin band at the other junction. Everything sits\n"
        "     inside the 33-unit safe circle of every launcher mask (ring edge 26, coins' edge 30.5).",
        vector(ring_group() + coin_path()),
    )
    ink = ' android:fillColor="#FF000000"/>'
    write(
        "ic_launcher_monochrome.xml",
        "The themed icon (Android 13+): the foreground's shapes in one ink, which the system\n"
        "     tints. The cut keeps the two halves apart where the colours are gone.",
        vector(ring_group(' android:strokeColor="#FF000000"/>') + coin_path(ink), aapt=False),
    )
    write(
        "ic_app_icon_ring.xml",
        "The welcome page's first piece: the foreground's ring alone, on the same canvas.",
        vector(ring_group()),
    )
    write(
        "ic_app_icon_coin.xml",
        "The welcome page's second piece: the foreground's two coins alone, on the same canvas.",
        vector(coin_path()),
    )
    write(
        "ic_stat_saldo.xml",
        "Status-bar icon: the system renders only the alpha channel. The launcher icon's two\n"
        "     coins without the ring: at 24 dp the status bar shows the object.",
        vector(
            coins(' android:fillColor="#FFFFFFFF"/>', STAT_FRONT, STAT_BACK, STAT_RADIUS, STAT_RIM, BETWEEN, 24),
            size=24,
            viewport=24,
            aapt=False,
        ),
    )


if __name__ == "__main__":
    main()
