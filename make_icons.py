import os
from PIL import Image

src_img = r"C:\Users\Arvind\.gemini\antigravity\brain\466aaa8f-cf7f-4215-b35b-089349e94cf2\phonespeakermic_logo_1785694286661.jpg"

base_dir = r"d:\Desktop\phonespeakermic\app\src\main\res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

try:
    img = Image.open(src_img).convert("RGBA")
    
    # 1. Create rounded version for mipmaps
    mask = Image.new("L", img.size, 0)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(mask)
    # Draw rounded rectangle mask
    rad = int(img.width * 0.2)
    draw.rounded_rectangle((0, 0, img.width, img.height), radius=rad, fill=255)
    rounded = img.copy()
    rounded.putalpha(mask)

    # Save to mipmap folders
    for folder, size in sizes.items():
        folder_path = os.path.join(base_dir, folder)
        os.makedirs(folder_path, exist_ok=True)
        out_path = os.path.join(folder_path, "ic_launcher.png")
        out_round = os.path.join(folder_path, "ic_launcher_round.png")
        
        # Save standard
        img_resized = img.resize((size, size), Image.Resampling.LANCZOS)
        img_resized.save(out_path, "PNG")
        
        # Save rounded
        rounded_resized = rounded.resize((size, size), Image.Resampling.LANCZOS)
        rounded_resized.save(out_round, "PNG")

    # 2. Save play store high-res icon
    play_store = img.resize((512, 512), Image.Resampling.LANCZOS)
    play_store.save(os.path.join(base_dir, "..", "ic_launcher-web.png"), "PNG")
    
    # 3. Create favicon for website
    favicon_path = r"d:\Desktop\phonespeakermic\docs\favicon.png"
    fav = rounded.resize((64, 64), Image.Resampling.LANCZOS)
    fav.save(favicon_path, "PNG")

    print("Success: Generated all mipmap icons and favicon!")
except Exception as e:
    print(f"Error: {e}")
