package com.vegayan.airtelmanagement.activity.controller;

import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelParseResponseDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelRowDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelUploadSummaryDto;
import com.vegayan.airtelmanagement.activity.service.PlanActivityExcelService;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;


@RestController
@RequestMapping("/activity/excel")
public class PlanActivityExcelController {

    private final PlanActivityExcelService planActivityExcelService;

    public PlanActivityExcelController(PlanActivityExcelService planActivityExcelService) {
        this.planActivityExcelService = planActivityExcelService;
    }

    @GetMapping("/v1/template")
    public ResponseEntity<byte[]> downloadTemplate(Authentication authentication) throws Exception {
        Long actorUserId = Long.valueOf(authentication.getName());

        Workbook workbook = planActivityExcelService.generateTemplate(actorUserId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Plan_Activity_Upload_Template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(out.toByteArray());
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
