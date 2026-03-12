import sys
import json
import re
import traceback

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
    try:
        import pdfplumber
        transactions = []
        date_pattern = re.compile(r'\d{1,4}[-/]\d{1,2}[-/]\d{1,4}|\d{1,2}\s+[a-zA-Z]{3}\s+\d{4}')
        
        with pdfplumber.open(file_path) as pdf:
            for page in pdf.pages:
                table = page.extract_table()
                if not table:
                    continue
                
                # Identify columns based on header or content
                # typical headers: [Date, Description, Debit, Credit, Balance]
                for row in table:
                    if not row or len(row) < 3:
                        continue
                        
                    # Clean None values
                    row = [str(cell).strip() if cell else "" for cell in row]
                    
                    # Try to find date in the first 2 columns
                    date_col = -1
                    tx_date = ""
                    for i in range(min(2, len(row))):
                        m = date_pattern.search(row[i])
                        if m:
                            date_col = i
                            tx_date = m.group()
                            break
                            
                    if date_col == -1:
                        continue # Not a transaction row
                        
                    # Try to find description (usually the widest text column after date)
                    desc_col = date_col + 1
                    description = row[desc_col] if desc_col < len(row) else "Unknown"
                    if len(description) < 3:
                        # Sometimes description spans multiple columns or is shifted
                        desc_col += 1
                        description = row[desc_col] if desc_col < len(row) else "Unknown"
                        
                    # Find amounts. Look for Debit/Credit columns at the end.
                    debit = 0.0
                    credit = 0.0
                    
                    # Scan remaining columns from right to left (skipping the last one if it's balance)
                    # Many statements are: Date | Desc | ID | Debit | Credit | Balance
                    amt_cols = [c for c in row[desc_col+1:] if any(c.replace('.','',1).replace(',','').replace('-','').isdigit() for _ in [1])]
                    
                    if len(amt_cols) >= 2:
                        debit = parse_amount(amt_cols[-2])
                        credit = parse_amount(amt_cols[-1])
                    elif len(amt_cols) == 1:
                        # Single column amount with sign?
                        val = amt_cols[0]
                        if '-' in val or 'dr' in val.lower():
                            debit = parse_amount(val)
                        else:
                            credit = parse_amount(val)
                            
                    if debit == 0.0 and credit == 0.0:
                        continue
                        
                    amount = debit if debit > 0 else credit
                    tx_type = "DEBIT" if debit > 0 else "CREDIT"
                    
                    # Format date strictly to YYYY-MM-DD if possible, else keep raw (Java will try to parse)
                    transactions.append({
                        "txDate": tx_date,
                        "description": description.replace("\n", " ")[:200], # Clean line breaks
                        "type": tx_type,
                        "amount": amount,
                        "category": "Uncategorized"
                    })
                    
        print(json.dumps({"transactions": transactions}))

    except Exception as e:
        print(json.dumps({"error": str(e), "trace": traceback.format_exc()}))
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No PDF path provided"}))
        sys.exit(1)
        
    process_pdf(sys.argv[1])
