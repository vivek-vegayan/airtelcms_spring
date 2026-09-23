package com.vegayan.airtelmanagement.sygnet.repository;

import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.sygnet.dto.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.vegayan.airtelmanagement.common.service.CommonService.formatProcedureCall;

@Repository
@RequiredArgsConstructor
public class SlotRepository extends BaseService {

    private final JdbcTemplate jdbcTemplateTwo;

    private static final Logger schedulerExternalLog = LoggerFactory.getLogger("Scheduler_External_Logger");

    /* ================= View SLot Insert External  ================= */
    public void insertCRQReservation1(
            String reservationId,
            SchedulingInputDataDto req,
            String changeImpact
    ) {

        Object[] params = req.toProcedureParamsNew(reservationId);

        String logCall = formatProcedureCall("Insert_CRQ_RESERVATION_1", params);
        schedulerExternalLog.info(logCall);

        String sql = "{call Insert_CRQ_RESERVATION_1(?,?,?,?,?,?,?,?,?,?,?,?,?,?)}";

        Object desiredDate = req.getDesiredDate() != null
                ? Date.valueOf(req.getDesiredDate())
                : null;

        databaseUtils.executeProcedureWithError(
                jdbcTemplateTwo,
                sql,
                reservationId,
                req.getRequestorOlmId(),
                req.getPlanType(),
                req.getPlanId(),
                req.getImpTaskId(),
                req.getActivity(),
                req.getM6Location(),
                req.getImpact(),
                req.getDomain(),
                req.getPlanDomain(),
                req.getSubdomain(),
                changeImpact,
                req.getVendor(),
                desiredDate
        );
    }

    //-----------------------Book SLot External-------------------
    public void insertCRQReservation2(
            String reservationId,
            SchedulingInputDataDto req
    ) {

        Object[] params = req.toProcedureParamsNew2(reservationId);

        String logCall = formatProcedureCall("Insert_CRQ_RESERVATION_2", params);
        schedulerExternalLog.info(logCall);

        String sql = "{call Insert_CRQ_RESERVATION_2(?,?,?,?,?,?,?,?)}";
        databaseUtils.executeProcedureWithError(
                jdbcTemplateTwo,
                sql,
                reservationId,
                req.getRequestorOlmId(),
                req.getPlanType(),
                req.getPlanId(),
                req.getImpTaskId(),
                req.getActivity(),
                req.getRemarks(),
                req.getLabel()
        );
    }

    /* ================= ACCEPTED SLOTS ================= */

    public SlotOutput getAcceptedSlots(String planId, Acknowledgement ack) {

        String sql = "{call Get_Possible_EmpName_List(?,?,?,?)}";

        List<TimeSlot> slots = jdbcTemplateTwo.execute((Connection conn) -> {
            try (CallableStatement cs = conn.prepareCall(sql)) {

                cs.setString(1, planId);
                cs.setString(2, ack.getAckStatus());

                // Check if the startDateTime is not empty or invalid
                String startDateTime = ack.getAcceptedTimeSlot().get(0).getStartDateTime();
                String formattedStartDateTime = (startDateTime != null && !startDateTime.isEmpty()) ? formatDate(startDateTime) : null;

                // Check if the endDateTime is not empty or invalid
                String endDateTime = ack.getAcceptedTimeSlot().get(0).getEndDateTime();
                String formattedEndDateTime = (endDateTime != null && !endDateTime.isEmpty()) ? formatDate(endDateTime) : null;

                cs.setString(3, formattedStartDateTime);
                cs.setString(4, formattedEndDateTime);


                try (ResultSet rs = cs.executeQuery()) {
                    List<TimeSlot> list = new ArrayList<>();
                    while (rs.next()) {
                        TimeSlot ts = new TimeSlot();
                        ts.setLabel(rs.getString(1));
                        ts.setStartDateTime(toIso(rs.getString(2)));
                        ts.setEndDateTime(toIso(rs.getString(3)));
                        list.add(ts);
                    }
                    return list;
                }
            }
        });

        return SlotOutput.successOrFail(
                slots,
                "slot booked",
                "no slots available"
        );
    }

    /* ================= FRESH SLOTS ================= */

    public SlotOutput getFreshSlots(String reservationId) {
        String sql = "{call Get_EmpName_By_DesiredDate(?)}";
        LOGGER.info(String.format("Executing query: call Get_EmpName_By_DesiredDate('%s');", reservationId));
        return jdbcTemplateTwo.execute((Connection conn) -> {
            try (CallableStatement cs = conn.prepareCall(sql)) {

                cs.setString(1, reservationId);

                try (ResultSet rs = cs.executeQuery()) {

                    List<TimeSlot> slots = new ArrayList<>();
                    ResultSetMetaData md = rs.getMetaData();

                    // Error message from SP
                    if (md.getColumnCount() == 1 && rs.next()) {
                        return SlotOutput.failed(rs.getString(1));
                    }

                    while (rs.next()) {
                        TimeSlot ts = new TimeSlot();
                        ts.setLabel(rs.getString(1));
                        ts.setStartDateTime(toIso(rs.getString(2)));
                        ts.setEndDateTime(toIso(rs.getString(3)));
                        slots.add(ts);
                    }

                    return SlotOutput.successOrFail(
                            slots,
                            "available slots",
                            "no slots available"
                    );
                }
            }
        });
    }

    private static final DateTimeFormatter DB_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter OUTPUT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* ================= Fresh Slots External  ================= */
    public GetSlotOutputDto getFreshSlotsNew(String reservationId) {
        String sql = "CALL Get_EmpName_By_DesiredDate(?)";
        schedulerExternalLog.info("Executing query: call Get_EmpName_By_DesiredDate('{}');", reservationId);
        try {
            List<TimeSlot> slots =
                    databaseUtils.executeProcedureGetDataWithError(
                            jdbcTemplateTwo,
                            sql,
                            TimeSlot.class,
                            reservationId
                    );
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

            List<TimeSlot> externalSlots = slots.stream().map(slot -> {

                String start = slot.getStartDateTime() != null
                        ? LocalDateTime.parse(slot.getStartDateTime(), inputFormatter).format(outputFormatter)
                        : null;

                String end = slot.getEndDateTime() != null
                        ? LocalDateTime.parse(slot.getEndDateTime(), inputFormatter).format(outputFormatter)
                        : null;

                return new TimeSlot(
                        slot.getLabel(),
                        start,
                        end
                );

            }).toList();

            GetSlotOutputDto response = new GetSlotOutputDto();
            response.setStatus("success");
            response.setAvailableTimeSlots(externalSlots);
            response.setMessage("available slots");
            response.setError("");

            return response;

        } catch (DatabaseOperationException ex) {

            return GetSlotOutputDto.fail(ex.getMessage());
        }
    }

    /* ================= HELPERS ================= */

    private static String toIso(String db) {
        return db.replace(" ", "T").replaceAll("\\.0$", "");
    }

    private static String formatDate(String iso) {
        return iso.replace("T", " ");
    }

}