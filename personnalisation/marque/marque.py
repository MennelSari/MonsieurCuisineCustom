"""Marque Camybrith Cooking : cocotte orange et trois volutes de vapeur, mot-symbole Poppins.
Dessin en suréchantillonnage (x3) pour des bords nets."""
import math, os
from PIL import Image, ImageDraw, ImageFont

BG = (11, 12, 14)
ORANGE = (255, 138, 61)
WHITE = (243, 244, 246)
GREY = (150, 155, 165)
_FONTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "fonts")
FONT_BOLD = os.path.join(_FONTS, "Poppins-Bold.ttf")
FONT_LIGHT = os.path.join(_FONTS, "Poppins-Light.ttf")
SS = 3

def ease(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)

def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))

def steam_points(cx, base_y, top_y, amp, phase, grow):
    """Volute : sinusoïde verticale de base_y vers top_y ; grow 0..1 = part tracée."""
    pts = []
    n = 160
    for k in range(n + 1):
        u = k / n
        if u > grow:
            break
        y = base_y + (top_y - base_y) * u
        x = cx + amp * math.sin(u * 2.2 * math.pi + phase) * (0.35 + 0.65 * u)
        pts.append((x, y))
    return pts

def draw_mark(d, cx, cy, size, pot=1.0, steam=(1.0, 1.0, 1.0), phase=0.0, steam_alpha=1.0):
    """Cocotte centrée en (cx, cy), boîte de côté size (unités 0..200)."""
    s = size / 200.0
    def P(x, y):
        return (cx + (x - 100) * s, cy + (y - 100) * s)
    if pot > 0:
        col = mix(BG, ORANGE, pot)
        # anses
        d.rounded_rectangle([P(20, 104), P(42, 120)], radius=7 * s, fill=col)
        d.rounded_rectangle([P(158, 104), P(180, 120)], radius=7 * s, fill=col)
        # corps
        d.rounded_rectangle([P(38, 96), P(162, 168)], radius=26 * s, fill=col)
        d.rectangle([P(38, 96), P(162, 120)], fill=col)
        # couvercle et bouton
        d.rounded_rectangle([P(28, 82), P(172, 98)], radius=8 * s, fill=col)
        d.rounded_rectangle([P(86, 68), P(114, 84)], radius=7 * s, fill=col)
        # reflet sombre sous le couvercle
        d.rectangle([P(38, 98), P(162, 104)], fill=mix(BG, ORANGE, pot * 0.55))
    w = max(1, int(9 * s))
    for i, (x0, g) in enumerate(zip((66, 100, 134), steam)):
        if g <= 0:
            continue
        pts = steam_points(*P(x0, 58), P(x0, 8)[1], 7 * s, phase + i * 1.3, g)
        if len(pts) > 1:
            c = mix(BG, WHITE, steam_alpha * (0.85 if i == 1 else 0.7))
            r = w / 2
            for (x, y) in pts:
                d.ellipse([x - r, y - r, x + r, y + r], fill=c)

def draw_words(img, cx, y, size, alpha=1.0, slide=0.0):
    d = ImageDraw.Draw(img)
    f1 = ImageFont.truetype(FONT_BOLD, int(size))
    f2 = ImageFont.truetype(FONT_LIGHT, int(size * 0.42))
    t1 = "Camybrith"
    w1 = d.textlength(t1, font=f1)
    d.text((cx - w1 / 2, y + slide), t1, font=f1, fill=mix(BG, WHITE, alpha))
    t2 = "COOKING"
    sp = size * 0.2
    widths = [d.textlength(ch, font=f2) for ch in t2]
    total = sum(widths) + sp * (len(t2) - 1)
    x = cx - total / 2
    yy = y + size * 1.18 - slide * 0.5
    for ch, cw in zip(t2, widths):
        d.text((x, yy), ch, font=f2, fill=mix(BG, ORANGE, alpha))
        x += cw + sp

def frame(w, h, pot=1.0, steam=(1, 1, 1), phase=0.0, words=1.0, slide=0.0, steam_alpha=1.0, bg=BG):
    big = Image.new("RGB", (w * SS, h * SS), bg)
    d = ImageDraw.Draw(big)
    mark = 0.34 * h * SS
    draw_mark(d, w * SS / 2, h * SS * 0.36, mark, pot, steam, phase, steam_alpha)
    if words > 0:
        draw_words(big, w * SS / 2, h * SS * 0.56, h * SS * 0.105, words, slide * SS)
    return big.resize((w, h), Image.LANCZOS)
