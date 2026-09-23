package com.vegayan.airtelmanagement.sygnet.service;

import com.vegayan.airtelmanagement.sygnet.dto.CalendarViewResponseDto;
import com.vegayan.airtelmanagement.sygnet.dto.SchedulingInternalCommand;
import com.vegayan.airtelmanagement.sygnet.repository.CalendarViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class CalendarViewService {
    private final CalendarViewRepository repository;

    public CalendarViewResponseDto getCalendarView(SchedulingInternalCommand command) {

        ZonedDateTime kolkataTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        long epochMillis = kolkataTime.toInstant().toEpochMilli();

        String reservationId = command.getRequestorOlmId() + "_" + epochMillis;

        repository.insertReservation(reservationId, command);

        CalendarViewResponseDto response =
                repository.getPredictedDates(reservationId);

        repository.updateCalendar(reservationId,
                response.getStartDate(),
                response.getEndDate());

        response.setStatus("success");
        return response;
    }
}
