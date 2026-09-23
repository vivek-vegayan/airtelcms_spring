package com.vegayan.airtelmanagement.sygnet.controller;


import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.sygnet.dto.CrqStatusUpdateResponseDto;
import com.vegayan.airtelmanagement.sygnet.service.CrqStatusUpdateService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crq_status")
@RequiredArgsConstructor
public class CrqStatusUpdateController {

    private final CrqStatusUpdateService crqStatusUpdateService;


    @LogType("Crq_Status_Update_External")
    @PostMapping("/update/{crqNo}")
    public ResponseEntity<CrqStatusUpdateResponseDto> crqStatusUpdate(
            @PathVariable String crqNo) {

        CrqStatusUpdateResponseDto response =
                crqStatusUpdateService.crqStatusUpdate(crqNo);

        return ResponseEntity.ok(response);
    }
}
