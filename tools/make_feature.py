from PIL import Image, ImageDraw, ImageFont
import os

ROOT = "/root/workspace/nfc-card-copy"
W, H = 2048, 1000

img = Image.new("RGB", (W, H))
d = ImageDraw.Draw(img)
for y in range(H):
    t = y / (H - 1)
    c = (int(0x4A + (0x22 - 0x4A) * t), int(0x7C + (0x3F - 0x7C) * t), int(0xFF + (0xC4 - 0xFF) * t))
    d.line([(0, y), (W, y)], fill=c)
img = img.convert("RGBA")

ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
od = ImageDraw.Draw(ov)
od.ellipse([W - 620, -380, W + 380, 620], fill=(255, 255, 255, 26))
od.ellipse([W - 300, H - 260, W + 300, H + 340], fill=(255, 255, 255, 16))
img = Image.alpha_composite(img, ov)

fb = "/system/fonts/NotoSansCJK-Bold.ttc"
fr = "/system/fonts/NotoSansCJK-Regular.ttc"
f1 = ImageFont.truetype(fb, 112, index=0)
f2 = ImageFont.truetype(fr, 44, index=0)
f3 = ImageFont.truetype(fb, 32, index=0)

x = 760
labels = ["MIFARE Classic", "离线 · 不上传", "一键校验"]
pills = []
px = x
for lab in labels:
    tw = d.textlength(lab, font=f3)
    pills.append((px, tw, lab))
    px += tw + 52 + 22

ov2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
od2 = ImageDraw.Draw(ov2)
for (px, tw, lab) in pills:
    od2.rounded_rectangle([px, 524, px + tw + 52, 600], radius=38, fill=(255, 255, 255, 34))
img = Image.alpha_composite(img, ov2)

art = Image.open(os.path.join(ROOT, "app/res/mipmap-xxxhdpi/ic_launcher_fg.png")).convert("RGBA")
art = art.resize((640, 640), Image.LANCZOS)
img.alpha_composite(art, (70, 180))

d = ImageDraw.Draw(img)
d.text((x, 236), "门禁卡助手", font=f1, fill=(255, 255, 255, 255))
d.text((x, 392), "读基准卡 · 写复制卡 · 自动比对", font=f2, fill=(255, 255, 255, 220))
for (px, tw, lab) in pills:
    d.text((px + 26, 542), lab, font=f3, fill=(255, 255, 255, 235))
d.text((x, 690), "只复制你本人有权使用的卡", font=f3, fill=(255, 255, 255, 150))

img.convert("RGB").resize((1024, 500), Image.LANCZOS).save(
    os.path.join(ROOT, "dist/play/feature-1024x500.png"))
print("ok")
