package com.vegayan.airtelmanagement.remedy.controller;



import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.remedy.dto.*;
import com.vegayan.airtelmanagement.remedy.service.CabRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cab-request")
@RequiredArgsConstructor
public class CabRequestController {

    private final CabRequestService cabRequestService;

    @LogType("Cab_Request_Logger")
    @PostMapping("/scheduled-for-review")
    public ResponseEntity<?> updateCrq(
            @RequestBody RemedyCabRequestDto requestPayload) {

        String response =
                cabRequestService.remedyCabRequest(requestPayload);

        return ResponseEntity.ok(response);
    }

    @LogType("Cab_Request_Logger")
    @PostMapping("/spocupdate")
    public SpocUpdateChmResponse spocUpdateExternal(
            @RequestBody SpocUpdateDTO body) {

        return cabRequestService.spocUpdateExternal(body);
    }
}
