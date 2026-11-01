package com.spendwise.controller;

import com.spendwise.dto.ApiResponse;
import com.spendwise.model.DashboardSummary;
import com.spendwise.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> getDashboardSummary(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @RequestParam(required = false, defaultValue = "ALL") String period) {
        DashboardSummary summary = dashboardService.getDashboardSummary(guestIdentifier, period);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
