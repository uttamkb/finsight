import sys
import json
import os
import re
import traceback
from datetime import datetime

# PaddleOCR v3: skip slow model host connectivity check
os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"

# --- Extraction Patterns ---

DATE_PATTERNS = [
    r'\b(\d{2})[/\-](\d{2})[/\-](\d{4})\b',
    r'\b(\d{4})[/\-](\d{2})[/\-](\d{2})\b',
    r'\b(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[,\s]+(\d{4})\b',
    r'\b(\d{1,2})[-/](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[-/](\d{4})\b',
    r'\b(\d{2})[/\-](\d{2})[/\-](\d{2})\b', # DD/MM/YY
]

# Patterns to identify columns
HEADER_MAPPINGS = {
    'txDate': ['date', 'txn date', 'transaction date', 'value date', 'posting date'],
    'description': ['description', 'narration', 'particulars', 'remarks', 'details', 'tran details'],
    'withdrawal': ['withdrawal', 'debit', 'dr', 'amount(dr)', 'withdrawal amt'],
    'deposit': ['deposit', 'credit', 'cr', 'amount(cr)', 'deposit amt'],
    'amount': ['amount', 'txn amount', 'transaction amount', 'balance'], # Balance often confused with amount
}

# Step 6: Vendor Detection Keywords
VENDOR_CLEANUP_PATTERNS = [
    r'VISA\s*\*?\s*(.*?)(?:\d|$)',
    r'POS\s*\*?\s*(.*?)(?:\d|$)',
    r'UBER\s*\*?\s*(.*?)$',
    r'AMAZON\s*\*?\s*(.*?)$',
    r'ZOMATO\s*\*?\s*(.*?)$',
    r'SWIGGY\s*\*?\s*(.*?)$',
]


def clean_amount(val_str):
    if not val_str:
        return 0.0
    # Remove currency symbols and formatting
    clean = re.sub(r'[^\d\.-]', '', str(val_str))
    try:
        return abs(float(clean))
    except ValueError:
        return 0.0

def parse_date(text):
    if not text: return None
    for pat in DATE_PATTERNS:
        m = re.search(pat, str(text), re.IGNORECASE)
        if m:
            return m.group(0).strip()
    return None

def detect_vendor(description):
    """Step 6: Extract a clean vendor name from a messy narration"""
    if not description: return "Unknown"
    
    desc_clean = str(description).upper()
    
    # 1. Pattern matching for common prefixes
    for pat in VENDOR_CLEANUP_PATTERNS:
        m = re.search(pat, desc_clean)
        if m and m.group(1).strip():
            return m.group(1).strip()
            
    # 2. Heuristic: Take first part of slash-separated strings
    if "/" in desc_clean:
        parts = desc_clean.split("/")
        if len(parts[0]) > 3: return parts[0].strip()
        
    # 3. Last fallback: Keep as is but trim
    return description[:50].strip()

def calculate_row_confidence(row_data):
    """Calculate confidence for a single normalized transaction row (0-100)"""
    score = 0
    if row_data.get('txDate'): score += 30
    if row_data.get('amount') and row_data.get('amount') > 0: score += 30
    if row_data.get('description') and len(row_data['description']) > 5: score += 20
    if row_data.get('type') in ['DEBIT', 'CREDIT']: score += 20
    return score

def extract_with_pdfplumber(file_path, debug_log):
    import pdfplumber
    extracted = []
    try:
        with pdfplumber.open(file_path) as pdf:
            debug_log.append(f"pdfplumber: processing {len(pdf.pages)} pages")
            for page in pdf.pages:
                tables = page.extract_tables()
                if tables:
                    for table in tables:
                        # process_raw_table directly performs transaction parsing
                        parsed_txns = process_raw_table(table, "pdfplumber-table")
                        if parsed_txns:
                            extracted.extend(parsed_txns)
                
                # Also try text extraction if no tables or to complement
                text = page.extract_text()
                if text and len(extracted) == 0:
                    for line in text.split('\n'):
                        row = line.split()
                        if len(row) >= 3:
                            parsed_txns_text = process_raw_table([row], "pdfplumber-text")
                            if parsed_txns_text:
                                extracted.extend(parsed_txns_text)
    except Exception as e:
        debug_log.append(f"pdfplumber error: {str(e)}")
    return extracted

def extract_with_camelot(file_path, debug_log):
    import camelot
    extracted = []
    try:
        debug_log.append("camelot: attempting lattice mode")
        tables = camelot.read_pdf(file_path, pages='all', flavor='lattice')
        for table in tables:
            extracted.extend(process_raw_table(table.data, "camelot-lattice"))
        
        debug_log.append("camelot: attempting stream mode")
        tables = camelot.read_pdf(file_path, pages='all', flavor='stream')
        for table in tables:
            extracted.extend(process_raw_table(table.data, "camelot-stream"))
    except Exception as e:
        debug_log.append(f"camelot error: {str(e)}")
    return extracted

def process_raw_table(table, source_name):
    """Normalize raw table data into list of transaction dicts"""
    if not table or len(table) < 1: return []
    
    results = []
    # Identify column indices
    col_map = {'txDate': -1, 'description': -1, 'withdrawal': -1, 'deposit': -1, 'amount': -1}
    
    # Check first 3 rows for headers
    for i in range(min(3, len(table))):
        row = [str(c).lower() for c in table[i]]
        for key, aliases in HEADER_MAPPINGS.items():
            for alias in aliases:
                if alias in row:
                    col_map[key] = row.index(alias)
                    break
    
    # Process rows
    for row in table:
        row = [str(c).strip() for c in row]
        # Ignore obvious header rows or short rows
        if len(row) < 3: continue
        
        tx_date = None
        # Try mapped column first
        if col_map['txDate'] != -1 and col_map['txDate'] < len(row):
            tx_date = parse_date(row[col_map['txDate']])
        
        # Fallback: search all columns for a date
        if not tx_date:
            for cell in row:
                tx_date = parse_date(cell)
                if tx_date: break
        
        if not tx_date: continue

        # Description
        description = ""
        if col_map['description'] != -1 and col_map['description'] < len(row):
            description = row[col_map['description']]
        else:
            # Fallback: find longest string
            description = max(row, key=len)
        
        # Amount and Type
        amount = 0.0
        tx_type = "DEBIT"
        
        w_idx = col_map['withdrawal']
        d_idx = col_map['deposit']
        a_idx = col_map['amount']
        
        if w_idx != -1 and d_idx != -1 and w_idx < len(row) and d_idx < len(row):
            w_val = clean_amount(row[w_idx])
            d_val = clean_amount(row[d_idx])
            if d_val > 0:
                amount = d_val
                tx_type = "CREDIT"
            else:
                amount = w_val
                tx_type = "DEBIT"
        elif a_idx != -1 and a_idx < len(row):
            amount = clean_amount(row[a_idx])
            # Check for sign or keywords in description
            if "-" in row[a_idx] or "DR" in row[a_idx].upper():
                tx_type = "DEBIT"
            elif "CR" in row[a_idx].upper():
                tx_type = "CREDIT"
        else:
            # Fallback: search for numbers
            nums = []
            for cell in row:
                val = clean_amount(cell)
                if val > 0: nums.append(val)
            if nums:
                amount = nums[0] # Often first number after date is it
        
        if amount > 0:
            results.append({
                "txDate": tx_date,
                "description": description.replace('\n', ' ')[:200],
                "vendor": detect_vendor(description),
                "type": tx_type,
                "amount": amount,
                "category": "Uncategorized",
                "source": source_name
            })
            
    return results

def deduplicate_transactions(transactions):
    seen = set()
    unique = []
    for tx in transactions:
        # Create a key based on date, description truncated, and amount
        key = (tx['txDate'], tx['description'][:30].lower(), round(tx['amount'], 2), tx['type'])
        if key not in seen:
            seen.add(key)
            unique.append(tx)
    return unique

def process_pdf(file_path):
    debug_log = []
    all_transactions = []
    
    try:
        # STEP 1: pdfplumber table extraction
        debug_log.append("Attempting extraction with pdfplumber...")
        plumber_txns = extract_with_pdfplumber(file_path, debug_log)
        all_transactions.extend(plumber_txns)
        
        # Calculate initial confidence (transaction parsing is handled inside process_raw_table)
        unique_txs = deduplicate_transactions(all_transactions)
        avg_confidence = 0.0
        
        if unique_txs:
            total_score = sum(calculate_row_confidence(tx) for tx in unique_txs)
            avg_confidence = total_score / len(unique_txs)
            debug_log.append(f"Initial pdfplumber confidence: {avg_confidence}%")
            
        # STEP 2: camelot fallback based on confidence threshold
        if not unique_txs or avg_confidence < 70:
            debug_log.append("Confidence threshold < 70 or 0 txns. Falling back to camelot...")
            camelot_txns = extract_with_camelot(file_path, debug_log)
            # Append camelot results on top of existing ones
            if camelot_txns:
                 all_transactions.extend(camelot_txns)
                 unique_txs = deduplicate_transactions(all_transactions)
                 
                 # Recalculate confidence after camelot additions
                 if unique_txs:
                     total_score = sum(calculate_row_confidence(tx) for tx in unique_txs)
                     avg_confidence = total_score / len(unique_txs)
                     debug_log.append(f"Post-camelot confidence: {avg_confidence}%")
        
        # Final JSON Output to Spring Boot
        print(json.dumps({
            "transactions": unique_txs,
            "confidenceScore": avg_confidence,
            "debug": debug_log
        }))

    except Exception as e:
        print(json.dumps({
            "error": str(e),
            "trace": traceback.format_exc(),
            "debug": debug_log
        }))
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No PDF path provided"}))
        sys.exit(1)
        
    process_pdf(sys.argv[1])
