#!/usr/bin/env python3
import sys
import os
from PIL import Image, ImageDraw

def generate_icons(source_path, res_dir):
    src = Image.open(source_path).convert("RGBA")
    w, h = src.size

    # 1. 生成 432x432 自适应图标前景色 (严格遵守 Android 66dp/108dp 安全区标准，logo 占 ~52%)
    fg_canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    scale = 224.0 / max(w, h)
    new_w = max(1, int(w * scale))
    new_h = max(1, int(h * scale))
    resized_logo = src.resize((new_w, new_h), Image.Resampling.LANCZOS)
    paste_x = (432 - new_w) // 2
    paste_y = (432 - new_h) // 2
    fg_canvas.paste(resized_logo, (paste_x, paste_y), resized_logo)

    fg_dir = os.path.join(res_dir, "drawable")
    os.makedirs(fg_dir, exist_ok=True)
    fg_canvas.save(os.path.join(fg_dir, "ic_launcher_foreground.png"))

    # 2. 生成各密度普通图标与圆形图标 (规范安全边距 ~58%)
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }
    bg_color = (24, 24, 28, 255)
    border_color = (255, 255, 255, 30)

    for name, size in densities.items():
        dir_path = os.path.join(res_dir, f"mipmap-{name}")
        os.makedirs(dir_path, exist_ok=True)

        # 方圆角
        icon_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(icon_img)
        corner_r = int(size * 0.22)
        draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=corner_r, fill=bg_color, outline=border_color, width=max(1, int(size * 0.02)))

        logo_s = int(size * 0.58)
        scale_s = float(logo_s) / max(w, h)
        mw = max(1, int(w * scale_s))
        mh = max(1, int(h * scale_s))
        m_logo = src.resize((mw, mh), Image.Resampling.LANCZOS)
        icon_img.paste(m_logo, ((size - mw) // 2, (size - mh) // 2), m_logo)
        icon_img.save(os.path.join(dir_path, "ic_launcher.png"))

        # 纯圆
        round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_draw = ImageDraw.Draw(round_img)
        round_draw.ellipse([0, 0, size - 1, size - 1], fill=bg_color, outline=border_color, width=max(1, int(size * 0.02)))
        round_img.paste(m_logo, ((size - mw) // 2, (size - mh) // 2), m_logo)
        round_img.save(os.path.join(dir_path, "ic_launcher_round.png"))

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: generate_icons.py <source_image> <res_dir>")
        sys.exit(1)
    generate_icons(sys.argv[1], sys.argv[2])
