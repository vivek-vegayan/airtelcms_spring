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

//    @PostMapping("/futureweek/update")
//    public ResponseEntity<ApiResponse> updateFutureWeek(
//            @RequestParam Integer futureId,
//            @RequestParam String colName,
//            @RequestParam String newValue,
//            Authentication authentication
//    ) {
//
//        String actorUserId = authentication.getName();
//
//        ApiResponse response =
//                futureWeekService.updateFutureWeek(
//                        actorUserId,
//                        futureId,
//                        colName,
//                        newValue
//                );
//
//        return ResponseEntity.ok(response);
//    }


//    @PostMapping("/futureweek/update")
//    public ResponseEntity<ApiResponse> updateFutureWeek(
//            @RequestParam Integer futureId,
//            @RequestParam Integer year,
//            @RequestParam Integer week,
//            @RequestParam String W7D1,
//            @RequestParam String W7D2,
//            @RequestParam String W7D3,
//            @RequestParam String W7D4,
//            @RequestParam String W7D5,
//            @RequestParam String W7D6,
//            @RequestParam String W7D7,
//            Authentication authentication
//    ) {
//
//        String actorUserId = authentication.getName();
//
//        ApiResponse response =
//                futureWeekService.updateFutureWeek(
//                        actorUserId,
//                        futureId,
//                        year,
//                        week,
//                        W7D1,
//                        W7D2,
//                        W7D3,
//                        W7D4,
//                        W7D5,
//                        W7D6,
//                        W7D7
//                );
//
//        return ResponseEntity.ok(response);
//    }



}