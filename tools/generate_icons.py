#!/usr/bin/env python3
"""Generate every vector drawable Color Zen needs.

The app deliberately does NOT depend on `material-icons-extended` (a deprecated
artifact whose availability in future Compose BOMs is not guaranteed). Instead
all icons are generated here from a small geometry description, so:

  * the XML is machine-written and therefore geometrically exact
    (rounded rects, arcs and arrowheads are computed, not hand-typed),
  * the same description renders a PNG contact sheet that can be eyeballed
    before the drawables are committed,
  * the launcher icon (vector + legacy PNGs) and the store artwork all use the
    identical mark, so branding is consistent everywhere.

Conventions used for the UI icons
---------------------------------
* 24x24 viewport, stroke-based, 2dp strokes, round caps and joins.
* Paths are pure black (#FF000000). Colour is applied at draw time by Compose
  `Icon(tint = ...)`, which is the standard, theme-safe approach.
* Directional icons (back, chevron, next, undo) set android:autoMirrored="true"
  so they flip automatically in the Arabic RTL layout.

Usage:  python3 tools/generate_icons.py
Output: app/src/main/res/drawable/ic_*.xml
        app/src/main/res/mipmap-*/ic_launcher*.png
        tools/preview/icons_contact_sheet.png
        store/icon_512.png (also written by generate_assets.py)
"""

from __future__ import annotations

import math
import os
import xml.sax.saxutils as saxutils

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO_ROOT, "app", "src", "main", "res")
PREVIEW_DIR = os.path.join(REPO_ROOT, "tools", "preview")

BLACK = "#FF000000"


# ---------------------------------------------------------------------------
# Geometry helpers
# ---------------------------------------------------------------------------
def f(n: float) -> str:
    """Compact number formatting for path data (no trailing zeros)."""
    s = f"{round(float(n) + 0.0, 2):.2f}".rstrip("0").rstrip(".")
    return s if s not in ("", "-0") else "0"


def pt(x: float, y: float) -> str:
    return f"{f(x)},{f(y)}"


def _corner(x: float, y: float, r: float, kx: int, ky: int) -> str:
    """One rounded corner: (x, y) is the corner point, kx/ky point inwards."""
    if r <= 0:
        return f"L{pt(x, y)}"
    return (
        f"L{pt(x + kx * r, y)}"
        f"A{f(r)},{f(r)} 0 0 1 {pt(x, y + ky * r)}"
    )


def rrect_path(x: float, y: float, w: float, h: float,
               rtl: float, rtr: float, rbr: float, rbl: float) -> str:
    """Rounded rectangle with independent corner radii, clockwise from top-left."""
    return (
        f"M{pt(x + rtl, y)}"
        f"H{f(x + w - rtr)}A{f(rtr)},{f(rtr)} 0 0 1 {pt(x + w, y + rtr)}"
        f"V{f(y + h - rbr)}A{f(rbr)},{f(rbr)} 0 0 1 {pt(x + w - rbr, y + h)}"
        f"H{f(x + rbl)}A{f(rbl)},{f(rbl)} 0 0 1 {pt(x, y + h - rbl)}"
        f"V{f(y + rtl)}A{f(rtl)},{f(rtl)} 0 0 1 {pt(x + rtl, y)}Z"
    )


def circle_path(cx: float, cy: float, r: float) -> str:
    """Full circle as two arcs (a closed sub-path, safe to fill)."""
    return (
        f"M{pt(cx - r, cy)}"
        f"A{f(r)},{f(r)} 0 1 0 {pt(cx + r, cy)}"
        f"A{f(r)},{f(r)} 0 1 0 {pt(cx - r, cy)}Z"
    )


def arc_points(cx: float, cy: float, r: float, deg: float) -> tuple[float, float]:
    rad = math.radians(deg)
    return cx + r * math.cos(rad), cy + r * math.sin(rad)


def arc_path(cx: float, cy: float, r: float, a0: float, a1: float, cw: bool = True) -> str:
    """Open arc. Angles in degrees, 0 = east, increasing clockwise (y grows down)."""
    x0, y0 = arc_points(cx, cy, r, a0)
    x1, y1 = arc_points(cx, cy, r, a1)
    delta = ((a1 - a0) % 360.0) if cw else ((a0 - a1) % 360.0)
    large = 1 if delta > 180.0 else 0
    sweep = 1 if cw else 0
    return f"M{pt(x0, y0)}A{f(r)},{f(r)} 0 {large},{sweep} {pt(x1, y1)}"


def polyline_path(points, close: bool = False) -> str:
    d = "M" + pt(*points[0])
    for x, y in points[1:]:
        d += f"L{pt(x, y)}"
    return d + ("Z" if close else "")


def star_points(cx: float, cy: float, r_out: float, r_in: float, tips: int = 5,
                start_deg: float = -90.0):
    pts = []
    step = 180.0 / tips
    for i in range(tips * 2):
        r = r_out if i % 2 == 0 else r_in
        pts.append(arc_points(cx, cy, r, start_deg + i * step))
    return pts


def gear_points(cx: float, cy: float, r_out: float, r_in: float, teeth: int = 8,
                tooth_width_deg: float = 22.0):
    """A gear outline: each tooth is a trapezoid between two root arcs."""
    pts = []
    step = 360.0 / teeth
    for t in range(teeth):
        base = -90.0 + t * step
        a_root_start = base + tooth_width_deg / 2.0
        a_tip_start = base + tooth_width_deg / 2.0 + (step - tooth_width_deg) * 0.16
        a_tip_end = base + step - tooth_width_deg / 2.0 - (step - tooth_width_deg) * 0.16
        a_root_end = base + step - tooth_width_deg / 2.0
        pts.append(arc_points(cx, cy, r_in, a_root_start))
        pts.append(arc_points(cx, cy, r_out, a_tip_start))
        pts.append(arc_points(cx, cy, r_out, a_tip_end))
        pts.append(arc_points(cx, cy, r_in, a_root_end))
    return pts


def arrowhead(tip, direction_deg: float, size: float = 3.4, spread_deg: float = 148.0):
    """Two strokes forming a '>' rotated to point along `direction_deg`."""
    tx, ty = tip
    out = []
    for off in (-spread_deg / 2.0, spread_deg / 2.0):
        ang = math.radians(direction_deg + 180.0 + off)
        out.append((tx + size * math.cos(ang), ty + size * math.sin(ang)))
    return [(out[0]), (tx, ty), (out[1])]


# ---------------------------------------------------------------------------
# Element -> Android vector <path>
# ---------------------------------------------------------------------------
def element_to_paths(el: dict) -> list[str]:
    t = el["t"]
    fill = el.get("fill", False)
    sw = el.get("sw", 2.0)
    color = el.get("color", BLACK)
    alpha = el.get("alpha")

    if t == "line":
        (x1, y1), (x2, y2) = el["p"]
        d = f"M{pt(x1, y1)}L{pt(x2, y2)}"
    elif t == "poly":
        d = polyline_path(el["p"], el.get("close", False))
    elif t == "circle":
        cx, cy = el["c"]
        d = circle_path(cx, cy, el["r"])
    elif t == "arc":
        cx, cy = el["c"]
        d = arc_path(cx, cy, el["r"], el["a0"], el["a1"], el.get("cw", True))
    elif t == "ellipse":
        cx, cy = el["c"]
        rx, ry = el["rx"], el["ry"]
        d = (f"M{pt(cx - rx, cy)}A{f(rx)},{f(ry)} 0 1 0 {pt(cx + rx, cy)}"
             f"A{f(rx)},{f(ry)} 0 1 0 {pt(cx - rx, cy)}Z")
    elif t == "rrect":
        r = el.get("r", 0.0)
        d = rrect_path(el["x"], el["y"], el["w"], el["h"],
                       el.get("rtl", r), el.get("rtr", r),
                       el.get("rbr", r), el.get("rbl", r))
    elif t == "raw":
        d = el["d"]
    else:
        raise ValueError(f"unknown element type {t}")

    attrs = [f'android:pathData="{saxutils.escape(d, {chr(34): "&quot;"})}"']
    if fill:
        attrs.append(f'android:fillColor="{color}"')
        # Stroking a filled shape with the same colour rounds its corners.
        if el.get("round_fill"):
            attrs.append(f'android:strokeColor="{color}"')
            attrs.append(f'android:strokeWidth="{f(sw)}"')
            attrs.append('android:strokeLineJoin="round"')
    else:
        attrs.append(f'android:strokeColor="{color}"')
        attrs.append(f'android:strokeWidth="{f(sw)}"')
        attrs.append(f'android:strokeLineCap="{el.get("cap", "round")}"')
        attrs.append(f'android:strokeLineJoin="{el.get("join", "round")}"')
    if alpha is not None:
        key = "fillAlpha" if fill else "strokeAlpha"
        attrs.append(f'android:{key}="{f(alpha)}"')

    body = "\n".join(f"        {a}" for a in attrs)
    return [f"    <path\n{body} />"]


def write_vector(name: str, elements, viewport: int = 24, size_dp: int | None = None,
                 auto_mirrored: bool = False, tint: str | None = None,
                 alpha: float | None = None) -> str:
    paths: list[str] = []
    for el in elements:
        paths.extend(element_to_paths(el))

    dp = size_dp or viewport
    head = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{dp}dp"',
        f'    android:height="{dp}dp"',
        f'    android:viewportWidth="{viewport}"',
        f'    android:viewportHeight="{viewport}"',
    ]
    if auto_mirrored:
        head.append('    android:autoMirrored="true"')
    if tint:
        head.append(f'    android:tint="{tint}"')
    if alpha is not None:
        head.append(f'    android:alpha="{f(alpha)}"')

    xml = "\n".join(head) + ">\n" + "\n".join(paths) + "\n</vector>\n"
    out = os.path.join(RES, "drawable", f"{name}.xml")
    with open(out, "w", encoding="utf-8") as fh:
        fh.write(xml)
    return out


# ---------------------------------------------------------------------------
# Icon descriptions (24x24 viewport)
# ---------------------------------------------------------------------------
def build_icons() -> dict[str, dict]:
    icons: dict[str, dict] = {}

    def add(name, elements, mirrored=False):
        icons[name] = {"elements": elements, "mirrored": mirrored}

    # --- navigation -------------------------------------------------------
    add("ic_arrow_back", [{"t": "poly", "p": [(15, 5), (8, 12), (15, 19)], "sw": 2.1}], mirrored=True)
    add("ic_chevron_right", [{"t": "poly", "p": [(9.5, 5), (16.5, 12), (9.5, 19)], "sw": 2.1}], mirrored=True)
    add("ic_next", [
        {"t": "poly", "p": [(6, 5.5), (12, 12), (6, 18.5)], "sw": 2.1},
        {"t": "poly", "p": [(12.5, 5.5), (18.5, 12), (12.5, 18.5)], "sw": 2.1},
    ], mirrored=True)
    add("ic_close", [
        {"t": "line", "p": [(6.5, 6.5), (17.5, 17.5)], "sw": 2.1},
        {"t": "line", "p": [(17.5, 6.5), (6.5, 17.5)], "sw": 2.1},
    ])
    add("ic_check", [{"t": "poly", "p": [(4.8, 12.4), (9.6, 17.2), (19.2, 6.8)], "sw": 2.2}])
    add("ic_lock", [
        {"t": "rrect", "x": 4.8, "y": 10.4, "w": 14.4, "h": 9.8, "r": 2.8, "sw": 2},
        {"t": "arc", "c": (12, 10.4), "r": 3.6, "a0": 180, "a1": 360, "cw": True, "sw": 2},
    ])

    # --- gameplay ---------------------------------------------------------
    star = star_points(12, 12.4, 8.6, 3.9)
    add("ic_star", [{"t": "poly", "p": star, "close": True, "fill": True,
                     "round_fill": True, "sw": 1.6}])
    add("ic_star_outline", [{"t": "poly", "p": star, "close": True, "sw": 1.9,
                             "join": "round"}])
    add("ic_play", [{"t": "poly", "p": [(8.2, 5.4), (19.4, 12), (8.2, 18.6)],
                     "close": True, "fill": True, "round_fill": True, "sw": 2.4}])

    # Classic "return" undo: head on the left, stem curving to the right.
    add("ic_undo", [
        {"t": "poly", "p": [(12.2, 7.8), (7.4, 3.2), (2.6, 7.8)], "sw": 2},
        {"t": "line", "p": [(7.4, 3.2), (7.4, 12)], "sw": 2},
        {"t": "arc", "c": (12.4, 12), "r": 5, "a0": 180, "a1": 90, "cw": False, "sw": 2},
        {"t": "line", "p": [(12.4, 17), (19.8, 17)], "sw": 2},
    ], mirrored=True)

    add("ic_restart", [
        {"t": "arc", "c": (12, 12), "r": 7.2, "a0": -60, "a1": 240, "cw": True, "sw": 2},
        {"t": "poly", "p": arrowhead(arc_points(12, 12, 7.2, 240), 330, 3.6), "sw": 2},
    ])

    add("ic_shuffle", [
        {"t": "poly", "p": [(16.4, 3.6), (20.6, 3.6), (20.6, 7.8)], "sw": 2},
        {"t": "line", "p": [(3.6, 20.4), (20.6, 3.6)], "sw": 2},
        {"t": "poly", "p": [(20.6, 16.2), (20.6, 20.4), (16.4, 20.4)], "sw": 2},
        {"t": "line", "p": [(14.6, 14.6), (20.6, 20.4)], "sw": 2},
        {"t": "line", "p": [(3.6, 3.6), (8.2, 8.2)], "sw": 2},
    ])

    # Light bulb: glass dome (long way over the top), neck and base lines.
    add("ic_hint", [
        {"t": "arc", "c": (12, 11), "r": 5.8, "a0": 160, "a1": 20, "cw": True, "sw": 2},
        {"t": "line", "p": [(9.2, 16.2), (14.8, 16.2)], "sw": 2},
        {"t": "line", "p": [(10.3, 19.4), (13.7, 19.4)], "sw": 2},
    ])

    add("ic_moves", [
        {"t": "line", "p": [(4.4, 8.6), (17.6, 8.6)], "sw": 2},
        {"t": "poly", "p": arrowhead((19.6, 8.6), 0, 3.4), "sw": 2},
        {"t": "line", "p": [(19.6, 15.4), (6.4, 15.4)], "sw": 2},
        {"t": "poly", "p": arrowhead((4.4, 15.4), 180, 3.4), "sw": 2},
    ])

    add("ic_coin", [
        {"t": "circle", "c": (12, 12), "r": 8.4, "sw": 2},
        {"t": "circle", "c": (12, 12), "r": 4.2, "sw": 2},
    ])

    # --- sections / chrome ------------------------------------------------
    add("ic_levels", [
        {"t": "rrect", "x": 3.8, "y": 3.8, "w": 7.2, "h": 7.2, "r": 2.4, "sw": 2},
        {"t": "rrect", "x": 13, "y": 3.8, "w": 7.2, "h": 7.2, "r": 2.4, "sw": 2},
        {"t": "rrect", "x": 3.8, "y": 13, "w": 7.2, "h": 7.2, "r": 2.4, "sw": 2},
        {"t": "rrect", "x": 13, "y": 13, "w": 7.2, "h": 7.2, "r": 2.4, "sw": 2},
    ])

    add("ic_shop", [
        {"t": "rrect", "x": 4.2, "y": 7.4, "w": 15.6, "h": 12.4, "r": 3, "sw": 2},
        {"t": "arc", "c": (12, 7.4), "r": 3.6, "a0": 180, "a1": 360, "cw": True, "sw": 2},
    ])

    gear = gear_points(12, 12, 8.8, 6.1, teeth=8, tooth_width_deg=26.0)
    add("ic_settings", [
        {"t": "poly", "p": gear, "close": True, "sw": 1.9, "join": "round"},
        {"t": "circle", "c": (12, 12), "r": 3.1, "sw": 1.9},
    ])

    add("ic_shield", [
        {"t": "poly", "p": [(12, 3.2), (19.6, 6.2), (19.6, 11.8), (16.6, 17.2),
                            (12, 20.8), (7.4, 17.2), (4.4, 11.8), (4.4, 6.2)],
         "close": True, "sw": 2, "join": "round"},
        {"t": "poly", "p": [(9, 12), (11.2, 14.2), (15.2, 9.8)], "sw": 2},
    ])

    add("ic_globe", [
        {"t": "circle", "c": (12, 12), "r": 8.4, "sw": 2},
        {"t": "line", "p": [(3.6, 12), (20.4, 12)], "sw": 2},
        {"t": "ellipse", "c": (12, 12), "rx": 3.6, "ry": 8.4, "sw": 2},
    ])

    add("ic_palette", [
        {"t": "circle", "c": (12, 12), "r": 8.6, "sw": 2},
        {"t": "circle", "c": (8.2, 9.6), "r": 1.35, "fill": True},
        {"t": "circle", "c": (12.4, 7.8), "r": 1.35, "fill": True},
        {"t": "circle", "c": (16.2, 10.4), "r": 1.35, "fill": True},
        {"t": "circle", "c": (15.4, 15.4), "r": 1.35, "fill": True},
    ])

    add("ic_info", [
        {"t": "circle", "c": (12, 12), "r": 8.4, "sw": 2},
        {"t": "line", "p": [(12, 11.2), (12, 16.4)], "sw": 2},
        {"t": "circle", "c": (12, 7.9), "r": 1.15, "fill": True},
    ])

    add("ic_mail", [
        {"t": "rrect", "x": 3.4, "y": 5.6, "w": 17.2, "h": 12.8, "r": 2.8, "sw": 2},
        {"t": "poly", "p": [(4.4, 7.6), (12, 13.4), (19.6, 7.6)], "sw": 2},
    ])

    add("ic_trophy", [
        {"t": "poly", "p": [(7.4, 4), (16.6, 4), (16.6, 9.4), (12, 13.4), (7.4, 9.4)],
         "close": True, "sw": 2, "join": "round"},
        {"t": "arc", "c": (7.4, 7), "r": 2.6, "a0": -90, "a1": 90, "cw": False, "sw": 2},
        {"t": "arc", "c": (16.6, 7), "r": 2.6, "a0": 90, "a1": 270, "cw": False, "sw": 2},
        {"t": "line", "p": [(12, 13.4), (12, 17)], "sw": 2},
        {"t": "line", "p": [(8.6, 20), (15.4, 20)], "sw": 2},
    ])

    # --- sound ------------------------------------------------------------
    speaker = {"t": "poly", "p": [(3.6, 9.4), (7.4, 9.4), (12, 5.4), (12, 18.6),
                                  (7.4, 14.6), (3.6, 14.6)],
               "close": True, "fill": True, "round_fill": True, "sw": 1.8}
    add("ic_sound_on", [
        speaker,
        {"t": "arc", "c": (11.6, 12), "r": 3.9, "a0": -52, "a1": 52, "sw": 2},
        {"t": "arc", "c": (11.6, 12), "r": 6.9, "a0": -55, "a1": 55, "sw": 2},
    ])
    add("ic_sound_off", [
        speaker,
        {"t": "line", "p": [(16.2, 9.2), (21, 14.8)], "sw": 2},
        {"t": "line", "p": [(21, 9.2), (16.2, 14.8)], "sw": 2},
    ])

    return icons


# ---------------------------------------------------------------------------
# Launcher mark (108x108 adaptive-icon viewport)
# ---------------------------------------------------------------------------
# Three bottles, centred inside the 72dp adaptive-icon safe zone.
TUBES = [
    # x, segments bottom->top as fractions of the tube height, colour
    (25.0, [1.0], "#FFF2A0B4"),          # rose, full
    (45.5, [0.5, 0.5], None),            # split: butter over mint
    (66.0, [1.0], "#FF9FB6F0"),          # periwinkle, full
]
TUBE_W = 17.0
TUBE_TOP = 30.0
TUBE_H = 48.0
TUBE_R_TOP = 4.0
TUBE_R_BOT = 8.0
SPLIT_COLORS = ("#FFF7E3A1", "#FF9EDCC3")


def tube_path(x: float, y: float = TUBE_TOP, w: float = TUBE_W, h: float = TUBE_H,
              r_top: float = TUBE_R_TOP, r_bot: float = TUBE_R_BOT) -> str:
    return rrect_path(x, y, w, h, r_top, r_top, r_bot, r_bot)


def launcher_foreground_elements() -> list[dict]:
    els: list[dict] = []
    for i, (x, segments, color) in enumerate(TUBES):
        # liquid
        if color is not None:
            frac = segments[0]
            lh = (TUBE_H - TUBE_R_BOT * 0.4) * frac
            els.append({"t": "raw", "d": tube_path(x + 2.2, TUBE_TOP + TUBE_H - 2.2 - lh,
                                                   TUBE_W - 4.4, lh, 2.6, TUBE_R_BOT - 2.4),
                        "fill": True, "color": color})
        else:
            half = (TUBE_H - TUBE_R_BOT * 0.4) / 2.0
            els.append({"t": "raw",
                        "d": rrect_path(x + 2.2, TUBE_TOP + TUBE_H - 2.2 - half,
                                        TUBE_W - 4.4, half, 0, 0, TUBE_R_BOT - 2.4, TUBE_R_BOT - 2.4),
                        "fill": True, "color": SPLIT_COLORS[1]})
            els.append({"t": "raw",
                        "d": rrect_path(x + 2.2, TUBE_TOP + TUBE_H - 2.2 - 2 * half,
                                        TUBE_W - 4.4, half, 2.6, 2.6, 0, 0),
                        "fill": True, "color": SPLIT_COLORS[0]})
        # glass outline
        els.append({"t": "raw", "d": tube_path(x), "sw": 2.4, "color": "#FF2E3138"})
        # rim highlight
        els.append({"t": "line", "p": [(x + 3.4, TUBE_TOP + 2.4), (x + TUBE_W - 3.4, TUBE_TOP + 2.4)],
                    "sw": 2.0, "color": "#FF2E3138", "alpha": 0.55})
    return els


def launcher_background_elements() -> list[dict]:
    """Soft, flat backdrop (no gradients: keeps the drawable valid on API 23)."""
    return [
        {"t": "rrect", "x": 0, "y": 0, "w": 108, "h": 108, "r": 0, "fill": True,
         "color": "#FFFFF3EA"},
        {"t": "circle", "c": (86, 22), "r": 26, "fill": True, "color": "#FFF9D9C6",
         "alpha": 0.55},
        {"t": "circle", "c": (20, 90), "r": 30, "fill": True, "color": "#FFD8E7F7",
         "alpha": 0.55},
    ]


def launcher_monochrome_elements() -> list[dict]:
    """Single-colour silhouette for themed icons (Android 13+)."""
    els = []
    for x, segments, _ in TUBES:
        frac = 0.55 if segments[0] == 1.0 else 0.5
        lh = (TUBE_H - TUBE_R_BOT * 0.4) * frac
        els.append({"t": "raw", "d": tube_path(x + 2.2, TUBE_TOP + TUBE_H - 2.2 - lh,
                                               TUBE_W - 4.4, lh, 2.6, TUBE_R_BOT - 2.4),
                    "fill": True, "color": BLACK})
        els.append({"t": "raw", "d": tube_path(x), "sw": 2.4, "color": BLACK})
    return els


# ---------------------------------------------------------------------------
# PIL contact sheet (visual verification of the generated XML)
# ---------------------------------------------------------------------------
def render_contact_sheet(icons: dict[str, dict], path: str, cell: int = 96) -> None:
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        print("Pillow not available - skipping contact sheet")
        return

    names = sorted(icons)
    cols = 6
    rows = math.ceil(len(names) / cols)
    pad = 18
    img = Image.new("RGB", (cols * cell + pad, rows * (cell + pad) + pad), "#FFFFFF")
    draw = ImageDraw.Draw(img)
    scale = cell / 24.0

    def s(v):
        return v * scale

    for idx, name in enumerate(names):
        cx0 = (idx % cols) * cell + pad // 2
        cy0 = (idx // cols) * (cell + pad) + pad // 2
        draw.rectangle([cx0, cy0, cx0 + cell, cy0 + cell], outline="#DDDDDD")
        for el in icons[name]["elements"]:
            w = max(1.0, s(el.get("sw", 2.0)))
            fill = el.get("fill", False)
            if el["t"] == "line":
                (x1, y1), (x2, y2) = el["p"]
                draw.line([cx0 + s(x1), cy0 + s(y1), cx0 + s(x2), cy0 + s(y2)],
                          fill="black", width=int(round(w)))
            elif el["t"] == "poly":
                pts = [(cx0 + s(x), cy0 + s(y)) for x, y in el["p"]]
                if fill:
                    draw.polygon(pts, fill="black")
                    draw.line(pts + [pts[0]], fill="black", width=int(round(w)), joint="curve")
                else:
                    draw.line(pts, fill="black", width=int(round(w)), joint="curve")
            elif el["t"] == "circle":
                (ccx, ccy), r = el["c"], el["r"]
                box = [cx0 + s(ccx - r), cy0 + s(ccy - r), cx0 + s(ccx + r), cy0 + s(ccy + r)]
                if fill:
                    draw.ellipse(box, fill="black")
                else:
                    draw.ellipse(box, outline="black", width=int(round(w)))
            elif el["t"] == "arc":
                (ccx, ccy), r = el["c"], el["r"]
                box = [cx0 + s(ccx - r), cy0 + s(ccy - r), cx0 + s(ccx + r), cy0 + s(ccy + r)]
                a0, a1 = el["a0"], el["a1"]
                if el.get("cw", True):
                    draw.arc(box, a0, a1, fill="black", width=int(round(w)))
                else:
                    draw.arc(box, a1, a0, fill="black", width=int(round(w)))
            elif el["t"] == "ellipse":
                (ccx, ccy) = el["c"]
                rx, ry = el["rx"], el["ry"]
                box = [cx0 + s(ccx - rx), cy0 + s(ccy - ry),
                       cx0 + s(ccx + rx), cy0 + s(ccy + ry)]
                draw.ellipse(box, outline="black", width=int(round(w)))
            elif el["t"] == "rrect":
                box = [cx0 + s(el["x"]), cy0 + s(el["y"]),
                       cx0 + s(el["x"] + el["w"]), cy0 + s(el["y"] + el["h"])]
                rad = s(el.get("r", 0.0))
                if fill:
                    draw.rounded_rectangle(box, radius=rad, fill="black")
                else:
                    draw.rounded_rectangle(box, radius=rad, outline="black",
                                           width=int(round(w)))
            # 'raw' path data is only used by the launcher mark, which is
            # previewed separately in generate_assets.py.
        draw.text((cx0 + 3, cy0 + cell - 13), name.replace("ic_", "")[:13], fill="#777777")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print(f"contact sheet -> {os.path.relpath(path, REPO_ROOT)}")


# ---------------------------------------------------------------------------
# Legacy launcher PNGs (API 23-25 cannot use adaptive icons)
# ---------------------------------------------------------------------------
def pil_color(android_hex: str):
    """Android #AARRGGBB -> PIL (R, G, B, A) tuple."""
    h = android_hex.lstrip("#")
    if len(h) == 6:
        return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)
    a, r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
    return (r, g, b, a)


def render_launcher_pngs() -> None:
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        print("Pillow not available - skipping launcher PNGs")
        return

    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    scale = 108.0  # icon viewport

    def draw_mark(size: int, pad_ratio: float, round_mask: bool):
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        k = size / scale
        if round_mask:
            d.ellipse([0, 0, size - 1, size - 1], fill="#FFF3EA")
        else:
            d.rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.22,
                                fill="#FFF3EA")
        # soft backdrop blobs
        d.ellipse([(86 - 26) * k, (22 - 26) * k, (86 + 26) * k, (22 + 26) * k],
                  fill=(249, 217, 198, 140))
        d.ellipse([(20 - 30) * k, (90 - 30) * k, (20 + 30) * k, (90 + 30) * k],
                  fill=(216, 231, 247, 140))
        if round_mask:
            mask = Image.new("L", (size, size), 0)
            ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
            img.putalpha(mask)

        inner = pad_ratio
        for x, segments, color in TUBES:
            bx = size * (0.5 - inner / 2) + (x - 25.0) / 58.0 * size * inner
            bw = TUBE_W / 58.0 * size * inner
            bh = TUBE_H / 58.0 * size * inner
            by = size * (0.5 - (TUBE_H / 58.0 * inner) / 2)
            rad = bw * (TUBE_R_BOT / TUBE_W)
            if color is not None:
                lh = bh * 0.88
                d.rounded_rectangle(
                    [bx + bw * 0.13, by + bh - lh, bx + bw * 0.87, by + bh - bh * 0.04],
                    radius=bw * 0.16, fill=pil_color(color))
            else:
                lh = bh * 0.44
                d.rounded_rectangle(
                    [bx + bw * 0.13, by + bh - lh, bx + bw * 0.87, by + bh - bh * 0.04],
                    radius=bw * 0.16, fill=pil_color(SPLIT_COLORS[1]))
                d.rectangle([bx + bw * 0.13, by + bh - 2 * lh, bx + bw * 0.87, by + bh - lh],
                            fill=pil_color(SPLIT_COLORS[0]))
            lw = max(1, int(round(bw * 0.11)))
            d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=rad,
                                outline=pil_color("#FF2E3138"), width=lw)
        return img

    for name, px in densities.items():
        for suffix, round_mask in (("", False), ("_round", True)):
            folder = os.path.join(RES, f"mipmap-{name}")
            os.makedirs(folder, exist_ok=True)
            draw_mark(px, 0.74, round_mask).save(
                os.path.join(folder, f"ic_launcher{suffix}.png"))
    print("launcher PNGs -> 5 densities x 2 variants")


def main() -> None:
    icons = build_icons()
    for name, spec in icons.items():
        write_vector(name, spec["elements"], auto_mirrored=spec.get("mirrored", False))
    print(f"UI icons -> {len(icons)} drawables")

    write_vector("ic_launcher_foreground", launcher_foreground_elements(),
                 viewport=108, size_dp=108)
    write_vector("ic_launcher_background", launcher_background_elements(),
                 viewport=108, size_dp=108)
    write_vector("ic_launcher_monochrome", launcher_monochrome_elements(),
                 viewport=108, size_dp=108)
    print("launcher vectors -> 3 drawables")

    render_launcher_pngs()
    render_contact_sheet(icons, os.path.join(PREVIEW_DIR, "icons_contact_sheet.png"))


if __name__ == "__main__":
    main()
