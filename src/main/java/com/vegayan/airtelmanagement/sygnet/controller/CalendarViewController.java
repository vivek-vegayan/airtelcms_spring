package com.vegayan.airtelmanagement.sygnet.controller;

import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.sygnet.dto.CalendarViewResponseDto;
import com.vegayan.airtelmanagement.sygnet.dto.SchedulingClientRequest;
import com.vegayan.airtelmanagement.sygnet.dto.SchedulingInternalCommand;
import com.vegayan.airtelmanagement.sygnet.service.CalendarViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarViewController {

    private final CalendarViewService service;

    @LogType("Scheduler_External_Logger")
    @PostMapping("/view")
    public ResponseEntity<CalendarViewResponseDto> getCalendarView(
            @AuthenticationPrincipal String olmId,
            @RequestBody SchedulingClientRequest clientRequest) {

        SchedulingInternalCommand command = new SchedulingInternalCommand();
        command.setRequestorOlmId(olmId);
        command.setClientData(clientRequest);

        CalendarViewResponseDto response = service.getCalendarView(command);
        return ResponseEntity.ok(response);
    }
}
