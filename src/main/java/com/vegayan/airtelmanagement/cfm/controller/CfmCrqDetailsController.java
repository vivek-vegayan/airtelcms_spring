package com.vegayan.airtelmanagement.cfm.controller;

import com.vegayan.airtelmanagement.cfm.service.CfmCrqDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cfm")
@RequiredArgsConstructor
public class CfmCrqDetailsController {

    private final CfmCrqDetailsService cfmCrqDetailsService;

    @PostMapping("/crq-details/{crqNo}/{batchNo}")
    public ResponseEntity<String> sendCrqDetails(
            @PathVariable String crqNo,
            @PathVariable String batchNo) {

        return ResponseEntity.ok(
                cfmCrqDetailsService.sendCrqDetails(crqNo, batchNo));
    }
}
