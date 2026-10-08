package com.vegayan.airtelmanagement.activity.controller;

import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelParseResponseDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelRowDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelUploadSummaryDto;
import com.vegayan.airtelmanagement.activity.service.PlanActivityExcelService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


/** The upload template is a static file served by the React app (public/templates). */
@RestController
@RequestMapping("/activity/excel")
public class PlanActivityExcelController {

    private final PlanActivityExcelService planActivityExcelService;

    public PlanActivityExcelController(PlanActivityExcelService planActivityExcelService) {
        this.planActivityExcelService = planActivityExcelService;
    }

    @PostMapping("/v1/parse")
    public ResponseEntity<PlanActivityExcelParseResponseDto> parseExcel(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws Exception {

        Long actorUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(planActivityExcelService.parseAndValidate(actorUserId, file));
    }

    @PostMapping("/v1/upload")
    public ResponseEntity<PlanActivityExcelUploadSummaryDto> uploadExcel(
            @RequestBody List<PlanActivityExcelRowDto> rows,
            Authentication authentication) {

        Long actorUserId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok(planActivityExcelService.insertBatch(actorUserId, rows));
    }
}
