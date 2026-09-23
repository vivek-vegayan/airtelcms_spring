package com.vegayan.airtelmanagement.rostergeneration.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.rostergeneration.dto.FutureWeekResponseDto;
import com.vegayan.airtelmanagement.rostergeneration.dto.FutureWeekUpdateRequest;
import com.vegayan.airtelmanagement.rostergeneration.service.FutureWeekService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rostergenration")
public class FutureWeekController {

    private final FutureWeekService futureWeekService;

    public FutureWeekController(
            FutureWeekService futureWeekService
    ) {
        this.futureWeekService = futureWeekService;
    }

    @GetMapping("/futureweek")
    public FutureWeekResponseDto getFutureWeek(
            Authentication authentication,
            @RequestParam Long subDomainId,
            @RequestParam(defaultValue = "1")  int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        String actorUserId = authentication.getName();
        return futureWeekService.getFutureWeek(
                actorUserId,
                subDomainId,
                pageNumber,
                pageSize
        );
    }

    @PostMapping("/futureweek/update")
    public ResponseEntity<ApiResponse> updateFutureWeek(
            @RequestBody List<FutureWeekUpdateRequest> requests,
            Authentication authentication
    ) {

        String actorUserId = authentication.getName();

        ApiResponse response =
                futureWeekService.updateFutureWeekBatch(
                        actorUserId,
                        requests
                );

        return ResponseEntity.ok(response);
    }

}