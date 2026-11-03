# 圖片 → 1920x1080 桌布：主圖放在 App 列上方，四周以模糊、壓暗填滿並羽化邊緣。
# 用法：python3 make.py <照片> <輸出.jpg>
import sys
from PIL import Image, ImageOps, ImageFilter, ImageEnhance, ImageDraw
TOP, BOTTOM, FEATHER = 210, 760, 36          # 照片可用區：時鐘下緣～App 列上緣（實機 1080p 截圖量的）
src = ImageOps.exif_transpose(Image.open(sys.argv[1])).convert("RGB")
bg = ImageOps.fit(src, (1920, 1080), Image.LANCZOS).filter(ImageFilter.GaussianBlur(45))
bg = ImageEnhance.Brightness(bg).enhance(0.55)
h = BOTTOM - TOP; w = round(src.width * h / src.height)
fg = src.resize((w, h), Image.LANCZOS)
mask = Image.new("L", (w, h), 0); ImageDraw.Draw(mask).rectangle([FEATHER, FEATHER, w - FEATHER, h - FEATHER], fill=255)
mask = mask.filter(ImageFilter.GaussianBlur(FEATHER / 2))
bg.paste(fg, ((1920 - w) // 2, TOP), mask)
bg.save(sys.argv[2], quality=88)
