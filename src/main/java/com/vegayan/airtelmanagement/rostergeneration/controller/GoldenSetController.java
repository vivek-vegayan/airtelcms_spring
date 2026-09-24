package com.vegayan.airtelmanagement.rostergeneration.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.rostergeneration.dto.DailyGoldenSetRequestDto;
import com.vegayan.airtelmanagement.rostergeneration.dto.GoldenSetResponseDto;
import com.vegayan.airtelmanagement.rostergeneration.service.GoldenSetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/goldenset")
public class GoldenSetController {

    private final GoldenSetService goldenSetService;

    public GoldenSetController(
            GoldenSetService goldenSetService
    ) {
        this.goldenSetService = goldenSetService;
    }

    @GetMapping
    public GoldenSetResponseDto getGoldenSet(
            Authentication authentication,
            @RequestParam(required = false) Long domainId,
            @RequestParam(required = false) Long subDomainId
    ) {

        String actorUserId = authentication.getName();

        // Proc cascades: sub domain -> domain -> the actor's own domain.
        return goldenSetService.getGoldenSet(
                actorUserId,
                domainId,
                subDomainId
        );
    }

    @PostMapping("/dailygoldenset")
    public ResponseEntity<ApiResponse> insertDailyGoldenSet(
            @RequestBody List<DailyGoldenSetRequestDto> requests,
            Authentication authentication
    ) {

        String actorUserId = authentication.getName();

        ApiResponse response =
                goldenSetService.insertDailyGoldenSet(
                        actorUserId,
                        requests
                );

        return ResponseEntity.ok(response);
    }
}