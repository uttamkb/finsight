package com.finsight.backend.service;

import com.finsight.backend.entity.ParserPattern;
import com.finsight.backend.repository.ParserPatternRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ParserPatternService {

    private static final Logger log = LoggerFactory.getLogger(ParserPatternService.class);

    private final ParserPatternRepository repository;
    private final ObjectMapper objectMapper;

    public ParserPatternService(ParserPatternRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a normalized signature from a list of header strings.
     */
    public String generateSignature(List<String> headers) {
        if (headers == null) return "EMPTY";
        return headers.stream()
                .map(h -> h.trim().toLowerCase().replaceAll("[^a-z]", ""))
                .filter(h -> !h.isEmpty())
                .collect(Collectors.joining("|"));
    }

    /**
     * Finds the best matching pattern using exact or fuzzy matching (similarity > 85%).
     */
    public Optional<ParserPattern> findMatchingPattern(String tenantId, String currentSignature) {
        // 1. Precise Match
        Optional<ParserPattern> exact = repository.findBySignatureAndTenantId(currentSignature, tenantId);
        if (exact.isPresent()) return exact;

        // 2. Fuzzy Clustering Match
        List<ParserPattern> existing = repository.findByTenantId(tenantId);
        for (ParserPattern p : existing) {
            double sim = calculateSimilarity(p.getSignature(), currentSignature);
            if (sim >= 0.85) {
                log.info("Fuzzy match found: {} with similarity {}", p.getPatternGroupId(), sim);
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * Saves or updates a pattern.
     */
    public ParserPattern savePattern(String tenantId, String signature, Map<String, Integer> mapping, 
                                    double confidence, String format, int headerIdx) {
        try {
            String jsonMapping = objectMapper.writeValueAsString(mapping);
            
            // Check for existing to avoid duplicates (Idempotency)
            Optional<ParserPattern> existing = repository.findBySignatureAndTenantId(signature, tenantId);
            
            ParserPattern pattern;
            if (existing.isPresent()) {
                pattern = existing.get();
                pattern.setUsageCount(pattern.getUsageCount() + 1);
                pattern.setLastUsedAt(LocalDateTime.now());
                pattern.setConfidenceScore((pattern.getConfidenceScore() + confidence) / 2.0); // Simple rolling average
            } else {
                pattern = ParserPattern.builder()
                        .tenantId(tenantId)
                        .signature(signature)
                        .patternGroupId(UUID.randomUUID().toString())
                        .columnMapping(jsonMapping)
                        .confidenceScore(confidence)
                        .usageCount(1)
                        .lastUsedAt(LocalDateTime.now())
                        .sourceFormat(format)
                        .headerRowIndex(headerIdx)
                        .build();
                
                // Grouping Logic: find similar group
                findSimilarGroup(tenantId, signature).ifPresent(pattern::setPatternGroupId);
            }
            
            return repository.save(pattern);
        } catch (Exception e) {
            log.error("Failed to save pattern", e);
            return null;
        }
    }

    private Optional<String> findSimilarGroup(String tenantId, String signature) {
        List<ParserPattern> existing = repository.findByTenantId(tenantId);
        return existing.stream()
                .filter(p -> calculateSimilarity(p.getSignature(), signature) >= 0.85)
                .map(ParserPattern::getPatternGroupId)
                .findFirst();
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) costs[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), 
                                  s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }

    /**
     * Periodically optimize the pattern store.
     * 1. Promote consistent patterns.
     * 2. Deprecate low-confidence idle patterns.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 3 * * *") // 3 AM
    public void optimizePatterns() {
        log.info("Starting Pattern Store Optimization...");
        List<ParserPattern> all = repository.findAll();
        
        for (ParserPattern p : all) {
            // High stability promotion
            if (p.getUsageCount() > 100 && p.getConfidenceScore() > 0.9) {
                log.info("Pattern {} is highly stable. Usage: {}", p.getPatternGroupId(), p.getUsageCount());
            }
            
            // Clean up stale, low-confidence patterns
            if (p.getUsageCount() < 2 && p.getConfidenceScore() < 0.5) {
                if (p.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusDays(30))) {
                    log.info("Removing stale pattern: {}", p.getSignature());
                    repository.delete(p);
                }
            }
        }
        
        mergeSimilarGroups();
    }

    public void mergeSimilarGroups() {
        log.info("Checking for pattern groups to merge...");
        List<ParserPattern> all = repository.findAll();
        
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                ParserPattern p1 = all.get(i);
                ParserPattern p2 = all.get(j);
                
                if (p1.getPatternGroupId().equals(p2.getPatternGroupId())) continue;
                
                double similarity = calculateSimilarity(p1.getSignature(), p2.getSignature());
                if (similarity >= 0.95) {
                    log.info("Merging pattern groups: {} and {} (Similarity: {})", 
                        p1.getPatternGroupId(), p2.getPatternGroupId(), similarity);
                    
                    p2.setPatternGroupId(p1.getPatternGroupId());
                    repository.save(p2);
                }
            }
        }
    }
}
