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

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }

    @GetMapping("/history")
    public ResponseEntity<List<MonthlySummaryDto>> getHistory() {
        return ResponseEntity.ok(dashboardService.getMonthlyHistory());
    }

    @GetMapping("/projections")
    public ResponseEntity<List<ProjectionDto>> getProjections() {
        return ResponseEntity.ok(dashboardService.getProjections());
    }
}
