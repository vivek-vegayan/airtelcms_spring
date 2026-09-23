package com.vegayan.airtelmanagement.analyticsreport.controller;

import com.vegayan.airtelmanagement.analyticsreport.service.AnalyticsReportService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics/reports")
public class AnalyticsReportController {

    private final AnalyticsReportService analyticsReportService;

    public AnalyticsReportController(AnalyticsReportService analyticsReportService) {
        this.analyticsReportService = analyticsReportService;
    }

    @GetMapping("/{type}")
    public ResponseEntity<?> getDates(@PathVariable String type) {
        try {
            List<String> dates = analyticsReportService.getAvailableDates(type);
            return ResponseEntity.ok(Map.of("availableDates", dates));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{type}/{date}")
    public ResponseEntity<?> getReportsByDate(@PathVariable String type, @PathVariable String date) {
        try {
            List<String> reports = analyticsReportService.getReportsByDate(type, date);
            return ResponseEntity.ok(Map.of("availableReports", reports));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{type}/{date}/download/{fileName}")
    public ResponseEntity<Resource> downloadReport(
            @PathVariable String type,
            @PathVariable String date,
            @PathVariable String fileName) throws Exception {

        Resource resource = analyticsReportService.downloadReport(type, date, fileName);

        String contentType = Files.probeContentType(Path.of(resource.getFile().getAbsolutePath()));
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
