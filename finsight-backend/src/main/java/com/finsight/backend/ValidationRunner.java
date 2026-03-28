package com.finsight.backend;

import com.finsight.backend.service.XlsxStatementParser;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.List;

@Component
public class ValidationRunner implements CommandLineRunner {
    private final XlsxStatementParser xlsxParser;

    public ValidationRunner(XlsxStatementParser xlsxParser) {
        this.xlsxParser = xlsxParser;
    }

    @Override
    public void run(String... args) throws Exception {
        File file = new File("/Users/uttamkumar_barik/Documents/Antigravity/java/finsight-backend/app-data/uploads/local_tenant/25f21696-ad19-42fc-8b35-ffc712e11561.xlsx");
        if (!file.exists()) {
            System.err.println("Validation file not found: " + file.getAbsolutePath());
            return;
        }

        System.out.println("--- VALIDATION START ---");
        try {
            List<ParsedBankTransactionDto> result = xlsxParser.parse(file, "local_tenant");
            System.out.println("Extracted Rows: " + result.size());
            if (result.isEmpty()) {
                throw new RuntimeException("Validation Failed: 0 rows extracted.");
            }
            
            System.out.println("Sample rows:");
            result.stream().limit(5).forEach(System.out::println);
            System.out.println("--- VALIDATION SUCCESS ---");
        } catch (Exception e) {
            System.err.println("--- VALIDATION FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
