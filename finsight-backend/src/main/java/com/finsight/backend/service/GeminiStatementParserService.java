package com.finsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finsight.backend.dto.GeminiBankStatementResponse;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.AppConfig;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Hybrid PDF statement parser.
 *
 * <p>Detection strategy:
 * <ul>
 *   <li><b>Digital PDF</b> (text-based): PDFTextStripper extracts raw text → sent to Gemini as
 *       plain text. Fast, tiny payloads, works for 90%+ of bank statements.</li>
 *   <li><b>Scanned PDF</b> (image-based): each page rendered to PNG via PDFRenderer → sent to
 *       Gemini as inline_data image. Handles any scan quality.</li>
 * </ul>
 */
@Service
public class GeminiStatementParserService {

    private static final Logger log = LoggerFactory.getLogger(GeminiStatementParserService.class);

    // Ensure AWT runs headless (required for PDFRenderer on server)
    static {
        System.setProperty("java.awt.headless", "true");
    }

    /** Minimum avg chars/page to classify a PDF as digital (not scanned). */
    private static final int DIGITAL_CHARS_PER_PAGE_THRESHOLD = 100;

    /** Characters per text chunk sent to Gemini for digital PDFs. */
    private static final int TEXT_CHUNK_SIZE = 6000;

    /** DPI for rendering scanned PDF pages to images. 150 DPI ≈ 200-500 KB/page PNG. */
    private static final int RENDER_DPI = 150;

    /** Pages per chunk for the scanned (binary PDF) path. */
    private static final int PAGES_PER_CHUNK = 2;

    /** Max concurrent Gemini API calls — free tier supports 15 req/min. */
    private static final int MAX_CONCURRENT_GEMINI_CALLS = 5;

    private final AppConfigService appConfigService;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    // ── Prompts ─────────────────────────────────────────────────────────────

    private static final String PROMPT_TEXT = """
        You are a highly accurate financial data extraction assistant.
        The following is plain text extracted from a digital bank statement PDF.
        Extract every transaction line item.

        EXTRACTION RULES:
        1. If Withdrawal (Dr) column has a value → type = "DEBIT".
        2. If Deposit (Cr) column has a value → type = "CREDIT".
        3. Remove commas and currency symbols from numeric values.
        4. Convert dates to ISO format: YYYY-MM-DD.
        5. Ignore headers, totals, opening/closing balance rows, and non-transaction rows.

        COUNTERPARTY RULES:
        1. Extract clean merchant/person name from the Remarks/Description column.
        2. Remove prefixes like UPI, IMPS, NEFT, POS, CARD and bank names.

        Return ONLY a JSON object matching this schema — no markdown, no extra text:
        {
          "transactions": [
            {
              "txDate": "YYYY-MM-DD",
              "description": "original narration",
              "vendor": "cleaned merchant name",
              "type": "DEBIT|CREDIT",
              "amount": 0.00,
              "category": ""
            }
          ]
        }
        """;

    private static final String PROMPT_IMAGE = """
        You are a highly accurate financial data extraction assistant.
        I am providing you with a rendered page image from a bank statement.
        Extract every transaction visible in this image.

        EXTRACTION RULES:
        1. If Withdrawal (Dr) column has a value → type = "DEBIT".
        2. If Deposit (Cr) column has a value → type = "CREDIT".
        3. Remove commas and currency symbols from numeric values.
        4. Convert dates to ISO format: YYYY-MM-DD.
        5. Ignore headers, totals, opening/closing balance rows, and non-transaction rows.

        COUNTERPARTY RULES:
        1. Extract clean merchant/person name from the Remarks/Description column.
        2. Remove prefixes like UPI, IMPS, NEFT, POS, CARD and bank names.

        Return ONLY a JSON object matching this schema — no markdown, no extra text:
        {
          "transactions": [
            {
              "txDate": "YYYY-MM-DD",
              "description": "original narration",
              "vendor": "cleaned merchant name",
              "type": "DEBIT|CREDIT",
              "amount": 0.00,
              "category": ""
            }
          ]
        }
        """;

    // ── Constructor ──────────────────────────────────────────────────────────

    public GeminiStatementParserService(AppConfigService appConfigService, GeminiClient geminiClient) {
        this.appConfigService = appConfigService;
        this.geminiClient = geminiClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── Public entry points ──────────────────────────────────────────────────

    public List<ParsedBankTransactionDto> parsePdfStatement(File file) throws Exception {
        AppConfig config = appConfigService.getConfig();
        String apiKey = config.getGeminiApiKey();
        String mode = config.getOcrMode();
        log.info("Starting PDF Statement Parse. File: {}, Mode: {}", file.getName(), mode);

        try {
            if ("MODE_HIGH_ACCURACY".equals(mode)) {
                return extractWithGemini(file, apiKey);
            }

            // Local extraction attempt (MODE_LOW_COST or MODE_HYBRID)
            try {
                GeminiBankStatementResponse localResult = extractLocally(file);
                double confidence = localResult.getConfidenceScore();

                if ("MODE_LOW_COST".equals(mode)) {
                    log.info("Low Cost Mode: Using local results regardless of confidence.");
                    return localResult.getTransactions() != null ? localResult.getTransactions() : new ArrayList<>();
                }

                // Hybrid: fall back to Gemini if confidence is low or no transactions found
                if (confidence >= 70.0 && localResult.getTransactions() != null && !localResult.getTransactions().isEmpty()) {
                    log.info("Hybrid Mode: Local extraction successful with {}% confidence ({} txns).",
                            confidence, localResult.getTransactions().size());
                    return localResult.getTransactions();
                }
                log.info("Confidence ({}%) < 70% or 0 transactions. Falling back to Gemini AI Parser...", confidence);

            } catch (Exception e) {
                log.info("Hybrid Mode: Local extraction failed. Falling back to Gemini...");
                if ("MODE_LOW_COST".equals(mode)) throw e;
            }

            return extractWithGemini(file, apiKey);

        } catch (Exception e) {
            log.error("Error during statement extraction: {}", e.getMessage(), e);
            throw new IOException(e);
        }
    }

    GeminiBankStatementResponse extractLocally(File file) throws Exception {
        String scriptPath = System.getenv("PDF_PARSE_SCRIPT_PATH") != null
                ? System.getenv("PDF_PARSE_SCRIPT_PATH")
                : "src/main/resources/scripts/parse_statement.py";
        String pythonPath = System.getenv("PYTHON_EXECUTABLE") != null
                ? System.getenv("PYTHON_EXECUTABLE")
                : "src/main/resources/scripts/venv/bin/python3";

        log.info("Attempting local extraction for {} using Python script...", file.getName());
        ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath, file.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
        }

        boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Local PDF parse script timed out after 120s.");
        }

        try {
            return objectMapper.readValue(output.toString(), GeminiBankStatementResponse.class);
        } catch (Exception e) {
            log.warn("Failed to parse output from local python script: {}", e.getMessage(), e);
            throw new RuntimeException("JSON parsing error from local script.", e);
        }
    }

    // ── Hybrid Gemini extraction ─────────────────────────────────────────────

    /**
     * Detects whether the PDF is digital or scanned, then routes to the appropriate path.
     */
    List<ParsedBankTransactionDto> extractWithGemini(File file, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your_gemini_api_key_here")) {
            throw new IllegalStateException("Gemini API Key is missing. Cannot perform AI extraction.");
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            int pageCount = document.getNumberOfPages();
            boolean isDigital = isDigitalPdf(document);

            log.info("PDF type detection: {} ({} pages) → {}",
                    file.getName(), pageCount, isDigital ? "DIGITAL (text)" : "SCANNED (image)");

            if (isDigital) {
                return extractFromDigitalPdf(document, apiKey);
            } else {
                return extractFromScannedPdf(document, apiKey);
            }
        }
    }

    // ── Detection ────────────────────────────────────────────────────────────

    /**
     * Samples the first 3 pages of the PDF using PDFTextStripper.
     * If average chars/page > threshold → digital; otherwise → scanned.
     */
    private boolean isDigitalPdf(PDDocument document) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            int samplePages = Math.min(3, document.getNumberOfPages());
            stripper.setStartPage(1);
            stripper.setEndPage(samplePages);
            String text = stripper.getText(document);
            double avgCharsPerPage = (double) text.trim().length() / samplePages;
            log.debug("PDF text sample: {:.0f} avg chars/page (threshold={})", avgCharsPerPage, DIGITAL_CHARS_PER_PAGE_THRESHOLD);
            return avgCharsPerPage > DIGITAL_CHARS_PER_PAGE_THRESHOLD;
        } catch (Exception e) {
            log.warn("PDF type detection failed, defaulting to scanned path: {}", e.getMessage());
            return false;
        }
    }

    // ── Digital path: text extraction ────────────────────────────────────────

    /**
     * Extracts full text from the PDF, chunks it, and sends each chunk to Gemini as plain text.
     * Payloads are tiny (~5KB each), making this very fast and cheap.
     */
    private List<ParsedBankTransactionDto> extractFromDigitalPdf(PDDocument document, String apiKey) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        String fullText = stripper.getText(document);

        List<String> textChunks = splitTextIntoChunks(fullText, TEXT_CHUNK_SIZE);
        log.info("Digital PDF: extracted {} chars → {} text chunks.", fullText.length(), textChunks.size());

        return dispatchChunksToGemini(textChunks.stream()
                .map(chunk -> (java.util.function.Supplier<List<ParsedBankTransactionDto>>) () -> {
                    try {
                        return geminiClient.callGeminiWithText(chunk, PROMPT_TEXT, apiKey);
                    } catch (Exception e) {
                        log.error("Text chunk failed after retries: {}", e.getMessage(), e);
                        return new ArrayList<>();
                    }
                })
                .collect(Collectors.toList()));
    }

    /**
     * Splits text into chunks of at most {@code maxChars}, preferring to break at newline
     * boundaries to avoid cutting a transaction row in half.
     */
    private List<String> splitTextIntoChunks(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (current.length() + line.length() + 1 > maxChars && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    // ── Scanned path: image rendering ────────────────────────────────────────

    /**
     * Renders each page of the PDF to a PNG image at 150 DPI and sends each image to Gemini.
     * Works for any scan quality. Typical payload: ~300-800 KB per page.
     */
    private List<ParsedBankTransactionDto> extractFromScannedPdf(PDDocument document, String apiKey) throws Exception {
        PDFRenderer renderer = new PDFRenderer(document);
        int pageCount = document.getNumberOfPages();
        List<byte[]> pageImages = new ArrayList<>();

        log.info("Scanned PDF: rendering {} pages at {} DPI...", pageCount, RENDER_DPI);
        for (int i = 0; i < pageCount; i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imgBytes = baos.toByteArray();
            log.debug("  Page {} rendered: {} KB", i + 1, imgBytes.length / 1024);
            pageImages.add(imgBytes);
        }

        log.info("Scanned PDF: dispatching {} pages to Gemini.", pageImages.size());
        return dispatchChunksToGemini(pageImages.stream()
                .map(imgBytes -> (java.util.function.Supplier<List<ParsedBankTransactionDto>>) () -> {
                    try {
                        return geminiClient.callGeminiWithImage(imgBytes, PROMPT_IMAGE, apiKey);
                    } catch (Exception e) {
                        log.error("Image page failed after retries: {}", e.getMessage(), e);
                        return new ArrayList<>();
                    }
                })
                .collect(Collectors.toList()));
    }

    // ── Rate-limited parallel dispatch ───────────────────────────────────────

    private List<ParsedBankTransactionDto> dispatchChunksToGemini(
            List<java.util.function.Supplier<List<ParsedBankTransactionDto>>> tasks) throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(MAX_CONCURRENT_GEMINI_CALLS);
        List<CompletableFuture<List<ParsedBankTransactionDto>>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(task, pool))
                .collect(Collectors.toList());

        try {
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                            .flatMap(f -> f.join().stream())
                            .collect(Collectors.toList()))
                    .get();
        } finally {
            pool.shutdown();
        }
    }

    // kept for test compatibility — delegates to geminiClient
    protected List<ParsedBankTransactionDto> callGeminiForChunk(byte[] pdfBytes, String apiKey) throws Exception {
        return geminiClient.callGeminiWithImage(pdfBytes, PROMPT_IMAGE, apiKey);
    }
}
