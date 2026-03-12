import sys
import os
from PIL import Image, ImageDraw, ImageFont

# Create a tall receipt-like image (1000x2500)
img = Image.new('RGB', (1000, 2500), color=(255, 255, 255))
draw = ImageDraw.Draw(img)

# Try to load a font, or default to a large size if possible
try:
    # On macOS, Helvetica is usually available
    font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 100)
except:
    font = ImageFont.load_default() # This will be small, but we try

# Vendor in Top 20% (y=200)
draw.text((100, 200), "RELIANCE RETAIL", fill=(0,0,0), font=font)

# Noise/Other text in Middle (y=1000)
draw.text((100, 1000), "DATE: 2024-03-12", fill=(0,0,0), font=font)
draw.text((100, 1300), "IGNORE 999.99", fill=(0,0,0), font=font)

# Total in Bottom 30% (y=2200)
draw.text((100, 2100), "GRAND TOTAL", fill=(0,0,0), font=font)
draw.text((100, 2300), "Rs. 789.50", fill=(0,0,0), font=font)

img.save("/tmp/test_spatial_v2.png")
print("Saved /tmp/test_spatial_v2.png")
