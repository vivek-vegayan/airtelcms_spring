package com.vegayan.airtelmanagement.remedy.controller;

import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.remedy.dto.RemedyChangeRequest;
import com.vegayan.airtelmanagement.remedy.service.ChangeRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/change-request")
public class ChangeRequestController {

    @Autowired
    private ChangeRequestService changeRequestService;


    @LogType("Change_Request_Logger")
    @PostMapping("/planning-in-progress")
    public ResponseEntity<?> updateCrq(
            @RequestBody RemedyChangeRequest requestPayload) {

        String response =
                changeRequestService.remedyChangeRequest(requestPayload);

        return ResponseEntity.ok(response);
    }
}
