import sys
import json
import re
import traceback

# ─── Multi-pattern amount/date extractors (shared with paddle_ocr_processor) ───

AMOUNT_PATTERNS = [
    r'(?:rs\.?|inr|\u20b9)\s*([\d,]+(?:\.\d{1,2})?)',
    r'([\d,]+(?:\.\d{2}))\s*(?:cr|dr)\b',
    r'(?:grand\s+total|net\s+total|total\s+amount|total)[:\s]+([\d,]+(?:\.\d{1,2})?)',
    r'(?:amount\s+(?:due|payable)|amount)[:\s]+([\d,]+(?:\.\d{1,2})?)',
    r'\b(\d{1,3}(?:,\d{2,3})+(?:\.\d{2})?)\b',               # Indian lakh grouping: 2,50,000
]

DATE_PATTERNS = [
    r'\b(\d{2})[/\-](\d{2})[/\-](\d{4})\b',
    r'\b(\d{4})[/\-](\d{2})[/\-](\d{2})\b',
    r'\b(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[,\s]+(\d{4})\b',
]

def _parse_date_from_text(text):
    for pat in DATE_PATTERNS:
        m = re.search(pat, text, re.IGNORECASE)
        if m:
            return m.group(0).strip()
    return None

def _parse_amount_from_text(text):
    amounts = []
    for pat in AMOUNT_PATTERNS:
        for m in re.finditer(pat, text, re.IGNORECASE):
            try:
                val = float(m.group(1).replace(',', ''))
                if 1.0 <= val <= 9_999_999:
                    amounts.append(val)
            except (ValueError, IndexError):
                pass
    return max(amounts) if amounts else 0.0

def parse_amount(val_str):
    if not val_str:
        return 0.0
    # Remove currency symbols and formatting
    clean = re.sub(r'[^\d\.-]', '', val_str)
    try:
        return abs(float(clean))
    except ValueError:
        return 0.0

def process_pdf(file_path):
    debug_log = []
    try:
        import pdfplumber
        transactions = []
        # Support various date formats: DD/MM/YYYY, YYYY-MM-DD, DD-MMM-YYYY
        date_pattern = re.compile(r'\b(?:\d{1,4}[-/]\d{1,2}[-/]\d{1,4}|\d{1,2}\s+[a-zA-Z]{3,}\s+\d{4})\b', re.IGNORECASE)
        
        with pdfplumber.open(file_path) as pdf:
            debug_log.append(f"Processing {len(pdf.pages)} pages")
            for page_num, page in enumerate(pdf.pages):
                # Try multiple strategies: extract_table() and extract_text()
                table = page.extract_table()
                if not table:
                    # Fallback to text-based extraction if table detection fails
                    text = page.extract_text()
                    if text:
                        debug_log.append(f"Page {page_num}: No table found, using text regex")
                        for line in text.split('\n'):
                            tx_date = _parse_date_from_text(line)
                            if tx_date:
                                amount = _parse_amount_from_text(line)
                                if amount > 0:
                                    tx_type = "CREDIT" if any(x in line.upper() for x in ["CR", "DEPOSIT", "RECEIVED"]) else "DEBIT"
                                    transactions.append({
                                        "txDate": tx_date,
                                        "description": line[:100].strip(),
                                        "type": tx_type,
                                        "amount": amount,
                                        "category": "Uncategorized"
                                    })
                    else:
                        # ── Scanned PDF path: page is an image, use PaddleOCR ──
                        debug_log.append(f"Page {page_num}: Empty text layer — attempting PaddleOCR on scanned image")
                        try:
                            from pdf2image import convert_from_path
                            from paddleocr import PaddleOCR
                            import numpy as np
                            pages_img = convert_from_path(file_path, first_page=page_num+1, last_page=page_num+1, dpi=200)
                            if pages_img:
                                # Bare minimum initialization to avoid 'Unknown argument' errors on this environment
                                ocr_engine = PaddleOCR(lang='en')
                                ocr_result = ocr_engine.ocr(np.array(pages_img[0].convert("RGB")))
                                
                                if ocr_result and ocr_result[0]:
                                    # Robust parsing of OCR result structure
                                    paddle_text_parts = []
                                    res_list = ocr_result[0]
                                    for line_item in res_list:
                                        try:
                                            if isinstance(line_item, (list, tuple)) and len(line_item) >= 2:
                                                content = line_item[1]
                                                if isinstance(content, (list, tuple)) and len(content) >= 2:
                                                    text = str(content[0]).strip()
                                                    conf = float(content[1])
                                                    if text and conf > 0.4:
                                                        paddle_text_parts.append(text)
                                        except (IndexError, TypeError):
                                            continue
                                            
                                    paddle_text = '\n'.join(paddle_text_parts)
                                    debug_log.append(f"Page {page_num}: PaddleOCR extracted {len(paddle_text_parts)} lines")
                                    
                                    for line in paddle_text.split('\n'):
                                        tx_date = _parse_date_from_text(line)
                                        if tx_date:
                                            amount = _parse_amount_from_text(line)
                                            if amount > 0:
                                                tx_type = "CREDIT" if any(x in line.upper() for x in ["CR", "DEPOSIT", "RECEIVED"]) else "DEBIT"
                                                transactions.append({
                                                    "txDate": tx_date,
                                                    "description": line[:100].strip(),
                                                    "type": tx_type,
                                                    "amount": amount,
                                                    "category": "Uncategorized"
                                                })
                        except Exception as paddle_err:
                            debug_log.append(f"Page {page_num}: PaddleOCR scanned path failed: {str(paddle_err)}")
                    # No table found — text/OCR extraction already handled above; skip table loop
                    continue

                debug_log.append(f"Page {page_num}: Found table with {len(table)} rows")
                for row_num, row in enumerate(table):
                    if not row or len(row) < 2:
                        continue
                        
                    row = [str(cell).strip() if cell else "" for cell in row]
                    
                    # 1. Flexible Date Search (any column)
                    tx_date = ""
                    date_col = -1
                    for i, cell in enumerate(row):
                        m = date_pattern.search(cell)
                        if m:
                            tx_date = m.group()
                            date_col = i
                            break
                    
                    if not tx_date:
                        continue
                        
                    # 2. Flexible Amount Search
                    # Look for numerical values that look like currency (skip date_col)
                    potential_amounts = []
                    for i, cell in enumerate(row):
                        if i == date_col: continue
                        # Match: 123.45 or 1,234.56 or -123.45
                        if re.search(r'^-?[\d,]+\.\d{2}$', cell.replace(' ','')):
                            val = parse_amount(cell)
                            if val > 0:
                                potential_amounts.append((i, val))
                    
                    if not potential_amounts:
                        continue
                        
                    # 3. Description identification
                    # Take the column with the most alphabetic text
                    desc_candidates = []
                    for i, cell in enumerate(row):
                        if i == date_col or any(i == p[0] for p in potential_amounts):
                            continue
                        alpha_count = len(re.findall(r'[a-zA-Z]', cell))
                        desc_candidates.append((i, alpha_count, cell))
                    
                    description = "Unknown Transaction"
                    if desc_candidates:
                        # Sort by alpha count descending
                        desc_candidates.sort(key=lambda x: x[1], reverse=True)
                        description = desc_candidates[0][2]
                    
                    # 4. Resolve Type
                    # If multiple amounts, second to last is often Debit, last is Credit
                    # If one amount, try to infer from row text (sign/narrative)
                    amount = potential_amounts[-1][1]
                    tx_type = "DEBIT" # Default
                    
                    if len(potential_amounts) >= 2:
                        debit_val = potential_amounts[-2][1]
                        credit_val = potential_amounts[-1][1]
                        if credit_val > 0 and debit_val == 0:
                            amount = credit_val
                            tx_type = "CREDIT"
                        elif debit_val > 0:
                           amount = debit_val
                           tx_type = "DEBIT"
                    
                    transactions.append({
                        "txDate": tx_date,
                        "description": (description or "Statement Line").replace("\n", " ")[:200],
                        "type": tx_type,
                        "amount": amount,
                        "category": "Uncategorized"
                    })
                    
        print(json.dumps({"transactions": transactions, "debug": debug_log}))

    except Exception as e:
        print(json.dumps({"error": str(e), "trace": traceback.format_exc(), "debug": debug_log}))
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No PDF path provided"}))
        sys.exit(1)
        
    process_pdf(sys.argv[1])
