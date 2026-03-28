#!/usr/bin/env node
/**
 * finsight-heal — Runtime Error Mining + LLM Prompt Generator (v2)
 *
 * Improvements:
 *  - Fuzzy grouping: clusters similar errors by root pattern (not just exact type)
 *  - Bank-parsing-focused prompt: includes known date formats, header hints, ICICI context
 *  - Stack trace denoiser: strips Spring/JVM internals, keeps only application frames
 *
 * Usage:
 *   node finsight-heal.js              # Analyze last 5 errors
 *   node finsight-heal.js --n=10       # Analyze last 10 errors
 *   node finsight-heal.js --module=PARSER  # Filter by module
 *   node finsight-heal.js --module=GEMINI  # Filter Gemini failures
 */

const fs = require('fs');
const path = require('path');

// ── Config ──────────────────────────────────────────────────────────────────
const ERROR_LOG_PATH = path.resolve(__dirname, '../finsight-backend/agent/runtime-errors.jsonl');
const SRC_ROOT = path.resolve(__dirname, '../finsight-backend/src/main/java');

const args = process.argv.slice(2).reduce((acc, arg) => {
  const [key, val] = arg.replace(/^--/, '').split('=');
  acc[key] = val || true;
  return acc;
}, {});

const N = parseInt(args.n || '5', 10);
const MODULE_FILTER = args.module ? args.module.toUpperCase() : null;

// ── Read errors ──────────────────────────────────────────────────────────────
function readErrors(logPath, maxN, moduleFilter) {
  if (!fs.existsSync(logPath)) {
    console.error(`\n❌ Error log not found: ${logPath}`);
    console.error('   Make sure the backend has been run at least once.\n');
    process.exit(1);
  }
  const lines = fs.readFileSync(logPath, 'utf-8')
    .split('\n')
    .filter(l => l.trim().length > 0)
    .map(l => { try { return JSON.parse(l); } catch { return null; } })
    .filter(Boolean);

  let filtered = moduleFilter ? lines.filter(e => e.module === moduleFilter) : lines;
  return filtered.slice(-maxN);
}

// ── Fuzzy grouping ────────────────────────────────────────────────────────────
// Groups errors by a "root key" that normalizes similar errors:
// e.g. all DateParseFailure entries regardless of rawCellValue → one group
// e.g. NoSuchFileException regardless of which file → one group
function getRootKey(err) {
  const type = err.errorType || 'Unknown';
  const module = err.module || 'GENERAL';

  // Normalize date parse failures — group all by type, not by specific input
  if (type === 'DateParseFailure') return `${module}::DateParseFailure`;
  // Normalize all null pointer exceptions
  if (type === 'NullPointerException') return `${module}::NullPointerException`;
  // Normalize all file-not-found errors
  if (type.includes('NoSuchFile') || type.includes('FileNotFound')) return `${module}::FileAccessError`;
  // Normalize all Gemini/AI errors
  if (module === 'GEMINI') return `GEMINI::AiExtractionFailure`;
  // Normalize all validation failures
  if (type === 'ValidationFailed') return `${module}::ValidationFailed`;

  return `${module}::${type}`;
}

function groupErrors(errors) {
  const groups = {};
  for (const err of errors) {
    const key = getRootKey(err);
    if (!groups[key]) {
      groups[key] = { ...err, count: 0, rootKey: key, sampleInputs: [] };
    }
    groups[key].count++;
    // Collect up to 3 distinct sample inputs
    if (err.inputData && groups[key].sampleInputs.length < 3) {
      const sig = JSON.stringify(err.inputData);
      if (!groups[key].sampleInputs.some(s => JSON.stringify(s) === sig)) {
        groups[key].sampleInputs.push(err.inputData);
      }
    }
    // Keep the most recent timestamp
    if (!groups[key].latestTimestamp || err.timestamp > groups[key].latestTimestamp) {
      groups[key].latestTimestamp = err.timestamp;
    }
  }
  // Sort by count DESC
  return Object.values(groups).sort((a, b) => b.count - a.count);
}

// ── Stack trace denoiser ──────────────────────────────────────────────────────
// Removes Spring internals, JVM internals, Tomcat frames — keeps only app frames
const NOISE_PATTERNS = [
  /at org\.springframework\./,
  /at org\.apache\.tomcat/,
  /at org\.apache\.catalina/,
  /at org\.hibernate\./,
  /at java\.base\//,
  /at java\.util\.concurrent/,
  /at sun\./,
  /at jdk\./,
  /at org\.antlr\./,
  /at com\.zaxxer\./,
  /^\s*\.\.\. \d+ common frames omitted/,
];

function denoiseStackTrace(rawStack, maxLines = 8) {
  if (!rawStack) return null;
  const lines = rawStack.split('\n');
  const appFrames = lines.filter(line => !NOISE_PATTERNS.some(p => p.test(line)));
  const result = appFrames.slice(0, maxLines);
  const stripped = lines.length - appFrames.length;
  if (stripped > 0) result.push(`  ... [${stripped} framework/JVM frames omitted]`);
  return result.join('\n');
}

// ── Source file finder ────────────────────────────────────────────────────────
const MODULE_FILES = {
  PARSER: ['XlsxStatementParser.java', 'CsvStatementParser.java'],
  GEMINI: ['GeminiStatementParserService.java', 'GeminiClient.java'],
  RECONCILIATION: ['ReconciliationServiceImpl.java'],
  STATEMENT_UPLOAD: ['BankStatementService.java'],
  OCR: [],
  GENERAL: ['GlobalExceptionHandler.java'],
};

function readSourceSnippet(filename, maxLines = 50) {
  try {
    const results = [];
    function walk(dir) {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) walk(full);
        else if (entry.name === filename) results.push(full);
      }
    }
    walk(SRC_ROOT);
    if (!results.length) return `// ${filename} not found in src`;
    const content = fs.readFileSync(results[0], 'utf-8').split('\n');
    // Smart excerpt: try to find the most relevant method
    const snippet = content.slice(0, maxLines).join('\n');
    return `// ${path.relative(SRC_ROOT, results[0])}\n${snippet}`;
  } catch {
    return `// Could not read ${filename}`;
  }
}

// ── Severity classifier ───────────────────────────────────────────────────────
function classify(rootKey, count) {
  if (rootKey.includes('NullPointer') || rootKey.includes('OutOfMemory')) return '🔴 CRITICAL';
  if (count >= 5) return '🔴 HIGH FREQUENCY';
  if (rootKey.includes('DateParseFailure') || rootKey.includes('AiExtractionFailure')) return '🟡 HIGH';
  if (rootKey.includes('FileAccessError')) return '🟠 MEDIUM';
  if (rootKey.includes('ValidationFailed')) return '🟢 LOW';
  return '⚪ INFO';
}

// ── Bank parsing context (domain-specific enrichment) ─────────────────────────
function getBankParsingContext(group) {
  if (group.module !== 'PARSER' && group.module !== 'GEMINI') return '';

  const knownDateFormats = [
    'dd/MM/yyyy', 'MM/dd/yyyy', 'yyyy-MM-dd', 'dd-MM-yyyy',
    'dd/MMM/yyyy', 'd/MMM/yyyy', 'dd MMM yyyy',
    'dd/MM/yyyy HH:mm:ss', 'dd/MM/yyyy hh:mm a',
  ];

  const rawSamples = group.sampleInputs
    .map(inp => inp.rawCellValue || inp.rawDate || inp.cellValue)
    .filter(Boolean);

  let ctx = '\n📋 Bank Parsing Context:\n';
  ctx += `  Known good date formats: ${knownDateFormats.join(', ')}\n`;
  if (rawSamples.length > 0) {
    ctx += `  Failed date inputs: ${rawSamples.map(s => `"${s}"`).join(', ')}\n`;
    ctx += `  Likely bank: ${inferBankFromSamples(rawSamples)}\n`;
  }
  ctx += '  Check: CsvStatementParser.DATE_FORMATS and XlsxStatementParser.parseDateFromCell()\n';
  return ctx;
}

function inferBankFromSamples(samples) {
  for (const s of samples) {
    if (/\d{2}\/[A-Za-z]{3}\/\d{4}/.test(s)) return 'Likely ICICI (dd/MMM/yyyy format)';
    if (/\d{2}-\d{2}-\d{4} \d{2}:\d{2}/.test(s)) return 'Likely HDFC/Axis (dd-MM-yyyy HH:mm)';
    if (/\d{4}-\d{2}-\d{2}T/.test(s)) return 'ISO format (generic)';
  }
  return 'Unknown bank format';
}

// ── Prompt generator ──────────────────────────────────────────────────────────
function generatePrompt(groups) {
  const lines = [];
  lines.push('═══════════════════════════════════════════════════════════════════');
  lines.push('  FINSIGHT BANK STATEMENT PARSER — RUNTIME HEALING PROMPT (v2)    ');
  lines.push('═══════════════════════════════════════════════════════════════════');
  lines.push(`Generated: ${new Date().toISOString()}`);
  lines.push(`Total errors: ${groups.reduce((s, g) => s + g.count, 0)} | Unique patterns: ${groups.length}`);
  lines.push('');
  lines.push('SYSTEM CONTEXT:');
  lines.push('  Project: Finsight — Multi-bank statement parser (XLSX, CSV, PDF)');
  lines.push('  Stack: Java 21, Spring Boot, Apache POI, Gemini AI, PaddleOCR');
  lines.push('  Key parsers: XlsxStatementParser, CsvStatementParser, GeminiStatementParserService');
  lines.push('');

  for (let i = 0; i < groups.length; i++) {
    const g = groups[i];
    const severity = classify(g.rootKey, g.count);

    lines.push('───────────────────────────────────────────────────────────────────');
    lines.push(`PATTERN ${i + 1}/${groups.length}  ${severity}`);
    lines.push(`  Module   : ${g.module}`);
    lines.push(`  Root Key : ${g.rootKey}`);
    lines.push(`  Frequency: ${g.count} occurrences | Last: ${g.latestTimestamp || g.timestamp}`);
    if (g.message) lines.push(`  Message  : ${g.message}`);
    lines.push('');

    // Sample inputs (up to 3, deduplicated)
    if (g.sampleInputs.length > 0) {
      lines.push('  Sample Inputs That Triggered This:');
      g.sampleInputs.forEach((inp, idx) => {
        lines.push(`    [${idx + 1}] ${JSON.stringify(inp)}`);
      });
    }

    // Expected vs actual
    if (g.expected || g.actual) {
      lines.push('');
      if (g.expected) lines.push(`  Expected: ${g.expected}`);
      if (g.actual) lines.push(`  Actual  : ${g.actual}`);
    }

    // Denoised stack trace
    const cleanStack = denoiseStackTrace(g.stackTrace);
    if (cleanStack) {
      lines.push('');
      lines.push('  Stack (app frames only):');
      lines.push(cleanStack.split('\n').map(l => '    ' + l).join('\n'));
    }

    // Bank parsing domain context
    const bankCtx = getBankParsingContext(g);
    if (bankCtx) lines.push(bankCtx);

    // Relevant source code
    const files = MODULE_FILES[g.module] || [];
    if (files.length > 0) {
      lines.push('');
      lines.push('  Relevant Source (first 50 lines):');
      const snippet = readSourceSnippet(files[0]);
      lines.push('  ```java');
      snippet.split('\n').forEach(l => lines.push('  ' + l));
      lines.push('  ```');
    }

    // Task for LLM
    lines.push('');
    lines.push('  ┌─ TASK FOR LLM ──────────────────────────────────────────────┐');
    lines.push(`  │ 1. ROOT CAUSE: Why does "${g.rootKey}" occur?`);
    lines.push('  │    Be specific — reference exact method / line if possible.');
    lines.push('  │');
    lines.push('  │ 2. MINIMAL FIX (code diff only, no explanations):');
    lines.push('  │    ```diff');
    lines.push('  │    - old line');
    lines.push('  │    + new line');
    lines.push('  │    ```');
    lines.push('  │');
    lines.push('  │ 3. RISK: Low / Medium / High — and why.');
    lines.push('  │');
    lines.push(`  │ 4. UNIT TEST (JUnit 5, test class: ${g.module}ParserTest):`);
    lines.push('  │    Cover the exact failing input shown in Sample Inputs above.');
    lines.push('  └─────────────────────────────────────────────────────────────┘');
    lines.push('');
  }

  lines.push('═══════════════════════════════════════════════════════════════════');
  lines.push('END — Paste this into your LLM. DO NOT auto-apply fixes.');
  lines.push('Review diffs carefully. Keep changes minimal & backward-compatible.');
  lines.push('═══════════════════════════════════════════════════════════════════');
  return lines.join('\n');
}

// ── Main ──────────────────────────────────────────────────────────────────────
console.log(`\n🔍 finsight-heal v2: Reading last ${N} errors${MODULE_FILTER ? ` [module=${MODULE_FILTER}]` : ''}...\n`);
const errors = readErrors(ERROR_LOG_PATH, N, MODULE_FILTER);

if (errors.length === 0) {
  console.log('✅ No errors found. System is clean!\n');
  process.exit(0);
}

const groups = groupErrors(errors);
console.log(`📊 ${errors.length} error(s) → ${groups.length} unique pattern(s) after fuzzy grouping.\n`);

const prompt = generatePrompt(groups);
console.log(prompt);

const outFile = path.resolve(__dirname, 'finsight-heal-output.txt');
fs.writeFileSync(outFile, prompt, 'utf-8');
console.log(`\n💾 Saved to: ${outFile}`);
