package com.vegayan.airtelmanagement.sygnet.service;


import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.sygnet.dto.BookSlotOutputDto;
import com.vegayan.airtelmanagement.sygnet.dto.GetSlotOutputDto;
import com.vegayan.airtelmanagement.sygnet.dto.SchedulingInputDataDto;
import com.vegayan.airtelmanagement.sygnet.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final SlotRepository slotRepository;

    private String generateReservationId(String olmId) {
        long epoch = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .toInstant().toEpochMilli();
        return olmId + "_" + epoch;
    }

    private String normalizeChangeImpact(String value) {
        if ("Service Affecting".equalsIgnoreCase(value)) return "SA";
        if ("Non Service Affecting".equalsIgnoreCase(value)) return "NSA";
        return value;
    }


    public GetSlotOutputDto getSlots(SchedulingInputDataDto req) {
        try {
            String reservationId = generateReservationId(req.getRequestorOlmId());
            String changeImpact = normalizeChangeImpact(req.getChangeImpact());
            slotRepository.insertCRQReservation1(reservationId, req, changeImpact);
            return slotRepository.getFreshSlotsNew(reservationId);
        } catch (DatabaseOperationException ex) {
            GetSlotOutputDto response = new GetSlotOutputDto();
            response.setStatus("failed");
            response.setAvailableTimeSlots(null);
            response.setMessage("No slots available");
            response.setError(ex.getMessage());
            return response;
        }
    }

    public BookSlotOutputDto bookSlots(SchedulingInputDataDto req) {
        String reservationId = generateReservationId(req.getRequestorOlmId());
        slotRepository.insertCRQReservation2(reservationId, req);
        BookSlotOutputDto output = new BookSlotOutputDto();
        output.setStatus("success");
        output.setMessage("slot booked");
        return output;
    }

}
