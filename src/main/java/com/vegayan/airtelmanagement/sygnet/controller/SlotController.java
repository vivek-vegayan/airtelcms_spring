package com.vegayan.airtelmanagement.sygnet.controller;

import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.sygnet.dto.BookSlotOutputDto;
import com.vegayan.airtelmanagement.sygnet.dto.GetSlotOutputDto;
import com.vegayan.airtelmanagement.sygnet.dto.SchedulingInputDataDto;
import com.vegayan.airtelmanagement.sygnet.dto.SlotOutput;
import com.vegayan.airtelmanagement.sygnet.service.SlotService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
public class SlotController {


    private final SlotService slotService;


    @LogType("Scheduler_External_Logger")
    @PostMapping("/viewslot")
    public ResponseEntity<GetSlotOutputDto> getSlots(
            @RequestBody SchedulingInputDataDto req
    ) {
        GetSlotOutputDto response = slotService.getSlots(req);
        return ResponseEntity.ok(response);
    }

    @LogType("Scheduler_External_Logger")
    @PostMapping("/bookslot")
    public ResponseEntity<BookSlotOutputDto> bookSlots(
            @RequestBody SchedulingInputDataDto req
    ) {

        BookSlotOutputDto response = slotService.bookSlots(req);

        return ResponseEntity.ok(response);
    }




}
