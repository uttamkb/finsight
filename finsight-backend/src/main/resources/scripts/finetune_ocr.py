import os
import subprocess
from pathlib import Path

# Configuration
PADDLE_OCR_REPO = "https://github.com/PaddlePaddle/PaddleOCR.git"
CONFIG_FILE = "training_data/rec_en_ppocrv4.yml" # You'll need to download the base config from PaddleOCR repo
DATASET_DIR = "training_data/paddle_dataset"
PRETRAINED_MODEL = "training_data/pretrained/rec_v4_base"

def setup_training_env():
    """
    Instructions and boilerplate for OCR fine-tuning.
    Pre-requisites:
    1. Clone PaddleOCR: git clone https://github.com/PaddlePaddle/PaddleOCR.git
    2. Install requirements: pip install -r requirements.txt
    3. Download pre-trained weights for Recognition model.
    """
    print("=== OCR Fine-tuning Setup ===")
    
    if not Path(DATASET_DIR).exists():
        print(f"Error: Dataset not found at {DATASET_DIR}. Run prepare_training_data.py first.")
        return

    print(f"Dataset found with {len(list(Path(DATASET_DIR).glob('images/*')))} images.")
    
    # 1. Boilerplate Command for Recognition Fine-tuning
    # This uses PaddleOCR's distributed training script (can be run on single GPU/CPU)
    train_cmd = [
        "python3", "PaddleOCR/tools/train.py",
        "-c", CONFIG_FILE,
        "-o", f"Train.dataset.data_dir={DATASET_DIR}",
        f"Train.dataset.label_file_list=[{DATASET_DIR}/Label.txt]",
        f"Global.pretrained_model={PRETRAINED_MODEL}",
        "Global.epoch_num=10", # Light fine-tuning
        "Global.save_model_dir=./output/finetuned_ocr/"
    ]

    print("\nTo start fine-tuning, run the following command (requires PaddleOCR repo):")
    print(" ".join(train_cmd))
    
    print("\n💡 TIP: For local machines without GPU, set 'Global.use_gpu=False' in the config.")
    print("Once training is complete, move the exported model to 'src/main/resources/models/custom_ocr'")

if __name__ == "__main__":
    setup_training_env()
