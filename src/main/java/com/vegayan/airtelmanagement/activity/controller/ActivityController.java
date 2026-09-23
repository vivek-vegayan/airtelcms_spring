package com.vegayan.airtelmanagement.activity.controller;

import com.vegayan.airtelmanagement.activity.dto.ActivityDTO;
import com.vegayan.airtelmanagement.activity.dto.ActivityInsertRequestDTO;
import com.vegayan.airtelmanagement.activity.dto.ActivityPhaseViewDTO;
import com.vegayan.airtelmanagement.activity.dto.UpdateActivityPhaseDto;
import com.vegayan.airtelmanagement.activity.dto.UpdatePlanDto;
import com.vegayan.airtelmanagement.activity.service.ActivityService;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.usermanagement.dto.InsertPlanDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/view")
    public List<ActivityDTO> getActivityDetails(
            Authentication authentication,
            @RequestParam Integer subDomainID
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        return activityService.getActivityDetails(actorUserId, subDomainID);
    }

    @GetMapping("/phase-view")
    public ResponseEntity<ActivityPhaseViewDTO> getPhaseView(
            Authentication authentication,
            @RequestParam Long planId
    ) {

        Long userId = Long.valueOf(authentication.getName());
        ActivityPhaseViewDTO response =
                activityService.getActivityPhaseView(userId, planId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/insert")
    public ResponseEntity<ApiResponse> insertActivity(
            Authentication authentication,
            @Valid @RequestBody ActivityInsertRequestDTO request
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                activityService.insertActivity(actorUserId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/phase-update")
    public ResponseEntity<ApiResponse> updateActivityPhase(
            Authentication authentication,
            @Valid @RequestBody UpdateActivityPhaseDto request
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                activityService.updateActivityPhase(actorUserId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/updateplan")
    public ResponseEntity<ApiResponse> updatePlan(
            Authentication authentication,
            @Valid @RequestBody UpdatePlanDto request) {

        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = activityService.updatePlan(actorUserId,request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }


    @PostMapping("/insertPlan")
    public ResponseEntity<ApiResponse> insertPlan(
            Authentication authentication,
            @Valid @RequestBody InsertPlanDto request) {

        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = activityService.insertPlan(actorUserId,request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PatchMapping("/plan/{planId}/status")
    public ResponseEntity<ApiResponse> changePlanStatus(
            Authentication authentication,
            @PathVariable Integer planId,
            @RequestParam String status) {

        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = activityService.changePlanStatus(actorUserId, planId, status);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{activityId}/status")
    public ResponseEntity<ApiResponse> changeActivityStatus(
            Authentication authentication,
            @PathVariable String activityId,
            @RequestParam Integer planId,
            @RequestParam String status) {

        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = activityService.changeActivityStatus(actorUserId, planId, activityId, status);
        return ResponseEntity.ok(response);
    }

}