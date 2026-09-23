package com.vegayan.airtelmanagement.cabmanager.controller;

import com.vegayan.airtelmanagement.cabmanager.service.CabServiceCsvExportService;
import com.vegayan.airtelmanagement.common.config.LogConfig;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export Excel in the CAB All-CRQs drawer.
 *
 * <p>One round trip: run getCSVasperService.py for this CRQ + service and
 * stream the resulting workbook back. Nothing is stored server-side, so there
 * is no generate-then-fetch step for the client to coordinate.
 */
@RestController
@RequestMapping("/cab/crqs")
@RequiredArgsConstructor
public class CabServiceCsvExportController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CabServiceCsvExportController.class);

    private final CabServiceCsvExportService exportService;

    @LogConfig(excludeResponse = true)
    @GetMapping("/service-csv/export")
    public ResponseEntity<?> exportServiceCsv(
            @RequestParam String crqNo,
            @RequestParam String service) {

        try {
            byte[] excel = exportService.exportServiceCsv(crqNo, service);

            String fileName = "Service_Impact_" + crqNo + "_" + service + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excel);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse("Fail", e.getMessage()));

        } catch (CabServiceCsvExportService.EmptyResultException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse("Fail", e.getMessage()));

        } catch (Exception e) {
            LOGGER.error("[CAB SERVICE EXPORT] failed for {} / {}: {}",
                    crqNo, service, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Fail",
                            "Failed to generate the service impact Excel."));
        }
    }
}
