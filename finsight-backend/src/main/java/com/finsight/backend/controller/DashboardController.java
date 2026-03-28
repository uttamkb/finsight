package com.finsight.backend.controller;

import com.finsight.backend.dto.DashboardStatsDto;
import com.finsight.backend.dto.MonthlySummaryDto;
import com.finsight.backend.dto.ProjectionDto;
import com.finsight.backend.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
@Tag(name = "Dashboard", description = "Endpoints for retrieving high-level financial statistics and projections")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get Dashboard Stats", description = "Provides primary summary cards filtered by account type.")
    public ResponseEntity<DashboardStatsDto> getStats(
            @Parameter(description = "Filter by account type") @RequestParam(defaultValue = "MAINTENANCE") String accountType) {
        return ResponseEntity.ok(dashboardService.getStats("local_tenant", accountType));
    }

    @GetMapping("/history")
    @Operation(summary = "Get Monthly History", description = "Retrieves month-by-month financial summary over the last 12 months.")
    public ResponseEntity<List<MonthlySummaryDto>> getHistory(
            @Parameter(description = "Filter by account type") @RequestParam(defaultValue = "MAINTENANCE") String accountType) {
        return ResponseEntity.ok(dashboardService.getMonthlyHistory("local_tenant", 6, accountType));
    }

    @GetMapping("/projections")
    @Operation(summary = "Get Revenue Projections", description = "Provides AI-driven forecast metrics based on historical tenant financial data.")
    public ResponseEntity<List<ProjectionDto>> getProjections() {
        // Keeping projections as is for now, or could filter by account if needed
        return ResponseEntity.ok(List.of()); 
    }
}
