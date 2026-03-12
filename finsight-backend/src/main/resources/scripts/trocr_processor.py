import sys
import os
import torch
from transformers import TrOCRProcessor, VisionEncoderDecoderModel
from PIL import Image
import json
import traceback

# Set level to avoid spamming logs
import logging
logging.getLogger("transformers").setLevel(logging.ERROR)

# Redirect HuggingFace cache to a local writable directory
local_cache = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hf_cache")
os.makedirs(local_cache, exist_ok=True)
os.environ["TRANSFORMERS_CACHE"] = local_cache
os.environ["HF_HOME"] = local_cache
os.environ["XDG_CACHE_HOME"] = local_cache

import cv2
import numpy as np
from pdf2image import convert_from_path

def preprocess_image(image):
    # Convert PIL to CV2 (BGR)
    cv_img = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
    
    # 1. Grayscale
    gray = cv2.cvtColor(cv_img, cv2.COLOR_BGR2GRAY)
    
    # 2. Contrast Enhancement (CLAHE)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8,8))
    gray = clahe.apply(gray)
    
    # 3. Noise Removal
    gray = cv2.medianBlur(gray, 3)
    
    # 4. Skew Correction
    coords = np.column_stack(np.where(gray > 0))
    angle = cv2.minAreaRect(coords)[-1]
    if angle < -45:
        angle = -(90 + angle)
    else:
        angle = -angle
    (h, w) = gray.shape[:2]
    center = (w // 2, h // 2)
    M = cv2.getRotationMatrix2D(center, angle, 1.0)
    gray = cv2.warpAffine(gray, M, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
    
    # 5. Adaptive Thresholding
    thresh = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2)
    
    # 6. Cropping (Find largest contour)
    contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if contours:
        c = max(contours, key=cv2.contourArea)
        x, y, w_c, h_c = cv2.boundingRect(c)
        if w_c > w/4 and h_c > h/4: # Only crop if contour is significant
            thresh = thresh[y:y+h_c, x:x+w_c]
    
    # 7. Normalization (Resize to height 800 while maintaining aspect ratio)
    h_orig, w_orig = thresh.shape
    aspect = w_orig / h_orig
    new_h = 800
    new_w = int(new_h * aspect)
    thresh = cv2.resize(thresh, (new_w, new_h), interpolation=cv2.INTER_AREA)

    # Convert back to PIL
    return Image.fromarray(thresh)

def process_ocr(image_path):
    try:
        if not os.path.exists(image_path):
            print(json.dumps({"error": f"File not found: {image_path}"}))
            sys.exit(1)

        # Handle PDF conversion
        is_pdf = image_path.lower().endswith(".pdf")
        if is_pdf:
            pages = convert_from_path(image_path, last_page=1)
            if not pages:
                raise ValueError("Could not convert PDF to image")
            image = pages[0].convert("RGB")
        else:
            image = Image.open(image_path).convert("RGB")
        
        # Apply Advanced Preprocessing
        image = preprocess_image(image)
        
        # Load model and processor
        processor = TrOCRProcessor.from_pretrained("microsoft/trocr-base-printed")
        model = VisionEncoderDecoderModel.from_pretrained("microsoft/trocr-base-printed")
        
        pixel_values = processor(images=image.convert("RGB"), return_tensors="pt").pixel_values
        
        generated_ids = model.generate(pixel_values)
        generated_text = processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
        
        # Return as JSON for easy parsing in Java
        print(json.dumps({
            "text": generated_text,
            "status": "success"
        }))
        
    except Exception as e:
        print(json.dumps({"error": str(e), "trace": traceback.format_exc(), "status": "failed"}))
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No image path provided"}))
        sys.exit(1)
    process_ocr(sys.argv[1])
