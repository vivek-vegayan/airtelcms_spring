package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExcelUploadStartResponseDto {
    private String uploadId;
    private String status;
    private String message;
    private String fileName;
    private long fileSizeBytes;
    private int totalRows;
}
