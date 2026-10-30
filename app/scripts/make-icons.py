"""从 icons/icon-512.png 生成 Android 图标与启动图。

- mipmap-*/ic_launcher.png          旧版方形图标（Android < 8 与部分启动器）
- mipmap-*/ic_launcher_round.png    圆形图标
- mipmap-*/ic_launcher_foreground.png  自适应图标前景（108dp 画布，图形占中间 66% 安全区）
- values/ic_launcher_background.xml 自适应图标背景色（取自图标主色）
- drawable*/splash.png              启动图（保持原尺寸，换成本图标）
"""
from PIL import Image, ImageDraw
import os
import xml.etree.ElementTree as ET

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(APP)
SRC = os.path.join(REPO, 'icons', 'icon-512.png')
RES = os.path.join(APP, 'android', 'app', 'src', 'main', 'res')

icon = Image.open(SRC).convert('RGBA')
W, H = icon.size

# 主色：取图标右侧中部附近（避开鲸鱼白色主体），用于自适应背景
px = icon.load()
samples = []
for x in range(int(W * 0.06), int(W * 0.16)):
    for y in range(int(H * 0.4), int(H * 0.7)):
        r, g, b, a = px[x, y]
        if a > 200:
            samples.append((r, g, b))
bg = tuple(sum(c[i] for c in samples) // len(samples) for i in range(3)) if samples else (37, 78, 200)
bg_hex = '#%02x%02x%02x' % bg

DENSITIES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}


def rounded(img, size, radius_ratio=0.22):
    img = img.resize((size, size), Image.LANCZOS)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def circle(img, size):
    img = img.resize((size, size), Image.LANCZOS)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


for name, base in DENSITIES.items():
    d = os.path.join(RES, 'mipmap-' + name)
    os.makedirs(d, exist_ok=True)
    rounded(icon, base).save(os.path.join(d, 'ic_launcher.png'))
    circle(icon, base).save(os.path.join(d, 'ic_launcher_round.png'))
    # 自适应前景：108dp 画布，图形占中间 66%
    canvas = base * 108 // 48
    inner = int(canvas * 0.66)
    fg = Image.new('RGBA', (canvas, canvas), (0, 0, 0, 0))
    badge = rounded(icon, inner)
    fg.paste(badge, ((canvas - inner) // 2, (canvas - inner) // 2), badge)
    fg.save(os.path.join(d, 'ic_launcher_foreground.png'))

# 自适应背景色
with open(os.path.join(RES, 'values', 'ic_launcher_background.xml'), 'w', encoding='utf-8') as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    f.write('    <color name="ic_launcher_background">%s</color>\n</resources>\n' % bg_hex)

# 启动图：保持现有尺寸，换成品牌色底 + 居中图标
SPLASH_BG = (245, 246, 248)
for root, dirs, files in os.walk(RES):
    if 'splash.png' in files:
        p = os.path.join(root, 'splash.png')
        try:
            sw, sh = Image.open(p).size
        except Exception:
            continue
        canvas = Image.new('RGBA', (sw, sh), SPLASH_BG + (255,))
        logo_size = int(min(sw, sh) * 0.34)
        logo = rounded(icon, logo_size)
        canvas.paste(logo, ((sw - logo_size) // 2, (sh - logo_size) // 2), logo)
        canvas.convert('RGB').save(p)

print('icons + splash done, bg=' + bg_hex)
