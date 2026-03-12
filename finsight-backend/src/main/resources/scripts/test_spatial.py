import sys
import os
from PIL import Image, ImageDraw, ImageFont

# Create a tall receipt-like image (1000x2500)
img = Image.new('RGB', (1000, 2500), color=(255, 255, 255))
draw = ImageDraw.Draw(img)

# Vendor in Top 20% (y=200)
draw.text((300, 200), "SPATIAL VENDOR", fill=(0,0,0))

# Noise/Other text in Middle (y=1000)
draw.text((300, 1000), "DATE: 2024-03-12", fill=(0,0,0))
draw.text((300, 1100), "IGNORE THIS 999.99", fill=(0,0,0))

# Total in Bottom 30% (y=2200)
draw.text((300, 2200), "GRAND TOTAL", fill=(0,0,0))
draw.text((300, 2300), "Rs. 789.50", fill=(0,0,0))

img.save("/tmp/test_spatial.png")
print("Saved /tmp/test_spatial.png")
