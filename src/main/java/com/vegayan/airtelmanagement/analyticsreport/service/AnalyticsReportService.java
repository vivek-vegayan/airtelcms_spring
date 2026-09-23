package com.vegayan.airtelmanagement.analyticsreport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AnalyticsReportService {

    @Value("${analytics.reports.base-path:D:/CHM_Analytics_Reports}")
    private String analyticsBasePath;

    public List<String> getAvailableDates(String type) {
        File typeDir = new File(analyticsBasePath, type.toLowerCase());

        if (!typeDir.exists() || !typeDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid report type: " + type);
        }

        File[] dirs = typeDir.listFiles(File::isDirectory);
        return Arrays.stream(Objects.requireNonNullElse(dirs, new File[0]))
                .map(File::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getReportsByDate(String type, String date) {
        File dateDir = new File(analyticsBasePath + "/" + type.toLowerCase(), date);

        if (!dateDir.exists() || !dateDir.isDirectory()) {
            throw new IllegalArgumentException("No reports found for date: " + date);
        }

        File[] files = dateDir.listFiles((dir, name) -> name.endsWith(".xlsx"));
        return Arrays.stream(Objects.requireNonNullElse(files, new File[0]))
                .map(File::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    public Resource downloadReport(String type, String date, String fileName) {
        Path filePath = Path.of(analyticsBasePath, type.toLowerCase(), date, fileName);

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found: " + fileName);
        }

        return new FileSystemResource(filePath);
    }
}
