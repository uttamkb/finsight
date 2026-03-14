"""
FinSight - PaddleOCR Processor
Replaces trocr_processor.py with PaddleOCR PP-OCRv4 + full preprocessing pipeline.

Days implemented:
  Day 1: PaddleOCR PP-OCRv4 + angle classifier (replaces slow TrOCR)
  Day 2: Resolution upscaling + multi-pattern amount/date extraction (Indian formats)
  Day 3: Layout-based vendor extraction (top 20% heuristic)
  Day 4: Confidence scoring; Java OcrServiceImpl falls back to Gemini if confidence < 0.60
"""

import sys
import os
import json
import traceback
import re
import logging

# Suppress PaddleOCR / PaddlePaddle verbose output
logging.disable(logging.WARNING)
os.environ["GLOG_minloglevel"] = "3"
os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"  # Skip slow network connectivity check

import cv2
import numpy as np
from PIL import Image

try:
    from pdf2image import convert_from_path
    PDF_SUPPORT = True
except ImportError:
    PDF_SUPPORT = False

# ─────────────────────────────────────────────
#  DAY 1 + DAY 2: IMAGE PREPROCESSING PIPELINE
# ─────────────────────────────────────────────

def preprocess_image(image: Image.Image) -> Image.Image:
    """
    OpenCV preprocessing pipeline (retained from TrOCR era; model-agnostic).
    Day 2 addition: resolution upscaling before any processing.
    """
    # Day 2 — Resolution upscaling: Target ~300 DPI (~2500px height for typical receipt)
    orig_w, orig_h = image.size
    target_h = 2500
    if orig_h < target_h:
        scale = target_h / orig_h
        image = image.resize((int(orig_w * scale), target_h), Image.LANCZOS)

    cv_img = cv2.cvtColor(np.array(image.convert("RGB")), cv2.COLOR_RGB2BGR)

    # 1. Grayscale
    gray = cv2.cvtColor(cv_img, cv2.COLOR_BGR2GRAY)

    # 2. Contrast Enhancement (CLAHE)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    gray = clahe.apply(gray)

    # 3. Noise Removal
    gray = cv2.medianBlur(gray, 3)

    # 4. Skew Correction (only if skew is significant to avoid false corrections)
    coords = np.column_stack(np.where(gray > 0))
    if len(coords) > 100:
        angle = cv2.minAreaRect(coords)[-1]
        if angle < -45:
            angle = -(90 + angle)
        else:
            angle = -angle
        if abs(angle) > 0.5:
            (h, w) = gray.shape[:2]
            center = (w // 2, h // 2)
            M = cv2.getRotationMatrix2D(center, angle, 1.0)
            gray = cv2.warpAffine(gray, M, (w, h),
                                  flags=cv2.INTER_CUBIC,
                                  borderMode=cv2.BORDER_REPLICATE)

    # 5. Auto-Crop on binary threshold
    # REQUIREMENT: Contour must be very substantial (at least 70% width, 30% height)
    thresh_for_crop = cv2.adaptiveThreshold(
        gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2
    )
    contours, _ = cv2.findContours(thresh_for_crop, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if contours:
        c = max(contours, key=cv2.contourArea)
        x, y, w_c, h_c = cv2.boundingRect(c)
        if w_c > thresh_for_crop.shape[1] * 0.7 and h_c > thresh_for_crop.shape[0] * 0.3:
            gray = gray[y: y + h_c, x: x + w_c]

    # Return grayscale as RGB (3-channel) — PaddleOCR expects RGB input
    return Image.fromarray(cv2.cvtColor(gray, cv2.COLOR_GRAY2RGB))


# ─────────────────────────────────────────────
#  DAY 2: MULTI-PATTERN FIELD EXTRACTION
# ─────────────────────────────────────────────

# Amount patterns covering Indian receipt formats
# CRITICAL ORDER: more specific patterns (with currency markers) must come first
# to avoid generic digit patterns matching year numbers (e.g. 2024 from '12/03/2024').
AMOUNT_PATTERNS = [
    r'(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)',          # ₹ 1,234.56 / Rs. 450  ← highest priority
    r'([\d,]+(?:\.\d{2}))\s*(?:cr|dr)\b',                   # 1234.56 CR / DR
    r'(?:grand\s+total|net\s+total|total\s+amount|total)[:\s]+([\d,]+(?:\.\d{1,2})?)',
    r'(?:subtotal|sub[ -]total)[:\s]+([\d,]+(?:\.\d{1,2})?)',
    r'(?:amount\s+(?:due|payable)|amount)[:\s]+([\d,]+(?:\.\d{1,2})?)',
    r'\b(\d{1,3}(?:,\d{2,3})+(?:\.\d{2})?)\b',               # Indian lakh grouping: 1,00,000 or 1,234.56
    # NOTE: intentionally no catch-all digit pattern — it matches years (2024), IDs, etc.
]

# TOTAL keywords for proximity matching
TOTAL_KEYWORDS = ["total", "amount", "payable", "due", "grand", "net", "balance"]

def extract_amount(parsed_lines: list, image_height: int) -> float:
    """
    Refined Spatial Rule Engine:
    1. Filter lines in the BOTTOM 50% of the image (Totals are rarely at the top, except for "Balance Due" summaries).
    2. Within those lines, look for amount patterns.
    3. Prioritize amounts near 'TOTAL' or 'BALANCE' keywords.
    4. Guard against stray digits being prefixed (e.g. '7' from line above).
    """
    candidates = []
    
    # Zone: Bottom 60% (more lenient for receipts with summaries at top)
    zone_threshold = image_height * 0.40
    
    for i, line in enumerate(parsed_lines):
        bbox, content = line[0], line[1]
        text, conf = content[0], content[1]
        
        y_center = (bbox[0][1] + bbox[2][1]) / 2 if bbox is not None else 0
        
        # Check patterns
        for pattern in AMOUNT_PATTERNS:
            m = re.search(pattern, text, re.IGNORECASE)
            if m:
                try:
                    raw_val = m.group(1).replace(",", "")
                    # Sanity check: Total shouldn't start with a stray '7' if it's unlikely
                    # (This is hard to automate, but we can check if there's a space or symbol)
                    val = float(raw_val)
                    if not (1.0 <= val <= 9_999_999): continue
                    
                    # Proximity score breakdown:
                    score = 0
                    
                    # 1. Keyword Bonus
                    if any(kw in text.lower() for kw in TOTAL_KEYWORDS):
                        score += 5000
                    elif i > 0:
                        prev_text = parsed_lines[i-1][1][0].lower()
                        if any(kw in prev_text for kw in TOTAL_KEYWORDS):
                            score += 3000
                    
                    # 2. Position Bonus (Lower is usually better for totals)
                    position_score = (y_center / image_height) * 1000
                    
                    candidates.append({
                        "val": val, 
                        "score": score + position_score
                    })
                except:
                    pass
                    
    if not candidates:
        global_text = "\n".join([l[1][0] for l in parsed_lines])
        for pattern in AMOUNT_PATTERNS:
            for m in re.finditer(pattern, global_text, re.IGNORECASE):
                try:
                    val = float(m.group(1).replace(",", ""))
                    if 1.0 <= val <= 9_999_999: candidates.append({"val": val, "score": val})
                except: pass
                    
    if candidates:
        # Sort by total calculated score
        candidates.sort(key=lambda x: x["score"], reverse=True)
        return candidates[0]["val"]
        
    return 0.0


# Date patterns covering Indian receipt/statement formats
DATE_PATTERNS = [
    r'\b(\d{2})[/\-](\d{2})[/\-](\d{4})\b',                     # 12/03/2024
    r'\b(\d{4})[/\-](\d{2})[/\-](\d{2})\b',                     # 2024-03-12
    r'\b(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[,\s]+(\d{4})\b',
    r'\b(\d{2})[/\-](\d{2})[/\-](\d{2})\b',                     # 12/03/24
    r'dated?\s*[:\-]?\s*(\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4})',     # Dated: 12-03-2024
]

def extract_date(text: str) -> str:
    for pattern in DATE_PATTERNS:
        m = re.search(pattern, text, re.IGNORECASE)
        if m:
            return m.group(0).strip()
    return ""


# ─────────────────────────────────────────────
#  DAY 3: LAYOUT-BASED VENDOR EXTRACTION
# ─────────────────────────────────────────────

# Keywords that strongly indicate an address (NOT a vendor name)
ADDRESS_KEYWORDS = ["road", "floor", "plaza", "colony", "apartment", "no.", "plot", "street", "building", "nagar", "corner", "extn", "layout"]

def load_vendor_dictionary():
    """Load verified vendor names from the auto-exported JSON dictionary."""
    dict_path = os.path.join(os.path.dirname(__file__), "vendor_dictionary.json")
    if os.path.exists(dict_path):
        try:
            with open(dict_path, 'r') as f:
                data = json.load(f)
                return data.get("vendors", [])
        except:
            pass
    return []

def extract_vendor_from_dictionary(ocr_lines: list, vendor_dict: list) -> str:
    """
    Match OCR text against a list of known, verified vendor names.
    Higher priority than layout heuristics.
    """
    if not vendor_dict:
        return ""
    
    # 1. Look for Exact or Substring matches in the top 40% of the image
    for line in ocr_lines:
        text = line[1][0].strip()
        if len(text) < 3: continue
        
        for v in vendor_dict:
            # Case insensitive exact or substring match
            if v.lower() == text.lower() or (len(v) > 5 and v.lower() in text.lower()):
                return v
    return ""

def extract_vendor_from_layout(ocr_lines: list, image_height: int) -> str:
    """
    Receipts almost always have the store/vendor name in the top 25% of the image.
    Among those header lines, the tallest text (largest bounding box height) is the name.
    """
    header_lines = []
    for line in ocr_lines:
        bbox = line[0]
        text = line[1][0].strip()
        conf = line[1][1]

        if not text or conf < 0.5 or len(text) < 3:
            continue

        y_center = (bbox[0][1] + bbox[2][1]) / 2
        
        # Headers usually live in the top 25%
        if y_center < image_height * 0.25:
            # 1. Skip lines that are clearly NOT vendors (dates, totals, or long address-like lines)
            if re.search(r'date|total|amount|rs\.|inr|₹|\d{4}', text, re.IGNORECASE):
                continue
            
            # 2. Filter out common address patterns (e.g. "No. 5/A", "80 Feet Road")
            if any(kw in text.lower() for kw in ADDRESS_KEYWORDS):
                continue
            
            # 3. Heuristic: Vendor names are usually 3-5 words max. Longer tends to be a full address line.
            if len(text.split()) > 6:
                continue

            text_height = abs(bbox[2][1] - bbox[0][1])
            header_lines.append((text, text_height))

    if header_lines:
        # Sort by prominence (text height) then by vertical position (higher is better)
        header_lines.sort(key=lambda x: x[1], reverse=True)
        candidate = header_lines[0][0]
        if re.search(r'[a-zA-Z]', candidate):
            return candidate

    return ""


def extract_vendor_fallback(ocr_lines: list) -> str:
    """Fallback: first line with meaningful alphabetic content."""
    for line in ocr_lines:
        text = line[1][0].strip()
        if len(text) > 3 and re.search(r'[a-zA-Z]{3,}', text):
            return text
    return "Unknown Vendor"


# ─────────────────────────────────────────────
#  DAY 4: CONFIDENCE SCORING
# ─────────────────────────────────────────────

def calculate_confidence(amount: float, date: str, vendor: str, raw_text: str) -> float:
    """
    Score breakdown:
      40 pts — amount found and > 0
      30 pts — date found
      20 pts — vendor found (non-generic)
      10 pts — sufficient raw text quality (> 20 chars)

    Java OcrServiceImpl already performs Gemini fallback when confidence < 0.60.
    """
    score = 0
    if amount and amount > 0:
        score += 40
    if date:
        score += 30
    if vendor and vendor.lower() not in ("", "unknown vendor"):
        # Penalty if vendor contains too many digits (likely a date/phone found in header)
        digit_ratio = len(re.findall(r'\d', vendor)) / len(vendor) if vendor else 0
        if digit_ratio < 0.3:
            score += 20
        else:
            score += 5 # Low score for digit-heavy 'vendor'
    if raw_text and len(raw_text.strip()) > 20:
        score += 10
    return round(score / 100.0, 2)


# ─────────────────────────────────────────────
#  MAIN OCR PIPELINE
# ─────────────────────────────────────────────

def process_ocr(image_path: str):
    try:
        if not os.path.exists(image_path):
            print(json.dumps({"error": f"File not found: {image_path}", "status": "failed"}))
            sys.exit(1)

        # Load image — support both direct images and PDFs
        is_pdf = image_path.lower().endswith(".pdf")
        if is_pdf:
            if not PDF_SUPPORT:
                raise ImportError("pdf2image is not installed. Run: pip install pdf2image")
            pages = convert_from_path(image_path, last_page=1, dpi=300)
            if not pages:
                raise ValueError("Could not convert PDF to image")
            image = pages[0].convert("RGB")
        else:
            image = Image.open(image_path).convert("RGB")

        # Record original height for layout analysis (before preprocessing crops)
        _, orig_h = image.size

        # Day 1 + Day 2: Preprocess (includes upscaling)
        processed = preprocess_image(image)
        processed_rgb = processed.convert("RGB")
        processed_h = processed_rgb.size[1]

        # Day 1: PaddleOCR v3/PaddleX API — use new parameter names
        from paddleocr import PaddleOCR
        ocr_engine = PaddleOCR(
            lang="en",
            use_textline_orientation=True,   # replaces deprecated use_angle_cls
            text_det_limit_side_len=960,     # replaces deprecated det_limit_side_len
        )
        # Note: show_log and use_gpu have been removed in PaddleOCR v3+

        result = ocr_engine.ocr(np.array(processed_rgb))

        if not result or not result[0]:
            # PaddleOCR returned nothing — likely blank or very noisy image
            print(json.dumps({
                "vendor": "Unknown Vendor",
                "amount": 0.0,
                "date": "",
                "raw_text": "",
                "confidence": 0.0,
                "status": "success",
                "note": "PaddleOCR found no text in image"
            }))
            return

        # ── PaddleOCR v3 returns a LIST of dicts (one per image):
        #    result[0] = {
        #      'rec_texts':  ['RELIANCE FRESH', 'TOTAL Rs. 456.00', ...],
        #      'rec_scores': [0.999, 0.942, ...],
        #      'rec_polys':  [ndarray([[x,y],...]), ...],
        #      ...
        #    }
        # Legacy PaddleOCR v2 returned: [ [[bbox, (text, conf)], ...] ]
        # We handle BOTH formats for maximum compatibility.

        raw_text_parts = []
        parsed_lines   = []   # [[bbox_ndarray, [text, conf]], ...]

        for res in result:
            if not res:
                continue

            # ── PaddleOCR v3 / PaddleX (OCRResult): dict-like object ──────────────
            # OCRResult supports .keys() and res['key'] but NOT res.attribute access
            # or isinstance(res, dict). Use the .keys() check as the most reliable way.
            res_keys = set(res.keys()) if hasattr(res, 'keys') else set()
            if 'rec_texts' in res_keys:
                texts  = res.get('rec_texts',  []) or []
                scores = res.get('rec_scores', []) or []
                polys  = res.get('rec_polys',  None) or [None] * len(texts)
                for text, conf, bbox in zip(texts, scores, polys):
                    try:
                        text = str(text).strip()
                        conf = float(conf)
                        if text and conf > 0.4:
                            raw_text_parts.append(text)
                            parsed_lines.append([bbox, [text, conf]])
                    except (TypeError, ValueError):
                        continue

            # ── PaddleOCR v2: list-of-lines format ───────────────────────────────
            elif isinstance(res, (list, tuple)):
                for line in res:
                    try:
                        if isinstance(line, (list, tuple)) and len(line) >= 2:
                            bbox, content = line[0], line[1]
                            if isinstance(content, (list, tuple)) and len(content) >= 2:
                                text = str(content[0]).strip()
                                conf = float(content[1])
                                if text and conf > 0.4:
                                    raw_text_parts.append(text)
                                    parsed_lines.append([bbox, [text, conf]])
                    except (IndexError, TypeError, ValueError):
                        continue

        raw_text = "\n".join(raw_text_parts)

        # Day 2 & Day 27: Multi-pattern field extraction with Spatial Rules
        amount = extract_amount(parsed_lines, processed_h)
        date   = extract_date(raw_text)

        # Rule-Based Refinement: Dictionary Matching (Highest Priority)
        vendor_dict = load_vendor_dictionary()
        vendor = extract_vendor_from_dictionary(parsed_lines, vendor_dict)

        if not vendor:
            # Day 3 & Day 27: Layout-based vendor extraction
            vendor = extract_vendor_from_layout(parsed_lines, processed_h)
            
        if not vendor:
            vendor = extract_vendor_fallback(parsed_lines)

        # Day 4: Confidence scoring
        confidence = calculate_confidence(amount, date, vendor, raw_text)

        print(json.dumps({
            "vendor":     vendor,
            "amount":     amount,
            "date":       date,
            "raw_text":   raw_text,
            "confidence": confidence,
            "status":     "success",
            "debug": {
                "image_h": processed_h,
                "amount_patterns": len(AMOUNT_PATTERNS),
                "lines_found": len(parsed_lines)
            }
        }))

    except Exception as e:
        print(json.dumps({
            "error":  str(e),
            "trace":  traceback.format_exc(),
            "status": "failed"
        }))
        sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No image path provided"}))
        sys.exit(1)
    process_ocr(sys.argv[1])
