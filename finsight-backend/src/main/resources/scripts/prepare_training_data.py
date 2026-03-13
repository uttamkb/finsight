import os
import json
import shutil
from pathlib import Path

# Paths
HARVESTED_DIR = Path("training_data/harvested")
OUTPUT_DIR = Path("training_data/paddle_dataset")
LABEL_FILE = OUTPUT_DIR / "Label.txt"

def prepare_dataset():
    """
    Converts harvested JSON/Image pairs into PaddleOCR training format.
    Format: img_path\t[{"transcription": "vendor_name", "points": [...]}, ...]
    Note: For light fine-tuning, we often map the whole image to the transcription if it's a cropped receipt.
    """
    if not HARVESTED_DIR.exists():
        print(f"Error: {HARVESTED_DIR} does not exist.")
        return

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    images_dir = OUTPUT_DIR / "images"
    images_dir.mkdir(exist_ok=True)

    annotations = []
    
    # Process all JSON files in harvested dir
    json_files = list(HARVESTED_DIR.glob("*.json"))
    print(f"Found {len(json_files)} samples to process.")

    for jf in json_files:
        try:
            with open(jf, 'r') as f:
                data = json.load(f)
            
            # Find corresponding image file
            base_name = jf.stem
            img_file = None
            for ext in ['.jpg', '.jpeg', '.png', '.img', '.pdf']:
                potential_img = HARVESTED_DIR / (base_name + ext)
                if potential_img.exists():
                    img_file = potential_img
                    break
            
            if not img_file:
                print(f"Warning: No image found for {jf.name}, skipping.")
                continue

            # Copy image to dataset dir
            target_img_path = images_dir / img_file.name
            shutil.copy(img_file, target_img_path)

            # Create annotation line (PaddleOCR Recognition sub-task format for simplicity)
            # Line format: images/file_name.jpg\tVendorName
            # If doing detection + rec, it's more complex, but for "Vendor identification", 
            # we often fine-tune the REC model on the whole header.
            
            # Here we just save a simple rec label map for now
            line = f"images/{img_file.name}\t{data.get('vendor', 'Unknown')}"
            annotations.append(line)

        except Exception as e:
            print(f"Error processing {jf.name}: {e}")

    # Write Label.txt
    with open(LABEL_FILE, 'w', encoding='utf-8') as f:
        for ann in annotations:
            f.write(ann + "\n")

    print(f"Dataset prepared in {OUTPUT_DIR}")
    print(f"Total labeled samples: {len(annotations)}")

if __name__ == "__main__":
    prepare_dataset()
