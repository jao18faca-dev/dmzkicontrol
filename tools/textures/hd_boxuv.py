"""
Gera texturas HD no estilo do pack do ZoneMC (anime, cores chapadas, sombra em faixa e contorno escuro) a partir
das texturas pequenas do mod e do modelo (geo.json com UV em caixa).

Para cada face de cada cubo: pega a cor media da face na textura original, pinta a face em HD com luz em cima,
sombra embaixo (duas faixas, como cel shading) e um contorno escuro fino. Os detalhes (olhos, boca, lente do
drone, logo) sao desenhados por cima, por funcoes de cada textura.

Uso: python3 hd_boxuv.py   (regrava as texturas em src/main/resources/assets/dmzkicontrol/textures/entity)
"""
import json, os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "dmzkicontrol")


def faces(size, uv):
    w, h, d = size
    u, v = uv
    return {
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }


def mix(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def mean(img, box):
    x, y, w, h = box
    px = [img.getpixel((x + i, y + j)) for i in range(int(w)) for j in range(int(h))
          if 0 <= x + i < img.width and 0 <= y + j < img.height]
    px = [p for p in px if p[3] > 10]
    if not px:
        return None
    return tuple(sum(p[c] for p in px) // len(px) for c in range(3))


def paint_face(dr, box, col, s, side, ink):
    x, y, w, h = box
    X, Y, W, H = int(x * s), int(y * s), max(1, int(w * s)), max(1, int(h * s))
    light = mix(col, (255, 255, 255), 0.18)
    shade = mix(col, (0, 0, 0), 0.22)
    deep = mix(col, (0, 0, 0), 0.38)
    if side == "up":
        dr.rectangle([X, Y, X + W - 1, Y + H - 1], fill=light)
    elif side == "down":
        dr.rectangle([X, Y, X + W - 1, Y + H - 1], fill=deep)
    else:
        dr.rectangle([X, Y, X + W - 1, Y + H - 1], fill=col)
        # sombra em faixa (cel shading): um terco de baixo mais escuro, uma linha mais funda no pe
        dr.rectangle([X, Y + int(H * 0.68), X + W - 1, Y + H - 1], fill=shade)
        dr.rectangle([X, Y + int(H * 0.9), X + W - 1, Y + H - 1], fill=deep)
        # brilho fino no alto
        dr.rectangle([X, Y, X + W - 1, Y + max(1, int(H * 0.08))], fill=light)
    t = max(2, s // 8)
    dr.rectangle([X, Y, X + W - 1, Y + H - 1], outline=ink, width=t)


def hd(name, scale, details=None, ink=(20, 16, 24)):
    geo = json.load(open(os.path.join(ROOT, "geo", "entity", name + ".geo.json")))
    src = Image.open(os.path.join(os.path.dirname(__file__), "src", name + ".png")).convert("RGBA")
    out = Image.new("RGBA", (src.width * scale, src.height * scale), (0, 0, 0, 0))
    dr = ImageDraw.Draw(out)
    for g in geo["minecraft:geometry"]:
        for b in g["bones"]:
            for c in b.get("cubes", []):
                for side, box in faces(c["size"], c["uv"]).items():
                    if box[2] <= 0 or box[3] <= 0:
                        continue
                    col = mean(src, box)
                    if col is None:
                        continue
                    paint_face(dr, box, col, scale, side, ink)
    if details:
        details(out, ImageDraw.Draw(out), scale)
    return out


# ------------------------------------------------------------------ detalhes por textura

def drone_details(img, dr, s):
    # corpo 8x8x8 em uv (0,0): faixa azul da Capsule Corp, paineis e o logo em cima
    blue, dblue, white, ink = (40, 110, 220), (22, 64, 150), (240, 244, 250), (20, 16, 24)
    for side in ("east", "north", "west", "south"):
        x, y, w, h = faces((8, 8, 8), (0, 0))[side]
        X, Y, W, H = x * s, y * s, w * s, h * s
        dr.rectangle([X, Y + H * 0.48, X + W, Y + H * 0.62], fill=blue)
        dr.line([X, Y + H * 0.62, X + W, Y + H * 0.62], fill=dblue, width=s // 6)
        for k in (0.25, 0.75):
            dr.line([X + W * k, Y + H * 0.08, X + W * k, Y + H * 0.45], fill=(170, 176, 190), width=max(2, s // 10))
        # parafusos
        for px, py in ((0.12, 0.15), (0.88, 0.15), (0.12, 0.85), (0.88, 0.85)):
            r = s * 0.22
            dr.ellipse([X + W * px - r, Y + H * py - r, X + W * px + r, Y + H * py + r], fill=(150, 156, 170), outline=ink, width=max(1, s // 12))
    x, y, w, h = faces((8, 8, 8), (0, 0))["up"]
    cx, cy, r = (x + w / 2) * s, (y + h / 2) * s, w * s * 0.3
    dr.ellipse([cx - r, cy - r, cx + r, cy + r], fill=blue, outline=ink, width=s // 6)
    dr.ellipse([cx - r * 0.55, cy - r * 0.55, cx + r * 0.55, cy + r * 0.55], fill=white)
    # lente vermelha (frente do cubo 4x4x1 em uv 32,0)
    x, y, w, h = faces((4, 4, 1), (32, 0))["north"]
    X, Y, W, H = x * s, y * s, w * s, h * s
    dr.rectangle([X, Y, X + W, Y + H], fill=(40, 6, 8))
    dr.ellipse([X + W * 0.1, Y + H * 0.1, X + W * 0.9, Y + H * 0.9], fill=(150, 10, 10), outline=(255, 90, 70), width=s // 5)
    dr.ellipse([X + W * 0.28, Y + H * 0.28, X + W * 0.72, Y + H * 0.72], fill=(255, 40, 30))
    dr.ellipse([X + W * 0.3, Y + H * 0.25, X + W * 0.48, Y + H * 0.43], fill=(255, 230, 220))
    # anel: luzes azuis em volta
    x, y, w, h = faces((10, 1, 10), (0, 16))["north"]
    for side in ("east", "north", "west", "south"):
        x, y, w, h = faces((10, 1, 10), (0, 16))[side]
        for k in range(1, 4):
            px = (x + w * k / 4) * s
            dr.ellipse([px - s * 0.3, (y + 0.2) * s, px + s * 0.3, (y + 0.8) * s], fill=(120, 220, 255))


def bubbles_details(img, dr, s):
    ink = (18, 14, 20)
    # cabeca 7x6x6 em uv (0,21): olhos na frente
    x, y, w, h = faces((7, 6, 6), (0, 21))["north"]
    X, Y, W, H = x * s, y * s, w * s, h * s
    skin = (238, 168, 150)
    dr.rounded_rectangle([X + W * 0.12, Y + H * 0.32, X + W * 0.88, Y + H * 0.95], radius=s * 1.2, fill=skin, outline=ink, width=s // 6)
    for ex in (0.33, 0.67):
        cx, cy = X + W * ex, Y + H * 0.5
        dr.ellipse([cx - s * 0.9, cy - s * 1.0, cx + s * 0.9, cy + s * 1.0], fill=(255, 255, 255), outline=ink, width=s // 7)
        dr.ellipse([cx - s * 0.45, cy - s * 0.5, cx + s * 0.45, cy + s * 0.6], fill=ink)
        dr.ellipse([cx - s * 0.25, cy - s * 0.4, cx - s * 0.02, cy - s * 0.15], fill=(255, 255, 255))
    # focinho 5x4x1 em uv (26,21): boca e narinas
    x, y, w, h = faces((5, 4, 1), (26, 21))["north"]
    X, Y, W, H = x * s, y * s, w * s, h * s
    dr.rectangle([X, Y, X + W, Y + H], fill=skin, outline=ink, width=s // 6)
    for nx in (0.4, 0.6):
        dr.ellipse([X + W * nx - s * 0.18, Y + H * 0.3, X + W * nx + s * 0.18, Y + H * 0.45], fill=ink)
    dr.arc([X + W * 0.2, Y + H * 0.35, X + W * 0.8, Y + H * 0.85], 20, 160, fill=ink, width=s // 6)
    # pelo: tracos curtos nas faces escuras
    import random
    rnd = random.Random(7)
    px = img.load()
    for _ in range(900):
        x0, y0 = rnd.randrange(img.width), rnd.randrange(img.height)
        p = px[x0, y0]
        if p[3] < 10 or sum(p[:3]) > 220:
            continue
        dr.line([x0, y0, x0 + rnd.randint(-s // 4, s // 4), y0 + s // 2], fill=(min(255, p[0] + 28), min(255, p[1] + 26), min(255, p[2] + 34)), width=max(1, s // 16))


if __name__ == "__main__":
    tex = os.path.join(ROOT, "textures", "entity")
    for name, scale, det in (("training_drone", 16, drone_details), ("bubbles", 16, bubbles_details)):
        img = hd(name, scale, det)
        img.save(os.path.join(tex, name + ".png"), optimize=True)
        print(name, img.size)
