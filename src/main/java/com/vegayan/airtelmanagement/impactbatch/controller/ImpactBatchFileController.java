package com.vegayan.airtelmanagement.impactbatch.controller;

import com.vegayan.airtelmanagement.common.config.LogConfig;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.impactbatch.service.ImpactBatchFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.FileNotFoundException;
import java.util.List;




@RestController
@RequestMapping("/excel")
@RequiredArgsConstructor
public class ImpactBatchFileController {
    private final ImpactBatchFileService fileService;

    @LogConfig(excludeResponse = true)
    @PostMapping("/impact-batchwise")
    public ResponseEntity<?> generateImpactBatchExcel(
            @RequestBody List<String> fileNames
    ) {

        try {

            byte[] excel =
                    fileService.generateImpactBatchExcel(fileNames);

            String crqNumber = "UNKNOWN";
            String attemptNumber = "0";

            // Extract CRQ + Attempt
            for (String fileName : fileNames) {

                String cleanName =
                        fileName.replace(".csv", "");

                String[] parts =
                        cleanName.split("_");

                for (String part : parts) {

                    if (part.startsWith("CRQ")) {

                        crqNumber = part;
                    }
                }

                attemptNumber =
                        parts[parts.length - 1];

                if (!crqNumber.equals("UNKNOWN")) {
                    break;
                }
            }


            // Excel File Name

            String excelFileName =
                    "Impact_Data_"
                            + crqNumber
                            + "_Batch_"
                            + attemptNumber
                            + ".xlsx";

            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename="
                                    + excelFileName
                    )
                    .contentType(
                            MediaType.APPLICATION_OCTET_STREAM
                    )
                    .body(excel);

        } catch (FileNotFoundException e) {

            ApiResponse response =
                    new ApiResponse(
                            "Fail",
                            "No CSV files were found for the selected files."
                    );

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);

        } catch (Exception e) {

            ApiResponse response =
                    new ApiResponse(
                            "Fail",
                            "Failed to generate impact batch Excel."
                    );

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(response);
        }
    }
}
