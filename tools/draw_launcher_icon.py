#!/usr/bin/env python3
"""Draws Saldo's mark: the adaptive icon's three layers and every drawing made from them.

    python3 tools/draw_launcher_icon.py

The mark is THE WALLET AND THE CARD (committente, 9 Nov 2026, when the family's marks were
redrawn as one series; ADR 53): a white wallet with its clasp on the right, and an amber card
standing out of it, its stripe struck in a deeper amber. The money in its containers, which is
what Saldo keeps: a balance for every account, the cash and the cards. The clasp, a tab with a
button cut through it, is what makes the shape a wallet rather than a box.

The series' rules, the same in every app of the family:
- the badge's ground is the app's own colour, here the laguna teal, from its light tone at the
  upper left to its deep tone at the lower right; the symbol is white with one amber accent;
- every shape is lit from the upper left: a linear ramp from its light tone at the upper left of
  its box to a darker one at the lower right (the white to its deep tone, which is still nearly
  white, the amber to its base), over the box's larger side; no shadow (the store and the
  launchers add their own);
- the symbol stays inside the keyline, a circle of KEYLINE around the centre: 56 dp of the 72
  that show, so a round mask leaves 8 dp of air all round and no mask of any launcher reaches it;
- where the overlaps meet, a band of the ground keeps the shapes apart, so the themed icon, which
  has one colour, still shows them.

Re-running this script IS the drawing. It writes, into app/src/main/res/drawable:
- ic_launcher_background.xml, ic_launcher_foreground.xml, ic_launcher_monochrome.xml: the
  adaptive icon's three layers;
- ic_stat_saldo.xml: the status-bar icon of every notification, the same wallet in one ink, its
  bands a little wider so they survive at 24 dp, inside the 2 units of air of the system's icons;
- ic_app_mark.xml: the icon in a round mask as one vector (the ground on a disc, the wallet on
  it), for the widgets' "open Saldo" button and their setup card, which draw it as a picture;
- ic_shortcut_expense.xml, ic_shortcut_income.xml, ic_shortcut_transfer.xml: the launcher
  shortcuts' marks, each arrow in white on a disc of the ground's colours.
None of them is edited by hand. Only the standard library is used, so any Python 3 runs it.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable"

CENTRE = 54.0
KEYLINE = 28.0  # the symbol's reach from the centre: measured 27.5

# The family's palette, the tones this mark uses: (light, base, deep).
LAGUNA = ("2AC3BB", "079390", "026F70")
SUN = ("FFD36A", "FDB528", "F28C17")
WHITE = ("FFFFFF", "F4F7FB", "E3EAF4")
STRIPE_ALPHA = "BF"  # the card's stripe: the amber's deep tone at 75%

CANVAS = "M 0,0 H 108 V 108 H 0 Z"
GROUND_LIGHT_BOX = (18.0, 18.0, 90.0, 90.0)  # the ground lit over the 72 that show

# The drawing, on the 108 grid, then fitted: scaled about the centre, and moved by FIT_SHIFT.
BODY = (28.0, 41.0, 80.0, 77.0, 7.5)  # left, top, right, bottom, corner
CARD = (35.0, 26.5, 69.0, 52.0, 4.0)
CARD_STRIPE = (32.75, 3.5)  # its middle and width, in the part that stands out of the wallet
CLASP = (58.0, 52.0, 84.0, 66.0, 7.0)  # the tab, out past the wallet's right side
BUTTON = (67.0, 59.0, 2.6)  # the clasp's button, cut through: middle and radius
GAP = 2.4  # the band of the ground round the wallet (under the card) and round the clasp
WALLET_LIGHT_BOX = (BODY[0], CARD[1], CLASP[2], BODY[3])  # the white parts lit as one shape
FIT_SCALE = 0.85
# Centred between its box and its weight: by its box the clasp pulls it 1.3 to the right, by its
# weight the body pulls it 1.1 to the left.
FIT_SHIFT = (-0.4, 0.5)

# The status-bar icon: the same drawing at a third of its size, its bands and the button wider so
# they keep at least a unit at 24 dp, centred by its box on the 24-unit grid with 2 units of air.
STAT_SCALE = 0.355
STAT_GAP = 3.4
STAT_BUTTON = 3.1
STAT_STRIPE = 4.0

# The launcher shortcuts: arrows on the 108 grid, each scaled about its own middle into a disc
# of 48. Half the size: the disc is all ground, and the glyph keeps the air an icon has.
SHORTCUT_GLYPH_SCALE = 0.5
SHORTCUTS = {
    "ic_shortcut_expense.xml": (
        "an arrow down: money going out",
        ["M54,78 L38,56 L50,56 L50,32 L58,32 L58,56 L70,56 Z"],
        (54.0, 55.0),
    ),
    "ic_shortcut_income.xml": (
        "an arrow up: money coming in",
        ["M54,30 L70,52 L58,52 L58,76 L50,76 L50,52 L38,52 Z"],
        (54.0, 53.0),
    ),
    "ic_shortcut_transfer.xml": (
        "two opposing arrows: money moving between accounts",
        ["M34,42 L60,42 L60,34 L76,46 L60,58 L60,50 L34,50 Z", "M74,60 L48,60 L48,52 L32,64 L48,76 L48,68 L74,68 Z"],
        (54.0, 55.0),
    ),
}

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Written by tools/draw_launcher_icon.py: change the script and run it, never this file.
     {what} -->
"""


def f(v):
    return f"{v:.2f}"


def lit(box):
    """A shape's light: from the upper-left corner of its box to the lower-right one, along the
    diagonal of a square on the box's larger side, so every shape is lit at the same angle."""
    x0, y0, x1, y1 = box
    cx, cy, r = (x0 + x1) / 2, (y0 + y1) / 2, max(x1 - x0, y1 - y0) / 2
    return (cx - r, cy - r), (cx + r, cy + r)


def ramp(attr, box, light, deep, indent="    "):
    (sx, sy), (ex, ey) = lit(box)
    return (
        f'{indent}<aapt:attr name="android:{attr}">\n'
        f'{indent}    <gradient android:type="linear" android:startX="{f(sx)}" android:startY="{f(sy)}" '
        f'android:endX="{f(ex)}" android:endY="{f(ey)}">\n'
        f'{indent}        <item android:offset="0" android:color="#FF{light}"/>\n'
        f'{indent}        <item android:offset="1" android:color="#FF{deep}"/>\n'
        f"{indent}    </gradient>\n{indent}</aapt:attr>\n"
    )


def shaded(d, box, light, deep, even_odd=False):
    """A shape lit from the upper left."""
    rule = ' android:fillType="evenOdd"' if even_odd else ""
    return f'<path android:pathData="{d}"{rule}>\n{ramp("fillColor", box, light, deep)}</path>\n'


def ink(d, even_odd=False):
    rule = ' android:fillType="evenOdd"' if even_odd else ""
    return f'<path android:pathData="{d}" android:fillColor="#FFFFFFFF"{rule}/>\n'


def vector(body, size=108, viewport=108, aapt=True):
    ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    return (
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
        f'    android:width="{size:g}dp"\n    android:height="{size:g}dp"\n'
        f'    android:viewportWidth="{viewport:g}"\n    android:viewportHeight="{viewport:g}">\n'
        f"{body}</vector>\n"
    )


def fitted(body, scale, shift):
    return (
        f'<group android:pivotX="{f(CENTRE)}" android:pivotY="{f(CENTRE)}" android:scaleX="{scale:g}" '
        f'android:scaleY="{scale:g}" android:translateX="{f(shift[0])}" android:translateY="{f(shift[1])}">\n'
        f"{body}</group>\n"
    )


def clipped(holes, body):
    """The body less each hole, every hole walked against the canvas so the nonzero rule leaves
    it out. One group per hole: nested clips intersect, while two holes that overlapped inside
    one clip would add up their windings and fill each other back in."""
    opened = "".join(f'<group>\n<clip-path android:pathData="{CANVAS} {h}"/>\n' for h in holes)
    return opened + body + "</group>\n" * len(holes)


def circle(cx, cy, r):
    """A circle walked counter-clockwise on screen: a hole inside the canvas's rectangle."""
    return f"M {f(cx - r)},{f(cy)} a {f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a {f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 z"


def rrect(x0, y0, x1, y1, r):
    """A rounded rectangle walked counter-clockwise on screen, as circle() is."""
    return (
        f"M {f(x1 - r)},{f(y0)} L {f(x0 + r)},{f(y0)} A {f(r)},{f(r)} 0 0,0 {f(x0)},{f(y0 + r)} "
        f"L {f(x0)},{f(y1 - r)} A {f(r)},{f(r)} 0 0,0 {f(x0 + r)},{f(y1)} "
        f"L {f(x1 - r)},{f(y1)} A {f(r)},{f(r)} 0 0,0 {f(x1)},{f(y1 - r)} "
        f"L {f(x1)},{f(y0 + r)} A {f(r)},{f(r)} 0 0,0 {f(x1 - r)},{f(y0)} Z"
    )


def rect(x0, y0, x1, y1):
    return f"M {f(x1)},{f(y0)} L {f(x0)},{f(y0)} L {f(x0)},{f(y1)} L {f(x1)},{f(y1)} Z"


def grow(shape, by):
    x0, y0, x1, y1, r = shape
    return (x0 - by, y0 - by, x1 + by, y1 + by, r + by)


def wallet(colour, gap=GAP, button=BUTTON[2], stripe=CARD_STRIPE[1]):
    """The card behind, less the band round the wallet; the wallet, less the band round the
    clasp; the clasp with its button cut through. In colour the card's stripe is a deeper amber
    laid on it, in one ink it is cut out of it."""
    card_d, body_d, clasp_d = rrect(*CARD), rrect(*BODY), rrect(*CLASP)
    clasp = f"{clasp_d} {circle(BUTTON[0], BUTTON[1], button)}"
    under_card = [rrect(*grow(BODY, gap))]
    under_body = [rrect(*grow(CLASP, gap))]
    y = CARD_STRIPE[0]
    if colour:
        band = rect(CARD[0], y - stripe / 2, CARD[2], y + stripe / 2)
        card = shaded(card_d, CARD[:4], SUN[0], SUN[1]) + (
            f'<path android:pathData="{band}" android:fillColor="#{STRIPE_ALPHA}{SUN[2]}"/>\n'
        )
        return (
            clipped(under_card, card)
            + clipped(under_body, shaded(body_d, WALLET_LIGHT_BOX, WHITE[0], WHITE[2]))
            + shaded(clasp, WALLET_LIGHT_BOX, WHITE[0], WHITE[2], even_odd=True)
        )
    band = rect(CARD[0] - 1, y - stripe / 2, CARD[2] + 1, y + stripe / 2)
    return (
        clipped(under_card + [band], ink(card_d))
        + clipped(under_body, ink(body_d))
        + ink(clasp, even_odd=True)
    )


def ground(d="M 0,0 H 108 V 108 H 0 Z"):
    return shaded(d, GROUND_LIGHT_BOX, LAGUNA[0], LAGUNA[2])


def background():
    what = "The badge's ground: the app's laguna teal, lit from the upper left."
    return HEADER.format(what=what) + vector(ground())


def foreground():
    what = (
        "The mark: a white wallet with its clasp, and an amber card standing out of it, its\n"
        f"     stripe struck in a deeper amber; inside {KEYLINE:g} units of the centre, the keyline."
    )
    return HEADER.format(what=what) + vector(fitted(wallet(True), FIT_SCALE, FIT_SHIFT))


def monochrome():
    what = (
        "The themed icon (Android 13+): the system reads only the alpha, so the card's stripe is\n"
        "     cut out of it, and the bands of the ground keep the card, the wallet and the clasp apart."
    )
    return HEADER.format(what=what) + vector(fitted(wallet(False), FIT_SCALE, FIT_SHIFT), aapt=False)


def status_bar():
    # Centred by its box: the card's top to the wallet's bottom, the wallet's left to the clasp's
    # right, with the bands widened round the shapes they cut.
    x0, x1 = BODY[0], CLASP[2]
    y0, y1 = CARD[1], BODY[3]
    mid_x = CENTRE + STAT_SCALE * ((x0 + x1) / 2 - CENTRE)
    mid_y = CENTRE + STAT_SCALE * ((y0 + y1) / 2 - CENTRE)
    shift = (12 - mid_x, 12 - mid_y)
    body = wallet(False, gap=STAT_GAP, button=STAT_BUTTON, stripe=STAT_STRIPE)
    what = (
        "Status-bar icon of every notification: the mark's wallet and card in one ink, inside 2\n"
        "     units of air on the 24-unit grid. White on transparent, as status-bar icons must be:\n"
        "     the system tints it."
    )
    return HEADER.format(what=what) + vector(fitted(body, STAT_SCALE, shift), size=24, viewport=24, aapt=False)


def app_mark():
    disc = circle(CENTRE, CENTRE, 36.0)
    body = (
        f'<group>\n<clip-path android:pathData="{disc}"/>\n{ground()}</group>\n'
        + fitted(wallet(True), FIT_SCALE, FIT_SHIFT)
    )
    what = (
        "The launcher icon in a round mask, as one vector: the ground on a disc of the 72 that\n"
        "     show, the mark on it. The widgets draw it as a picture: their \"open Saldo\" button and\n"
        "     their setup card."
    )
    return HEADER.format(what=what) + vector(body)


def shortcut(what, paths, middle):
    disc = "M 24,24 m -24,0 a 24,24 0 1,1 48,0 a 24,24 0 1,1 -48,0"
    s = SHORTCUT_GLYPH_SCALE
    glyph = "".join(ink(d) for d in paths)
    body = shaded(disc, (0.0, 0.0, 48.0, 48.0), LAGUNA[0], LAGUNA[2]) + (
        f'<group android:pivotX="{f(middle[0])}" android:pivotY="{f(middle[1])}" android:scaleX="{s:g}" '
        f'android:scaleY="{s:g}" android:translateX="{f(24 - middle[0])}" android:translateY="{f(24 - middle[1])}">\n'
        f"{glyph}</group>\n"
    )
    note = (
        f"A launcher shortcut's mark: {what}, in white on a disc of the launcher icon's\n"
        "     ground, lit from the upper left as the ground is. Launchers draw shortcut icons as they are."
    )
    return HEADER.format(what=note) + vector(body, size=48, viewport=48)


def main():
    outputs = [
        (RES / "ic_launcher_background.xml", background()),
        (RES / "ic_launcher_foreground.xml", foreground()),
        (RES / "ic_launcher_monochrome.xml", monochrome()),
        (RES / "ic_stat_saldo.xml", status_bar()),
        (RES / "ic_app_mark.xml", app_mark()),
    ]
    outputs += [(RES / name, shortcut(*spec)) for name, spec in SHORTCUTS.items()]
    for path, text in outputs:
        path.write_text(text, encoding="utf-8", newline="\n")
        print("wrote", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
