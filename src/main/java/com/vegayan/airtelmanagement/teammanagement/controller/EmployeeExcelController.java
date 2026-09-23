package com.vegayan.airtelmanagement.teammanagement.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.teammanagement.dto.EmployeeExcelRowDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelRowResultDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUploadProgressDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUploadResultResponseDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUploadStartResponseDto;
import com.vegayan.airtelmanagement.teammanagement.service.EmployeeExcelService;
import com.vegayan.airtelmanagement.teammanagement.service.ExcelUploadService;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

@RestController
@RequestMapping("/teamoverview/excel")
public class EmployeeExcelController {

    private final EmployeeExcelService employeeExcelService;
    private final ExcelUploadService excelUploadService;

    public EmployeeExcelController(EmployeeExcelService employeeExcelService, ExcelUploadService excelUploadService) {
        this.employeeExcelService = employeeExcelService;
        this.excelUploadService = excelUploadService;
    }


    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_EMPLOYEE_UPLOAD,
               action = AuditAction.DOWNLOAD,
               remark = "Downloaded the employee upload template")
    @GetMapping("/v1/template")
    public ResponseEntity<byte[]> downloadEmployeeTemplate(
            Authentication authentication) throws Exception {

        Long userId = Long.parseLong(authentication.getName());

        Workbook workbook = employeeExcelService.generateEmployeeTemplate(userId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return ResponseEntity.ok()
                             .header(HttpHeaders.CONTENT_DISPOSITION,
                                     "attachment; filename=Employee_Upload_Template.xlsx")
                             .contentType(MediaType.APPLICATION_OCTET_STREAM)
                             .body(out.toByteArray());
    }

    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_EMPLOYEE_UPLOAD,
               action = AuditAction.UPLOAD,
               remark = "Uploaded an employee spreadsheet (synchronous)")
    @PostMapping("/v1/upload")
    public ResponseEntity<List<ExcelRowResultDto>> uploadEmployeeExcel(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws Exception {

        Long actorUserId = Long.parseLong(authentication.getName());
        List<ExcelRowResultDto> result = excelUploadService.processSynchronously(actorUserId, file);
        return ResponseEntity.ok(result);
    }

    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_EMPLOYEE_UPLOAD,
               action = AuditAction.UPLOAD,
               remark = "Submitted pre-parsed employee rows for batch creation")
    @PostMapping("/v1/batch")
    public ResponseEntity<List<ExcelRowResultDto>> batchUploadEmployees(
            @RequestBody List<EmployeeExcelRowDto> rows,
            Authentication authentication) {

        Long actorUserId = Long.parseLong(authentication.getName());
        List<ExcelRowResultDto> result = excelUploadService.processSynchronously(actorUserId, rows);
        return ResponseEntity.ok(result);
    }

    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_EMPLOYEE_UPLOAD,
               action = AuditAction.UPLOAD,
               remark = "Uploaded an employee spreadsheet for background processing")
    @PostMapping("/v1/upload/async")
    public ResponseEntity<ExcelUploadStartResponseDto> uploadEmployeeExcelAsync(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws Exception {

        Long actorUserId = Long.parseLong(authentication.getName());
        ExcelUploadStartResponseDto response = excelUploadService.startAsyncUpload(actorUserId, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/v1/upload/{uploadId}/status")
    public ResponseEntity<ExcelUploadProgressDto> getUploadStatus(@PathVariable String uploadId) {
        return ResponseEntity.ok(excelUploadService.getStatus(uploadId));
    }

    @GetMapping("/v1/upload/{uploadId}/result")
    public ResponseEntity<ExcelUploadResultResponseDto> getUploadResult(@PathVariable String uploadId) {
        return ResponseEntity.ok(excelUploadService.getResult(uploadId));
    }

    @GetMapping("/v1/upload/{uploadId}/error-report")
    public ResponseEntity<byte[]> downloadUploadErrorReport(@PathVariable String uploadId) throws Exception {
        byte[] report = excelUploadService.getErrorReportBytes(uploadId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Excel_Upload_Error_Report_" + uploadId + ".xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(report);
    }
}
