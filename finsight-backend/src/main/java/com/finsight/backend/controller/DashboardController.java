package com.finsight.backend.controller;

import com.finsight.backend.dto.DashboardStatsDto;
import com.finsight.backend.dto.MonthlySummaryDto;
import com.finsight.backend.dto.ProjectionDto;
import com.finsight.backend.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Get Dashboard Stats", description = "Provides primary summary cards (Total Revenue, Cash Flow, Pending Invoices, etc.) for the main dashboard view.")
    public ResponseEntity<DashboardStatsDto> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }

    @GetMapping("/history")
    @Operation(summary = "Get Monthly History", description = "Retrieves month-by-month financial summary over the last 12 months for chart rendering.")
    public ResponseEntity<List<MonthlySummaryDto>> getHistory() {
        return ResponseEntity.ok(dashboardService.getMonthlyHistory());
    }

    @GetMapping("/projections")
    @Operation(summary = "Get Revenue Projections", description = "Provides AI-driven forecast metrics based on historical tenant financial data.")
    public ResponseEntity<List<ProjectionDto>> getProjections() {
        return ResponseEntity.ok(dashboardService.getProjections());
    }
}
