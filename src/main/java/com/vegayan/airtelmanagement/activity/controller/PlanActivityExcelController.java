package com.vegayan.airtelmanagement.activity.controller;

import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelParseResponseDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelRowDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelUploadSummaryDto;
import com.vegayan.airtelmanagement.activity.service.PlanActivityExcelExportService;
import com.vegayan.airtelmanagement.activity.service.PlanActivityExcelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


/** The upload template is a static file served by the React app (public/templates). */
@RestController
@RequestMapping("/activity/excel")
public class PlanActivityExcelController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PlanActivityExcelService planActivityExcelService;
    private final PlanActivityExcelExportService planActivityExcelExportService;

    public PlanActivityExcelController(PlanActivityExcelService planActivityExcelService,
                                       PlanActivityExcelExportService planActivityExcelExportService) {
        this.planActivityExcelService = planActivityExcelService;
        this.planActivityExcelExportService = planActivityExcelExportService;
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

    /** Plans for the Plan View filters, in the upload template layout. */
    @GetMapping("/v1/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false, defaultValue = "0") Long verticalId,
            @RequestParam(required = false, defaultValue = "0") Long functionId,
            @RequestParam(required = false, defaultValue = "0") Long domainId,
            @RequestParam(required = false, defaultValue = "0") Long subDomainId,
            @RequestParam(required = false, defaultValue = "Active") String statusFilter,
            Authentication authentication) throws Exception {

        Long actorUserId = Long.valueOf(authentication.getName());
        byte[] file = planActivityExcelExportService.exportPlanActivities(
                actorUserId, verticalId, functionId, domainId, subDomainId, statusFilter);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Plan_Activity_Export.xlsx")
                .contentType(XLSX)
                .body(file);
    }
}
