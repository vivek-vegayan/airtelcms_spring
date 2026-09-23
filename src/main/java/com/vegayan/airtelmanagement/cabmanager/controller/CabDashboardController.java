package com.vegayan.airtelmanagement.cabmanager.controller;

import com.vegayan.airtelmanagement.cabmanager.dto.DashboardDataDto;
import com.vegayan.airtelmanagement.cabmanager.service.CabDashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cab/dashboard")
public class CabDashboardController {

    private final CabDashboardService cabDashboardService;

    public CabDashboardController(CabDashboardService cabDashboardService) {
        this.cabDashboardService = cabDashboardService;
    }

    @GetMapping
    public DashboardDataDto getDashboard(Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return cabDashboardService.getDashboard(actorUserId);
    }
}
