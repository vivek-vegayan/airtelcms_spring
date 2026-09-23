package com.vegayan.airtelmanagement.impactbatch.controller;

import com.vegayan.airtelmanagement.impactbatch.dto.BatchStatusResponseDTO;
import com.vegayan.airtelmanagement.impactbatch.service.ImpactFileReaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/impact")
@CrossOrigin(origins = "*")
public class ImpactFileReadController {

    @Autowired
    private ImpactFileReaderService impactFileReaderService;

    @GetMapping("/statuscsv/batch")
    public ResponseEntity<Object> getBatchWiseStatus(
            @RequestParam String crqNo
    ) {

        try {

            Map<String, BatchStatusResponseDTO> result =
                    impactFileReaderService.getBatchWiseStatus(crqNo);

            return ResponseEntity.ok(result);

        } catch (RuntimeException ex) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "status", "ERROR",
                            "message", ex.getMessage(),
                            "crqNo", crqNo
                    ));

        } catch (Exception ex) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "ERROR",
                            "message", "Unexpected server error",
                            "details", ex.getMessage()
                    ));
        }
    }
}
