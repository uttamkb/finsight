package com.finsight.backend.service.reconciliation;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class DateMatchingService {

    public MatchResult matchDate(LocalDate txDate, LocalDate receiptDate) {
        if (txDate == null || receiptDate == null) {
            return new MatchResult(0, "Missing date");
        }

        long days = Math.abs(ChronoUnit.DAYS.between(txDate, receiptDate));
        double score = 0;
        String reasoning = "";

        if (days == 0) {
            score = 5;
            reasoning = "Exact date match";
        } else if (days <= 5) {
            score = 3;
            reasoning = "Date within 5 days (" + days + " days apart)";
        } else {
            reasoning = "Date mismatch (>" + days + " days apart)";
        }

        return new MatchResult(score, reasoning);
    }
}
