package com.vegayan.airtelmanagement.sygnet.repository;

import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.sygnet.dto.CalendarViewResponseDto;
import com.vegayan.airtelmanagement.sygnet.dto.SchedulingClientRequest;
import com.vegayan.airtelmanagement.sygnet.dto.SchedulingInternalCommand;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.vegayan.airtelmanagement.common.service.CommonService.formatProcedureCall;

@Repository
@RequiredArgsConstructor
public class CalendarViewRepository extends BaseService {

    private final CommonService commonService;

    private static final Logger schedulerExternalLog = LoggerFactory.getLogger("Scheduler_External_Logger");

    public void insertReservation(String reservationId, SchedulingInternalCommand command) {
        SchedulingClientRequest clientData = command.getClientData();

        Object[] params = new Object[]{
                reservationId,
                command.getRequestorOlmId(),
                clientData.getPlanType(),
                clientData.getPlanId(),
                clientData.getImpTaskId(),
                clientData.getActivity(),
                clientData.getM6Location(),
                clientData.getImpact(),
                clientData.getDomain(),
                clientData.getPlanDomain(),
                clientData.getSubdomain(),
                normalizeChangeImpact(clientData.getChangeImpact()), // Normalized here
                clientData.getVendor()
        };
        // 2. Log it
        String logCall = formatProcedureCall("Insert_CRQ_Reservation", params);
        schedulerExternalLog.info(logCall);

        // 3. Execute it directly!
        String sql = "{call Insert_CRQ_Reservation(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)}";

        databaseUtils.executeProcedureWithError(
                jdbcTemplateTwo,
                sql,
                params
        );
    }

    private String normalizeChangeImpact(String value) {
        if ("Service Affecting".equalsIgnoreCase(value)) return "SA";
        if ("Non Service Affecting".equalsIgnoreCase(value)) return "NSA";
        return value;
    }

    public CalendarViewResponseDto getPredictedDates(String reservationId) {
        String sql = "{call Get_Predicted_SlotDates(?)}";
        schedulerExternalLog.info("Executing query: call Get_Predicted_SlotDates('{}');", reservationId);
        List<CalendarViewResponseDto> result =
                databaseUtils.executeProcedureGetDataWithError(
                        jdbcTemplateTwo,
                        sql,
                        CalendarViewResponseDto.class,
                        reservationId
                );

        return result.stream()
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException("No predicted dates available."));
    }

    public void updateCalendar(String reservationId, String startDate, String endDate) {
        schedulerExternalLog.info("call Update_Predicted_SlotDates('{}','{}','{}');", reservationId, startDate, endDate);
        String procedureCall = "call Update_Predicted_SlotDates(?, ?, ?)";
        jdbcTemplateTwo.update(procedureCall, reservationId, startDate, endDate);
    }
}
