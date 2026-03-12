"""
Unit tests for paddle_ocr_processor.py
Tests focus on the pure Python logic (amount extraction, date extraction,
vendor layout extraction, confidence scoring) — all without requiring
PaddleOCR, pdf2image, or OpenCV to be installed in the test environment.
"""
import unittest
import sys
import os
import json
import importlib
import types

# ─── Stub out all heavy optional dependencies before import ──────────────────

# stub paddleocr
paddleocr_stub = types.ModuleType("paddleocr")
class _PaddleOCRStub:
    def __init__(self, **kwargs): pass
    def ocr(self, img, cls=True): return [[]]
paddleocr_stub.PaddleOCR = _PaddleOCRStub
sys.modules.setdefault("paddleocr", paddleocr_stub)

# stub paddlepaddle (paddle)
paddle_stub = types.ModuleType("paddle")
sys.modules.setdefault("paddle", paddle_stub)

# stub cv2
cv2_stub = types.ModuleType("cv2")
import numpy as _np
class _CLAHE:
    def apply(self, img): return img
cv2_stub.cvtColor = lambda img, code: img
cv2_stub.COLOR_RGB2BGR = 0
cv2_stub.COLOR_BGR2GRAY = 0
cv2_stub.createCLAHE = lambda **kw: _CLAHE()
cv2_stub.medianBlur = lambda img, k: img
cv2_stub.minAreaRect = lambda c: (None, None, -10)
cv2_stub.getRotationMatrix2D = lambda c, a, s: _np.eye(2, 3)
cv2_stub.warpAffine = lambda img, M, dsize, **kw: img
cv2_stub.adaptiveThreshold = lambda *a, **kw: a[0]
cv2_stub.ADAPTIVE_THRESH_GAUSSIAN_C = 0
cv2_stub.THRESH_BINARY = 0
cv2_stub.findContours = lambda *a: ([], None)
cv2_stub.RETR_EXTERNAL = 0
cv2_stub.CHAIN_APPROX_SIMPLE = 0
cv2_stub.boundingRect = lambda c: (0, 0, 100, 100)
cv2_stub.contourArea = lambda c: 0
sys.modules.setdefault("cv2", cv2_stub)

# stub pdf2image
pdf2image_stub = types.ModuleType("pdf2image")
pdf2image_stub.convert_from_path = lambda *a, **kw: []
sys.modules.setdefault("pdf2image", pdf2image_stub)

# Add the scripts directory to path so we can import the module
SCRIPTS_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "..", "..",
                           "src", "main", "resources", "scripts")
sys.path.insert(0, os.path.abspath(SCRIPTS_DIR))

# Now import after stubs are in place
import paddle_ocr_processor as poc


# ─── Tests ───────────────────────────────────────────────────────────────────

class TestAmountExtraction(unittest.TestCase):
    """Day 2: Multi-pattern amount extraction"""

    def _make_amount_line(self, text, y=2200):
        # Default y=2200 puts it in the bottom 12% of a 2500px image (passes bottom 30% rule)
        bbox = [[0, y], [100, y], [100, y+30], [0, y+30]]
        return [[bbox, [text, 0.95]]]

    def test_rupee_symbol(self):
        self.assertAlmostEqual(poc.extract_amount(self._make_amount_line("Total ₹ 1,234.56"), 2500), 1234.56, places=2)

    def test_rs_prefix(self):
        self.assertAlmostEqual(poc.extract_amount(self._make_amount_line("Rs. 450.00 paid"), 2500), 450.0, places=2)

    def test_inr_prefix(self):
        self.assertAlmostEqual(poc.extract_amount(self._make_amount_line("INR 78900"), 2500), 78900.0, places=2)

    def test_grand_total_label(self):
        self.assertAlmostEqual(poc.extract_amount(self._make_amount_line("Grand Total: 2500.00"), 2500), 2500.0, places=2)

    def test_cr_suffix(self):
        self.assertAlmostEqual(poc.extract_amount(self._make_amount_line("5000.00 CR"), 2500), 5000.0, places=2)

    def test_amount_with_commas(self):
        self.assertAlmostEqual(poc.extract_amount(self._make_amount_line("1,00,000.00"), 2500), 100000.0, places=2)

    def test_no_amount_returns_zero(self):
        self.assertEqual(poc.extract_amount(self._make_amount_line("No numbers here"), 2500), 0.0)

    def test_returns_maximum_amount(self):
        # Should return 5000, not 100 (proximity bonus + max check)
        lines = self._make_amount_line("Subtotal: 100.00", y=2100)
        lines += self._make_amount_line("Grand Total: 5000.00", y=2300)
        result = poc.extract_amount(lines, 2500)
        self.assertAlmostEqual(result, 5000.0, places=2)

    def test_ignores_tiny_amounts(self):
        # Values < 1.0 should be ignored
        self.assertEqual(poc.extract_amount(self._make_amount_line("0.50 paise"), 2500), 0.0)

    def test_ignores_huge_amounts(self):
        # Values > 9,999,999 should be rejected
        self.assertEqual(poc.extract_amount(self._make_amount_line("Total: 99,999,999.00"), 2500), 0.0)


class TestDateExtraction(unittest.TestCase):
    """Day 2: Multi-pattern date extraction"""

    def test_dd_mm_yyyy_slash(self):
        result = poc.extract_date("Date: 12/03/2024")
        self.assertIn("12", result)
        self.assertIn("2024", result)

    def test_dd_mm_yyyy_dash(self):
        result = poc.extract_date("Invoice date 05-11-2023")
        self.assertIn("05", result)
        self.assertIn("2023", result)

    def test_yyyy_mm_dd(self):
        result = poc.extract_date("Issued: 2024-03-12")
        self.assertEqual(result, "2024-03-12")

    def test_dd_mon_yyyy(self):
        result = poc.extract_date("Dated: 15 Mar 2024")
        self.assertIsNotNone(result)
        self.assertIn("Mar", result)

    def test_dated_prefix(self):
        result = poc.extract_date("Dated: 01-01-2024")
        self.assertIn("01", result)

    def test_no_date_returns_empty(self):
        result = poc.extract_date("No date in this text at all")
        self.assertEqual(result, "")


class TestVendorExtraction(unittest.TestCase):
    """Day 3: Layout-based vendor extraction"""

    def _make_line(self, text, y_top, y_bottom, conf=0.95):
        """Helper: creates a PaddleOCR-format line result."""
        bbox = [[0, y_top], [200, y_top], [200, y_bottom], [0, y_bottom]]
        return [bbox, [text, conf]]

    def test_picks_top_20_percent(self):
        image_height = 1000
        lines = [
            self._make_line("RELIANCE MART", 50, 100),    # top 10% — vendor
            self._make_line("Date: 12/03/2024", 300, 330), # not header
            self._make_line("Total: 450.00", 800, 830),   # near bottom
        ]
        vendor = poc.extract_vendor_from_layout(lines, image_height)
        self.assertEqual(vendor, "RELIANCE MART")

    def test_picks_largest_text_in_header(self):
        image_height = 1000
        lines = [
            self._make_line("Phone: 9876543210", 10, 25),  # smaller text
            self._make_line("SUPERMART PVT LTD", 40, 80),  # larger — tallest
            self._make_line("GST: 29AAAA0000A1Z5", 90, 110),
        ]
        vendor = poc.extract_vendor_from_layout(lines, image_height)
        self.assertEqual(vendor, "SUPERMART PVT LTD")

    def test_returns_empty_when_no_header_lines(self):
        image_height = 1000
        lines = [
            self._make_line("Item: Milk", 500, 530),
            self._make_line("Total: 45.00", 900, 930),
        ]
        vendor = poc.extract_vendor_from_layout(lines, image_height)
        self.assertEqual(vendor, "")

    def test_skips_low_confidence_lines(self):
        image_height = 1000
        lines = [
            self._make_line("GARBAGE TEXT", 20, 60, conf=0.1),  # low conf, skipped
            self._make_line("REAL VENDOR", 70, 120, conf=0.95),
        ]
        vendor = poc.extract_vendor_from_layout(lines, image_height)
        self.assertEqual(vendor, "REAL VENDOR")

    def test_requires_alphabetic_character(self):
        image_height = 1000
        lines = [
            self._make_line("123456789", 10, 50),  # no letters — rejected
        ]
        vendor = poc.extract_vendor_from_layout(lines, image_height)
        self.assertEqual(vendor, "")


class TestConfidenceScoring(unittest.TestCase):
    """Day 4: Confidence scoring"""

    def test_full_confidence(self):
        score = poc.calculate_confidence(450.0, "12/03/2024", "RELIANCE MART", "Some text here blah blah")
        self.assertEqual(score, 1.00)

    def test_missing_amount_drops_40pts(self):
        score = poc.calculate_confidence(0.0, "12/03/2024", "VENDOR", "text text text text text")
        self.assertAlmostEqual(score, 0.60, places=2)

    def test_missing_date_drops_30pts(self):
        score = poc.calculate_confidence(500.0, "", "VENDOR", "text text text text text")
        self.assertAlmostEqual(score, 0.70, places=2)

    def test_missing_vendor_drops_20pts(self):
        score = poc.calculate_confidence(500.0, "12/03/2024", "", "text text text text text")
        self.assertAlmostEqual(score, 0.80, places=2)

    def test_empty_text_drops_10pts(self):
        score = poc.calculate_confidence(500.0, "12/03/2024", "VENDOR", "")
        self.assertAlmostEqual(score, 0.90, places=2)

    def test_zero_everything_is_zero(self):
        score = poc.calculate_confidence(0.0, "", "", "")
        self.assertEqual(score, 0.00)

    def test_below_threshold_triggers_fallback(self):
        # Verify that the Java fallback threshold (0.75) is meaningful:
        # Missing amount = score 0.60 = triggers fallback (threshold 0.75)
        score = poc.calculate_confidence(0.0, "12/03/2024", "VENDOR", "text text text text text")
        self.assertLess(score, 0.75)

    def test_unknown_vendor_treated_as_missing(self):
        score = poc.calculate_confidence(500.0, "12/03/2024", "unknown vendor", "text text text text text")
        self.assertAlmostEqual(score, 0.80, places=2)


class TestVendorFallback(unittest.TestCase):
    """extract_vendor_fallback — alternative when layout extraction fails"""

    def _make_line(self, text, conf=0.9):
        return [[None], [text, conf]]

    def test_returns_first_meaningful_line(self):
        lines = [
            self._make_line("12"),       # too short
            self._make_line("SHOPRITE SUPERMARKET"),
        ]
        result = poc.extract_vendor_fallback(lines)
        self.assertEqual(result, "SHOPRITE SUPERMARKET")

    def test_returns_unknown_when_no_lines(self):
        result = poc.extract_vendor_fallback([])
        self.assertEqual(result, "Unknown Vendor")


if __name__ == "__main__":
    unittest.main()
