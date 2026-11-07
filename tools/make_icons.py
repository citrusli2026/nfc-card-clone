from PIL import Image, ImageDraw
import os, math

ROOT = "/root/workspace/nfc-card-copy"
RES = os.path.join(ROOT, "app/res")
S = 4096  # supersample

def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m

def gradient(size, top, bottom):
    img = Image.new("RGB", (1, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.point((0, y), fill=c)
    return img.resize((size, size))

def draw_icon(size=1024, round_mask=False):
    bg = gradient(S, (0x47, 0x7C, 0xFF), (0x2C, 0x55, 0xE0)).convert("RGBA")
    mask = Image.new("L", (S, S), 0)
    m = ImageDraw.Draw(mask)
    if round_mask:
        m.ellipse([0, 0, S, S], fill=255)
    else:
        m.rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(bg, (0, 0), mask)

    # card layer (rotated)
    card = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    cw, ch = int(S * 0.52), int(S * 0.34)
    x0, y0 = (S - cw) // 2, (S - ch) // 2
    # shadow
    d.rounded_rectangle([x0 + 18, y0 + 30, x0 + cw + 18, y0 + ch + 30], radius=int(ch * 0.12),
                        fill=(0, 0, 0, 60))
    d.rounded_rectangle([x0, y0, x0 + cw, y0 + ch], radius=int(ch * 0.12), fill=(255, 255, 255, 255))
    # chip
    chip = int(ch * 0.26)
    d.rounded_rectangle([x0 + int(cw * 0.10), y0 + int(ch * 0.16),
                         x0 + int(cw * 0.10) + chip, y0 + int(ch * 0.16) + int(chip * 0.78)],
                        radius=int(chip * 0.22), fill=(0xF3, 0xB5, 0x44, 255))
    # contact lines
    lx = x0 + int(cw * 0.10) + chip + int(cw * 0.05)
    for i in range(3):
        yy = y0 + int(ch * 0.16) + int(chip * 0.20) + i * int(ch * 0.16)
        d.rounded_rectangle([lx, yy, lx + int(cw * 0.30), yy + int(ch * 0.06)],
                            radius=int(ch * 0.03), fill=(0xC8, 0xD2, 0xE0, 255))
    card = card.rotate(-12, resample=Image.BICUBIC, center=(S // 2, S // 2))
    out.alpha_composite(card)

    # NFC arcs
    arc = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(arc)
    cx, cy = int(S * 0.74), int(S * 0.40)
    for i, r in enumerate([int(S * 0.055), int(S * 0.095), int(S * 0.135)]):
        d.arc([cx - r, cy - r, cx + r, cy + r], start=-58, end=58,
              fill=(255, 255, 255, 200 - i * 45), width=int(S * 0.020))
    out.alpha_composite(arc)

    # check badge
    badge = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(badge)
    br = int(S * 0.115)
    bx, by = int(S * 0.735), int(S * 0.725)
    d.ellipse([bx - br, by - br, bx + br, by + br], fill=(0x1E, 0xA8, 0x55, 255))
    w = max(3, int(S * 0.011))
    d.line([(bx - br * 0.42, by + br * 0.02), (bx - br * 0.08, by + br * 0.36),
            (bx + br * 0.46, by - br * 0.30)], fill=(255, 255, 255, 255), width=w, joint="curve")
    out.alpha_composite(badge)

    return out.resize((size, size), Image.LANCZOS)

icon = draw_icon(1024)
icon.save(os.path.join(ROOT, "dist/play/icon-512.png"))
icon.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, "dist/play/icon-512.png"))
for d, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    p = os.path.join(RES, "mipmap-" + d)
    os.makedirs(p, exist_ok=True)
    draw_icon(px).save(os.path.join(p, "ic_launcher.png"))
    draw_icon(px, round_mask=True).save(os.path.join(p, "ic_launcher_round.png"))
# adaptive foreground PNG not needed; vector used
print("icons ok")
