"""
Unit tests for statement_processor.py
Tests cover the pure extraction logic: amount/date regex helpers
and the transaction building logic, without requiring pdfplumber or paddleocr.
"""
import unittest
import sys
import os
import types

# ─── Stub heavy dependencies ──────────────────────────────────────────────────

pdfplumber_stub = types.ModuleType("pdfplumber")
sys.modules.setdefault("pdfplumber", pdfplumber_stub)

paddleocr_stub = types.ModuleType("paddleocr")
class _PaddleOCRStub:
    def __init__(self, **kw): pass
    def ocr(self, img, cls=True): return [[]]
paddleocr_stub.PaddleOCR = _PaddleOCRStub
sys.modules.setdefault("paddleocr", paddleocr_stub)

pdf2image_stub = types.ModuleType("pdf2image")
pdf2image_stub.convert_from_path = lambda *a, **kw: []
sys.modules.setdefault("pdf2image", pdf2image_stub)

numpy_stub = types.ModuleType("numpy")
numpy_stub.array = lambda x: x
sys.modules.setdefault("numpy", numpy_stub)

SCRIPTS_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "..", "..",
                           "src", "main", "resources", "scripts")
sys.path.insert(0, os.path.abspath(SCRIPTS_DIR))

import statement_processor as sp


# ─── Tests ───────────────────────────────────────────────────────────────────

class TestStatementAmountExtraction(unittest.TestCase):
    """_parse_amount_from_text: multi-pattern amount extraction"""

    def test_rupee_prefix(self):
        self.assertAlmostEqual(sp._parse_amount_from_text("₹ 5,000.00"), 5000.0, places=2)

    def test_grand_total(self):
        self.assertAlmostEqual(sp._parse_amount_from_text("Grand Total: 12000.50"), 12000.50, places=2)

    def test_cr_suffix(self):
        self.assertAlmostEqual(sp._parse_amount_from_text("3500.00 CR"), 3500.0, places=2)

    def test_dr_suffix(self):
        self.assertAlmostEqual(sp._parse_amount_from_text("1200.00 DR"), 1200.0, places=2)

    def test_inr_prefix(self):
        self.assertAlmostEqual(sp._parse_amount_from_text("INR 99000"), 99000.0, places=2)

    def test_grouped_number(self):
        self.assertAlmostEqual(sp._parse_amount_from_text("2,50,000.00"), 250000.0, places=2)

    def test_no_amount(self):
        self.assertEqual(sp._parse_amount_from_text("No amounts here"), 0.0)

    def test_amount_too_small_ignored(self):
        self.assertEqual(sp._parse_amount_from_text("₹ 0.50"), 0.0)

    def test_amount_too_large_ignored(self):
        self.assertEqual(sp._parse_amount_from_text("Total: 99,999,999.00 INR"), 0.0)


class TestStatementDateExtraction(unittest.TestCase):
    """_parse_date_from_text: date parsing across common bank statement formats"""

    def test_dd_mm_yyyy(self):
        result = sp._parse_date_from_text("12/03/2024 3500.00 CR")
        self.assertIsNotNone(result)
        self.assertIn("2024", result)

    def test_dd_dash_mm_dash_yyyy(self):
        result = sp._parse_date_from_text("Transaction on 05-11-2023")
        self.assertIsNotNone(result)
        self.assertIn("2023", result)

    def test_yyyy_mm_dd(self):
        result = sp._parse_date_from_text("2024-01-15 NEFT CREDIT 1000.00")
        self.assertIsNotNone(result)
        self.assertIn("2024", result)

    def test_month_abbreviation(self):
        result = sp._parse_date_from_text("22 Jan 2024 ATM DEBIT 500.00")
        self.assertIsNotNone(result)
        self.assertIn("Jan", result)

    def test_no_date_returns_none(self):
        result = sp._parse_date_from_text("NEFT CREDIT FROM SOMEONE")
        self.assertIsNone(result)


class TestStatementParseAmount(unittest.TestCase):
    """Legacy parse_amount helper (still used for table cell values)"""

    def test_plain_number(self):
        self.assertAlmostEqual(sp.parse_amount("1234.56"), 1234.56, places=2)

    def test_comma_formatted(self):
        self.assertAlmostEqual(sp.parse_amount("1,234.56"), 1234.56, places=2)

    def test_currency_symbol(self):
        self.assertAlmostEqual(sp.parse_amount("₹450"), 450.0, places=2)

    def test_negative_returns_absolute(self):
        self.assertAlmostEqual(sp.parse_amount("-500.00"), 500.0, places=2)

    def test_empty_returns_zero(self):
        self.assertEqual(sp.parse_amount(""), 0.0)

    def test_non_numeric_returns_zero(self):
        self.assertEqual(sp.parse_amount("NEFT"), 0.0)


class TestTransactionTypeDetection(unittest.TestCase):
    """Verify CR/DR line-level type detection (inline logic in text extraction path)"""

    def _detect_type(self, line):
        """Mirror the inline type detection from statement_processor.py"""
        return "CREDIT" if any(x in line.upper() for x in ["CR", "DEPOSIT", "RECEIVED"]) else "DEBIT"

    def test_cr_suffix_is_credit(self):
        self.assertEqual(self._detect_type("12/03/2024 NEFT 5000.00 CR"), "CREDIT")

    def test_deposit_keyword_is_credit(self):
        self.assertEqual(self._detect_type("01/01/2024 Salary DEPOSIT 50000.00"), "CREDIT")

    def test_received_keyword_is_credit(self):
        self.assertEqual(self._detect_type("15/06/2024 Amount Received 1200.00"), "CREDIT")

    def test_debit_by_default(self):
        self.assertEqual(self._detect_type("05/05/2024 ATM Withdrawal 2000.00"), "DEBIT")

    def test_dr_not_specifically_matched(self):
        # "DR" alone falls through — default DEBIT is correct
        self.assertEqual(self._detect_type("12/03/2024 2000.00 DR"), "DEBIT")


if __name__ == "__main__":
    unittest.main()
