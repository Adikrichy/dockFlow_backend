package org.aldousdev.dockflowbackend.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.dashboard.dto.response.DashboardActivityResponse;
import org.aldousdev.dockflowbackend.dashboard.dto.response.DashboardStatsResponse;
import org.aldousdev.dockflowbackend.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard statistics and activity API")
public class DashboardController {
    private final DashboardService dashboardService;
    private final AuthServiceImpl authService;
    private final JWTService jwtService;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        Long companyId = getCompanyIdFromContext();
        DashboardStatsResponse stats = dashboardService.getStats(companyId, authService.getCurrentUser());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/activities")
    @Operation(summary = "Get recent activities")
    public ResponseEntity<List<DashboardActivityResponse>> getActivities() {
        Long companyId = getCompanyIdFromContext();
        List<DashboardActivityResponse> activities = dashboardService.getRecentActivities(companyId);
        return ResponseEntity.ok(activities);
    }

    private Long getCompanyIdFromContext() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtService.extractCompanyId(jwtAuth.getToken());
        }
        throw new RuntimeException("No company context found");
    }
}
