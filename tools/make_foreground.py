from PIL import Image, ImageDraw
import os

ROOT = "/root/workspace/nfc-card-copy"
RES = os.path.join(ROOT, "app/res")

def fg(size):
    S = size * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cw, ch = int(S * 0.40), int(S * 0.26)
    x0, y0 = (S - cw) // 2 - int(S * 0.03), (S - ch) // 2 - int(S * 0.02)
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([x0, y0, x0 + cw, y0 + ch], radius=int(ch * 0.14), fill=(255, 255, 255, 255))
    chip = int(ch * 0.28)
    d.rounded_rectangle([x0 + int(cw * 0.09), y0 + int(ch * 0.18),
                         x0 + int(cw * 0.09) + chip, y0 + int(ch * 0.18) + int(chip * 0.8)],
                        radius=int(chip * 0.24), fill=(0xF3, 0xB5, 0x44, 255))
    lx = x0 + int(cw * 0.09) + chip + int(cw * 0.05)
    for i in range(2):
        yy = y0 + int(ch * 0.22) + i * int(ch * 0.20)
        d.rounded_rectangle([lx, yy, lx + int(cw * 0.26), yy + int(ch * 0.07)],
                            radius=int(ch * 0.035), fill=(0xC9, 0xD3, 0xE1, 255))
    layer = layer.rotate(-10, resample=Image.BICUBIC, center=(S // 2, S // 2))
    img.alpha_composite(layer)

    d = ImageDraw.Draw(img)
    cx, cy = int(S * 0.79), int(S * 0.42)
    for i, r in enumerate([int(S * 0.045), int(S * 0.075), int(S * 0.105)]):
        d.arc([cx - r, cy - r, cx + r, cy + r], start=-55, end=55,
              fill=(255, 255, 255, 210 - i * 55), width=int(S * 0.017))

    br = int(S * 0.075)
    bx, by = int(S * 0.72), int(S * 0.73)
    d.ellipse([bx - br, by - br, bx + br, by + br], fill=(0x1E, 0xA8, 0x55, 255))
    w = max(2, int(S * 0.010))
    d.line([(bx - br * 0.40, by + br * 0.02), (bx - br * 0.06, by + br * 0.34),
            (bx + br * 0.44, by - br * 0.30)], fill=(255, 255, 255, 255), width=w, joint="curve")
    return img.resize((size, size), Image.LANCZOS)

for d, px in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]:
    p = os.path.join(RES, "mipmap-" + d)
    os.makedirs(p, exist_ok=True)
    fg(px).save(os.path.join(p, "ic_launcher_fg.png"))
print("foreground ok")
