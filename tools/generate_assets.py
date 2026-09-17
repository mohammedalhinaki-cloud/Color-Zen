#!/usr/bin/env python3
"""Generate the Google Play store artwork for Color Zen.

Outputs (all in store/):

  icon_512.png                       512x512  hi-res app icon
  feature_graphic_1024x500.png       1024x500 feature banner
  screenshot_gameplay_1080x1920.png  portrait phone screenshot (gameplay)
  screenshot_levels_1080x1920.png    portrait phone screenshot (level select)
  screenshot_shop_1080x1920.png      portrait phone screenshot (shop)

Everything is drawn from the same geometry as the in-app renderer and the
launcher icon, so the brand mark is identical in the store, on the device home
screen and inside the game. Colours are copied from the PASTEL palette in
game/Palettes.kt.

Bottles are rendered with alpha masks (a union of two rounded rectangles gives
the narrow top / wide bottom radii), and the glass outline is produced by
eroding the mask, so the stroke width is perfectly uniform.

Usage: python3 tools/generate_assets.py
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(REPO, "store")
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# --- palette ---------------------------------------------------------------
ROSE, PEACH, BUTTER, SAGE, MINT = (242, 139, 168), (246, 169, 107), (242, 208, 107), (168, 213, 162), (127, 200, 169)
SKY, PERI, LAV, ORCHID, SAND, SLATE = (142, 201, 232), (143, 166, 232), (180, 155, 224), (224, 160, 200), (201, 168, 138), (154, 167, 180)
BG = (247, 243, 238)
INK = (46, 49, 56)
INK_SOFT = (112, 116, 126)
CARD = (255, 255, 255)
ACCENT = (143, 166, 232)
STAR = (242, 208, 107)

_fonts: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    key = (FONT_BOLD if bold else FONT_REG, size)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(key[0], size)
    return _fonts[key]


def rounded(d: ImageDraw.ImageDraw, box, radius, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def gradient(size, top, bottom) -> Image.Image:
    w, h = size
    img = Image.new("RGB", size)
    d = ImageDraw.Draw(img)
    for y in range(h):
        k = y / max(1, h - 1)
        d.line([(0, y), (w, y)],
               fill=tuple(int(top[i] + (bottom[i] - top[i]) * k) for i in range(3)))
    return img


def blobs(img: Image.Image, specs) -> None:
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for (cx, cy, r, color, alpha) in specs:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (alpha,))
    img.paste(layer.filter(ImageFilter.GaussianBlur(6)), (0, 0), layer)


def paste_shadow(img: Image.Image, box, radius, blur=18, offset=(0, 10), alpha=42) -> None:
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    x0, y0, x1, y1 = box
    ImageDraw.Draw(layer).rounded_rectangle(
        [x0 + offset[0], y0 + offset[1], x1 + offset[0], y1 + offset[1]],
        radius=radius, fill=(40, 40, 60, alpha))
    img.paste(layer.filter(ImageFilter.GaussianBlur(blur)), (0, 0), layer)


# ---------------------------------------------------------------------------
# Bottle rendering (mask based)
# ---------------------------------------------------------------------------
def bottle_mask(size, box, inset=0.0):
    """Bottle silhouette: narrow rounded top, wide rounded bottom."""
    x0, y0, x1, y1 = box
    x0 += inset
    y0 += inset
    x1 -= inset
    y1 -= inset
    w, h = x1 - x0, y1 - y0
    r_top, r_bot = w * 0.24, w * 0.46
    mid = y0 + h * 0.5
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([x0, y0, x1, y1], radius=r_bot, fill=255)
    d.rectangle([x0, y0, x1, mid], fill=255)
    d.rounded_rectangle([x0, y0, x1, mid], radius=r_top, fill=255)
    return m


def draw_bottle(img: Image.Image, box, segments, outline_w=None, glass_tint=True,
                highlight=True):
    """One bottle; `segments` lists RGB colours bottom -> top (max 4)."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    ow = outline_w or max(2, int(round(w * 0.07)))

    body = bottle_mask(img.size, box)

    if glass_tint:
        tint = Image.new("RGBA", img.size, (255, 255, 255, 80))
        img.paste(tint, (0, 0), body)

    # liquid: inset shape, filled row by row from the bottom
    inset = max(1.5, w * 0.10)
    inner = bottle_mask(img.size, box, inset=inset)
    liquid = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(liquid)
    seg_h = (y1 - inset - (y0 + inset)) / 4.0
    for i, color in enumerate(segments[:4]):
        sy1 = y1 - inset - i * seg_h
        sy0 = sy1 - seg_h
        ld.rectangle([x0, sy0 - 1, x1, sy1 + 1], fill=color + (255,))
    liquid.putalpha(Image.composite(liquid.getchannel("A"), Image.new("L", img.size, 0), inner))
    img.paste(liquid, (0, 0), liquid)

    # glass outline = mask minus eroded mask (uniform stroke, any shape)
    from PIL import ImageChops
    k = max(3, (ow // 2) * 2 + 1)
    eroded = body.filter(ImageFilter.MinFilter(k))
    ring = ImageChops.subtract(body, eroded)
    img.paste(Image.new("RGBA", img.size, INK + (255,)), (0, 0), ring)

    if highlight:
        hl = Image.new("RGBA", img.size, (0, 0, 0, 0))
        hd = ImageDraw.Draw(hl)
        hx = x0 + w * 0.27
        hd.line([(hx, y0 + h * 0.16), (hx, y0 + h * 0.40)],
                fill=(255, 255, 255, 200), width=max(2, int(w * 0.075)))
        hl.putalpha(Image.composite(hl.getchannel("A"), Image.new("L", img.size, 0), inner))
        img.paste(hl, (0, 0), hl)


def draw_row(img: Image.Image, cx, cy, bottles, bw, bh, gap, selected=None):
    total = len(bottles) * bw + (len(bottles) - 1) * gap
    x = cx - total / 2
    for i, seg in enumerate(bottles):
        box = (x, cy - bh / 2, x + bw, cy + bh / 2)
        draw_bottle(img, box, list(seg))
        if selected == i:
            d = ImageDraw.Draw(img)
            pad = bw * 0.22
            rounded(d, [box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad],
                    bw * 0.5, outline=ACCENT, width=max(3, int(bw * 0.05)))
        x += bw + gap


# ---------------------------------------------------------------------------
# Icon + feature graphic
# ---------------------------------------------------------------------------
MARK_SPEC = [(ROSE, ROSE, ROSE), (BUTTER, MINT), (PERI, PERI, PERI)]


def draw_mark(img: Image.Image, box, outline_frac=0.012):
    x0, y0, x1, y1 = box
    w = x1 - x0
    bw = w / 3.6
    gap = (w - 3 * bw) / 2
    h = y1 - y0
    ow = max(2, int(w * outline_frac))
    for i, seg in enumerate(MARK_SPEC):
        bx = x0 + i * (bw + gap)
        draw_bottle(img, (bx, y0, bx + bw, y0 + h), list(seg), outline_w=ow)


def build_icon(path: str, size: int = 512) -> None:
    ss = 4
    s = size * ss
    img = gradient((s, s), (255, 246, 238), (250, 236, 240))
    blobs(img, [(s * 0.82, s * 0.2, s * 0.34, (249, 217, 198), 150),
                (s * 0.16, s * 0.86, s * 0.36, (216, 231, 247), 150),
                (s * 0.5, s * 0.5, s * 0.6, (255, 255, 255), 90)])
    bw = s * 0.185
    gap = s * 0.055
    total = 3 * bw + 2 * gap
    draw_mark(img, ((s - total) / 2, s * 0.24, (s - total) / 2 + total, s * 0.76),
              outline_frac=0.014)
    img.resize((size, size), Image.LANCZOS).save(path)
    print(f"-> {os.path.relpath(path, REPO)}")


def build_feature(path: str) -> None:
    ss = 2
    w, h = 1024 * ss, 500 * ss
    img = gradient((w, h), (255, 245, 236), (233, 239, 250))
    blobs(img, [(w * 0.86, h * 0.18, w * 0.22, (249, 217, 198), 140),
                (w * 0.72, h * 0.92, w * 0.26, (216, 231, 247), 130),
                (w * 0.06, h * 0.1, w * 0.18, (255, 255, 255), 120)])
    d = ImageDraw.Draw(img)

    mx = w * 0.07
    bw = w * 0.088
    gap = w * 0.026
    total = 3 * bw + 2 * gap
    mark_x0 = w - total - w * 0.06
    title_size = int(h * 0.17)
    while title_size > 40 and mx + d.textlength("Color Zen", font=font(title_size, True)) > mark_x0 - w * 0.03:
        title_size -= 4
    d.text((mx, h * 0.20), "Color Zen", font=font(title_size, True), fill=INK)
    d.text((mx + 4, h * 0.47), "Sort the colours.", font=font(int(h * 0.068)), fill=INK_SOFT)
    d.text((mx + 4, h * 0.575), "Find your calm.", font=font(int(h * 0.068)), fill=INK_SOFT)

    py = h * 0.735
    px = mx
    for label in ("No ads", "No music", "120 levels"):
        fnt = font(int(h * 0.05), True)
        tw = d.textlength(label, font=fnt)
        pad = h * 0.03
        rounded(d, [px, py, px + tw + pad * 2, py + h * 0.1], h * 0.05, fill=CARD)
        d.text((px + pad, py + h * 0.022), label, font=fnt, fill=ACCENT)
        px += tw + pad * 2 + h * 0.03

    draw_mark(img, (mark_x0, h * 0.24, mark_x0 + total, h * 0.80),
              outline_frac=0.005)

    img.resize((1024, 500), Image.LANCZOS).save(path)
    print(f"-> {os.path.relpath(path, REPO)}")


# ---------------------------------------------------------------------------
# Screenshot helpers
# ---------------------------------------------------------------------------
W, H = 1080, 1920


def screen_base(title: str, subtitle: str | None = None):
    img = Image.new("RGB", (W, H), BG)
    blobs(img, [(W * 0.9, H * 0.04, W * 0.5, (250, 231, 220), 90),
                (W * 0.1, H * 0.98, W * 0.5, (226, 236, 248), 90)])
    d = ImageDraw.Draw(img)
    d.text((72, 118), title, font=font(84, True), fill=INK)
    if subtitle:
        d.text((74, 226), subtitle, font=font(42), fill=INK_SOFT)
    return img, d


def pill(d, box, label, filled=False, fsize=40):
    x0, y0, x1, y1 = box
    fnt = font(fsize, True)
    tw = d.textlength(label, font=fnt)
    rounded(d, box, (y1 - y0) / 2,
            fill=ACCENT if filled else CARD,
            outline=None if filled else (226, 222, 216), width=3)
    d.text(((x0 + x1) / 2 - tw / 2, y0 + (y1 - y0 - fsize * 1.16) / 2), label,
           font=fnt, fill=(255, 255, 255) if filled else INK)


def stat_tile(d, box, value, label):
    x0, y0, x1, y1 = box
    rounded(d, box, 36, fill=CARD, outline=(228, 224, 218), width=3)
    vf, lf = font(64, True), font(34)
    vw, lw = d.textlength(value, font=vf), d.textlength(label, font=lf)
    cx = (x0 + x1) / 2
    d.text((cx - vw / 2, y0 + 26), value, font=vf, fill=INK)
    d.text((cx - lw / 2, y0 + 108), label, font=lf, fill=INK_SOFT)


def star(d, cx, cy, r, filled=True):
    d.regular_polygon((cx, cy, r), 5, rotation=-90,
                      fill=STAR if filled else None,
                      outline=None if filled else (214, 210, 204),
                      width=3)


# ---------------------------------------------------------------------------
# Screenshots
# ---------------------------------------------------------------------------
def shot_gameplay(path: str) -> None:
    img, d = screen_base("Level 12", "Medium · 7 bottles · 5 colours")

    fnt = font(44, True)
    label = "3 hints"
    tw = d.textlength(label, font=fnt)
    rounded(d, [W - 72 - tw - 104, 128, W - 72, 212], 42, fill=CARD,
            outline=(226, 222, 216), width=3)
    d.ellipse([W - 72 - tw - 88, 146, W - 72 - tw - 40, 194], outline=ACCENT, width=6)
    d.ellipse([W - 72 - tw - 74, 160, W - 72 - tw - 54, 180], outline=ACCENT, width=5)
    d.text((W - 72 - tw - 24, 146), label, font=fnt, fill=INK)

    y = 320
    tw_ = (W - 144 - 48) / 3
    for i, (v, l) in enumerate([("14", "Moves"), ("16", "Best"), ("2/5", "Sorted")]):
        stat_tile(d, [72 + i * (tw_ + 24), y, 72 + i * (tw_ + 24) + tw_, y + 168], v, l)

    paste_shadow(img, [72, 560, W - 72, 1400], 60)
    rounded(d, [72, 560, W - 72, 1400], 60, fill=CARD)
    draw_row(img, W / 2, 830, [(ROSE, ROSE, BUTTER, SKY), (BUTTER, MINT, MINT, ROSE),
                              (SKY, SKY, PERI, MINT), (PERI, PERI, ROSE, BUTTER)],
             bw=132, bh=310, gap=44, selected=1)
    draw_row(img, W / 2, 1200, [(MINT, BUTTER, SKY, PERI), (SAGE, SAGE, LAV, LAV),
                               (LAV, PEACH, PEACH, SAGE)], bw=132, bh=310, gap=44)

    cy = 1500
    cw = (W - 144 - 72) / 4
    for i, label in enumerate(["Undo", "Shuffle", "Hint", "Restart"]):
        x0 = 72 + i * (cw + 24)
        pill(d, [x0, cy, x0 + cw, cy + 108], label)

    d.text((72, 1712), "No ads. No music. Just calm.", font=font(40), fill=INK_SOFT)
    img.save(path)
    print(f"-> {os.path.relpath(path, REPO)}")


def shot_levels(path: str) -> None:
    img, d = screen_base("Levels", "28 of 120 complete")
    y = 330
    cols = 6
    tile = (W - 144 - 5 * 24) / cols
    done = 28
    for n in range(1, 31):
        r, c = divmod(n - 1, cols)
        x0 = 72 + c * (tile + 24)
        y0 = y + r * (tile + 24)
        if n <= done:
            rounded(d, [x0, y0, x0 + tile, y0 + tile], 30, fill=CARD,
                    outline=(228, 224, 218), width=3)
            nf = font(52, True)
            tw = d.textlength(str(n), font=nf)
            d.text((x0 + tile / 2 - tw / 2, y0 + 16), str(n), font=nf, fill=INK)
            stars = 3 if n % 3 else 2
            for s in range(3):
                star(d, x0 + tile / 2 - 40 + s * 40, y0 + tile - 38, 19, filled=s < stars)
        else:
            rounded(d, [x0, y0, x0 + tile, y0 + tile], 30, fill=(238, 234, 229))
            lx, ly = x0 + tile / 2, y0 + tile / 2
            d.rounded_rectangle([lx - 22, ly - 6, lx + 22, ly + 26], radius=8,
                                outline=(176, 172, 166), width=6)
            d.arc([lx - 14, ly - 30, lx + 14, ly + 2], 180, 360,
                  fill=(176, 172, 166), width=6)

    by = y + 5 * (tile + 24) + 34
    paste_shadow(img, [72, by, W - 72, by + 250], 48, alpha=56)
    rounded(d, [72, by, W - 72, by + 250], 48, fill=ACCENT)
    d.text((120, by + 42), "Level Pack 1", font=font(56, True), fill=(255, 255, 255))
    d.text((122, by + 128), "Levels 31-60 · $0.99", font=font(40), fill=(238, 242, 255))
    pw = d.textlength("Buy", font=font(44, True)) + 80
    rounded(d, [W - 120 - pw, by + 84, W - 120, by + 168], 42, fill=(255, 255, 255))
    d.text((W - 120 - pw + 40, by + 102), "Buy", font=font(44, True), fill=ACCENT)

    d.text((72, 1712), "One-time purchases. No ads, no subscriptions.",
           font=font(40), fill=INK_SOFT)
    img.save(path)
    print(f"-> {os.path.relpath(path, REPO)}")


def shot_shop(path: str) -> None:
    img, d = screen_base("Shop", "One-time purchases. No ads, no subscriptions.")
    y = 320

    paste_shadow(img, [72, y, W - 72, y + 300], 52, alpha=56)
    g = gradient((W - 144, 300), (168, 140, 226), (143, 166, 232))
    mask = Image.new("L", (W - 144, 300), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 144, 300], radius=52, fill=255)
    img.paste(g, (72, y), mask)
    d = ImageDraw.Draw(img)
    d.text((120, y + 44), "Full Game", font=font(64, True), fill=(255, 255, 255))
    d.text((122, y + 142), "All 120 levels, forever", font=font(40),
           fill=(240, 243, 255))
    pw = d.textlength("$2.99", font=font(48, True)) + 88
    rounded(d, [W - 120 - pw, y + 100, W - 120, y + 196], 48, fill=(255, 255, 255))
    d.text((W - 120 - pw + 44, y + 122), "$2.99", font=font(48, True), fill=(120, 110, 210))

    y += 352
    rows = [
        ("Level Pack 1", "Levels 31-60 · 30 levels", "$0.99"),
        ("Level Pack 2", "Levels 61-90 · 30 levels", "$0.99"),
        ("Level Pack 3", "Levels 91-120 · 30 levels", "$0.99"),
        ("10 hints", "Reveal the best next pour", "$0.99"),
        ("Colour themes", "4 colour sets · 3 bottle designs", "$1.99"),
    ]
    for title, sub, price in rows:
        paste_shadow(img, [72, y, W - 72, y + 172], 40, blur=14, offset=(0, 8), alpha=32)
        rounded(d, [72, y, W - 72, y + 172], 40, fill=CARD)
        d.text((120, y + 30), title, font=font(50, True), fill=INK)
        d.text((122, y + 100), sub, font=font(36), fill=INK_SOFT)
        pf = font(44, True)
        pw = d.textlength(price, font=pf) + 72
        rounded(d, [W - 120 - pw, y + 48, W - 120, y + 128], 40, fill=(238, 242, 252))
        d.text((W - 120 - pw + 36, y + 66), price, font=pf, fill=ACCENT)
        y += 196

    d.text((72, 1712), "Purchases are handled by Google Play.", font=font(40), fill=INK_SOFT)
    img.save(path)
    print(f"-> {os.path.relpath(path, REPO)}")


def main() -> None:
    os.makedirs(STORE, exist_ok=True)
    build_icon(os.path.join(STORE, "icon_512.png"))
    build_feature(os.path.join(STORE, "feature_graphic_1024x500.png"))
    shot_gameplay(os.path.join(STORE, "screenshot_gameplay_1080x1920.png"))
    shot_levels(os.path.join(STORE, "screenshot_levels_1080x1920.png"))
    shot_shop(os.path.join(STORE, "screenshot_shop_1080x1920.png"))


if __name__ == "__main__":
    main()
