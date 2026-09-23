package com.vegayan.airtelmanagement.schedular.service;

import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.schedular.dto.*;
import com.vegayan.airtelmanagement.schedular.repository.CrqRescheduleRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CrqRescheduleService extends BaseService {

    private static final Set<String> BLOCKING_STATUSES = Set.of("error", "blocked");

    private final CrqRescheduleRepository rescheduleRepository;

    public CrqRescheduleService(CrqRescheduleRepository rescheduleRepository) {
        this.rescheduleRepository = rescheduleRepository;
    }

    /* ── row helpers ──────────────────────────────────────────────────────── */

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }

    private static Long num(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.valueOf(v.toString());
    }

    private static Integer intVal(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.valueOf(v.toString());
    }


    private static boolean flag(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return "1".equals(v.toString()) || Boolean.parseBoolean(v.toString());
    }


    private static List<String> csv(Map<String, Object> row, String key) {
        String value = str(row, key);
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }


    private static boolean isSlotRows(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return false;
        Map<String, Object> first = rows.get(0);
        return first.containsKey("Label") && !first.containsKey("status");
    }

    private static RescheduleSlotDto toSlot(Map<String, Object> r) {
        return new RescheduleSlotDto(
                str(r, "Label"), str(r, "StartDateTime"), str(r, "EndDateTime"),
                str(r, "Engineer_Olm_Id"), str(r, "Engineer_Name"), str(r, "Shift_Letter"),
                intVal(r, "Free_Minutes"), intVal(r, "Duration_Minutes"), str(r, "Skill_Level"));
    }


    private Map<String, Object> checkStatusRow(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            throw new BusinessException("No response from the database.");
        }
        Map<String, Object> row = rows.get(0);

        String errorMessage = str(row, "error_message");
        if (errorMessage != null && !errorMessage.isBlank()) {
            throw new BusinessException(errorMessage);
        }
        String status = str(row, "status");
        if (status != null && BLOCKING_STATUSES.contains(status)) {
            throw new BusinessException(str(row, "message"));
        }
        return row;
    }

    private String resolveOlmId(Long actorUserId) {
        if (actorUserId == null) return null;
        try {
            List<Map<String, Object>> rows = rescheduleRepository.findOlmIdByUserId(actorUserId);
            return rows.isEmpty() ? null : str(rows.get(0), "olmid");
        } catch (Exception e) {
            LOGGER.warn("Could not resolve olmid for user {}: {}", actorUserId, e.getMessage());
            return null;
        }
    }

    /* ── wizard steps ─────────────────────────────────────────────────────── */

    public RescheduleContextResponseDto getContext(Long crqId) {
        if (crqId == null) throw new BusinessException("crqId is required.");
        Map<String, Object> row = checkStatusRow(rescheduleRepository.findContext(crqId));
        return new RescheduleContextResponseDto(
                str(row, "status"), str(row, "message"),
                num(row, "crq_id"), str(row, "crq_no"),
                str(row, "current_stage"), str(row, "current_status"),
                intVal(row, "reschedule_count"), intVal(row, "max_reschedules"),
                flag(row, "reschedule_blocked"), flag(row, "can_reschedule"),
                str(row, "blocked_reason"),
                str(row, "plan_no"), num(row, "task_row_id"), str(row, "task_id"),
                intVal(row, "task_count"),
                str(row, "engineer_olm_id"), str(row, "engineer_name"), str(row, "shift_letter"),
                str(row, "scheduled_start"), str(row, "scheduled_end"),
                csv(row, "eligible_stages"),
                num(row, "active_reschedule_id"), str(row, "active_reschedule_status"),
                str(row, "active_desired_date"), str(row, "active_to_stage"),
                str(row, "active_activity_epoch"));
    }

    /**
     * Step 1: creates the attempt and returns the scheduling calendar with it.
     * The procedure emits the calendar first and its own status row last, so
     * both result sets are read in one round trip.
     */
    public RescheduleActionResponseDto initiate(Long actorUserId, RescheduleInitiateRequest request) {
        if (request == null || request.crqId() == null) {
            throw new BusinessException("crqId is required.");
        }
        String reason = request.reason() == null ? null : request.reason().trim();
        if (reason != null && reason.isBlank()) reason = null;
        String remark = request.remark() == null ? null : request.remark().trim();
        if (remark != null && remark.isBlank()) remark = null;

        String performer = resolveOlmId(actorUserId);
        List<List<Map<String, Object>>> resultSets =
                rescheduleRepository.initiate(request.crqId(), performer, reason, remark);
        if (resultSets.isEmpty()) {
            throw new BusinessException("No response from the database.");
        }

        // Status row is always last; anything before it is the calendar.
        Map<String, Object> statusRow = checkStatusRow(resultSets.get(resultSets.size() - 1));
        Map<String, Object> calendarRow = null;
        for (int i = 0; i < resultSets.size() - 1; i++) {
            List<Map<String, Object>> rs = resultSets.get(i);
            if (!rs.isEmpty() && rs.get(0).containsKey("startDate")) {
                calendarRow = rs.get(0);
            }
        }

        return new RescheduleActionResponseDto(
                str(statusRow, "status"), str(statusRow, "message"),
                num(statusRow, "reschedule_id"), str(statusRow, "activity_epoch"),
                calendarRow == null ? null : str(calendarRow, "startDate"),
                calendarRow == null ? null : str(calendarRow, "endDate"),
                calendarRow == null ? null : str(calendarRow, "busyDates"),
                calendarRow == null ? null : str(calendarRow, "weekendDates"),
                calendarRow == null ? null : str(calendarRow, "holidayDates"),
                calendarRow == null ? null : str(calendarRow, "networkFreeDates"));
    }

    /** Step 2 (Refresh): recompute the window without creating another attempt. */
    public RescheduleCalendarResponseDto getCalendar(Long rescheduleId) {
        if (rescheduleId == null) throw new BusinessException("rescheduleId is required.");
        Map<String, Object> row = checkStatusRow(rescheduleRepository.findCalendar(rescheduleId));
        return new RescheduleCalendarResponseDto(
                str(row, "status"), str(row, "message"), str(row, "startDate"), str(row, "endDate"),
                str(row, "busyDates"), str(row, "weekendDates"), str(row, "holidayDates"),
                str(row, "networkFreeDates"));
    }

    /** Step 2: persist the chosen date. The procedure re-validates that it is in the future. */
    public RescheduleStatusResponseDto saveDate(RescheduleSaveDateRequest request) {
        if (request == null || request.rescheduleId() == null) {
            throw new BusinessException("rescheduleId is required.");
        }
        if (request.desiredDate() == null || request.desiredDate().isBlank()) {
            throw new BusinessException("A desired date is required.");
        }
        Map<String, Object> row = checkStatusRow(
                rescheduleRepository.saveDate(request.rescheduleId(), request.desiredDate()));
        return new RescheduleStatusResponseDto(str(row, "status"), str(row, "message"));
    }

    /**
     * Step 3: move the CRQ to the chosen earlier stage. The procedure validates
     * the transition (strictly-earlier stage, not closed, not on hold, under the
     * attempt cap) and, on success, emits the recomputed engineer slots - which
     * are returned here so the slot step opens already populated.
     */
    public RescheduleMoveStageResponseDto moveStage(Long actorUserId, RescheduleMoveStageRequest request) {
        if (request == null || request.rescheduleId() == null) {
            throw new BusinessException("rescheduleId is required.");
        }
        if (request.toStage() == null || request.toStage().isBlank()) {
            throw new BusinessException("A target stage is required.");
        }

        String performer = resolveOlmId(actorUserId);
        List<List<Map<String, Object>>> resultSets =
                rescheduleRepository.moveStage(request.rescheduleId(), request.toStage(), performer);
        if (resultSets.isEmpty()) {
            throw new BusinessException("No response from the database.");
        }

        List<Map<String, Object>> last = resultSets.get(resultSets.size() - 1);

        // Slot rows as the final result set means the move AND the slot
        // computation both succweeded.
        if (isSlotRows(last)) {
            return new RescheduleMoveStageResponseDto(
                    "success", "Stage moved.", last.stream().map(CrqRescheduleService::toSlot).toList());
        }

        if (last.isEmpty()) {
            throw new BusinessException("No response from the database.");
        }
        Map<String, Object> row = last.get(0);

        boolean moveCommitted = resultSets.size() > 1;

        String errorMessage = str(row, "error_message");
        String status = str(row, "status");
        String message = errorMessage != null && !errorMessage.isBlank()
                ? errorMessage
                : str(row, "message");

        if (!moveCommitted) {
            if ((errorMessage != null && !errorMessage.isBlank())
                    || (status != null && BLOCKING_STATUSES.contains(status))) {
                throw new BusinessException(message);
            }
            return new RescheduleMoveStageResponseDto(
                    status == null ? "success" : status, message, List.of());
        }

        if ((errorMessage != null && !errorMessage.isBlank())
                || (status != null && BLOCKING_STATUSES.contains(status))) {
            return new RescheduleMoveStageResponseDto(
                    "partial",
                    "Stage moved, but engineer slots could not be computed: " + message,
                    List.of());
        }
        return new RescheduleMoveStageResponseDto(
                status == null ? "success" : status,
                message == null ? "Stage moved." : message,
                List.of());
    }

    /** Step 4 (Refresh): re-cut the offer window without repeating earlier steps. */
    public RescheduleSlotsResponseDto getSlots(Long rescheduleId) {
        if (rescheduleId == null) throw new BusinessException("rescheduleId is required.");

        List<Map<String, Object>> rows = rescheduleRepository.findSlots(rescheduleId);
        if (rows.isEmpty()) {
            return new RescheduleSlotsResponseDto("error", "No response from the database.", List.of());
        }
        Map<String, Object> first = rows.get(0);

        String errorMessage = str(first, "error_message");
        if (errorMessage != null && !errorMessage.isBlank()) {
            throw new BusinessException(errorMessage);
        }
        String status = str(first, "status");
        if (status != null) {
            if (BLOCKING_STATUSES.contains(status)) {
                throw new BusinessException(str(first, "message"));
            }
            return new RescheduleSlotsResponseDto(status, str(first, "message"), List.of());
        }

        return new RescheduleSlotsResponseDto(
                "success", "Slots retrieved.", rows.stream().map(CrqRescheduleService::toSlot).toList());
    }

    /**
     * Step 5: confirm the chosen slot - the call that commits the reschedule.
     */
    public RescheduleConfirmResponseDto confirmSlot(Long actorUserId, RescheduleConfirmSlotRequest request) {
        if (request == null || request.rescheduleId() == null) {
            throw new BusinessException("rescheduleId is required.");
        }
        if (request.slotLabel() == null || request.slotLabel().isBlank()) {
            throw new BusinessException("A slot must be selected.");
        }

        String performer = resolveOlmId(actorUserId);
        Map<String, Object> row = checkStatusRow(
                rescheduleRepository.confirmSlot(request.rescheduleId(), request.slotLabel(), performer));
        return new RescheduleConfirmResponseDto(
                str(row, "status"), str(row, "message"), num(row, "Schedule_ID"),
                str(row, "Engineer_Olm_Id"), str(row, "Engineer_Name"), str(row, "Shift_Letter"),
                str(row, "Slot_Start"), str(row, "Slot_End"));
    }

    /** Abandon an in-flight attempt: restores the parked reservation, expires offers. */
    public RescheduleStatusResponseDto cancel(Long actorUserId, RescheduleCancelRequest request) {
        if (request == null || request.rescheduleId() == null) {
            throw new BusinessException("rescheduleId is required.");
        }
        String performer = resolveOlmId(actorUserId);
        Map<String, Object> row = checkStatusRow(
                rescheduleRepository.cancel(request.rescheduleId(), performer, request.reason()));
        return new RescheduleStatusResponseDto(str(row, "status"), str(row, "message"));
    }

    /** The fixed reason list sp_reschedule_reason_drop_down offers on Reschedule Details. */
    public List<RescheduleReasonOptionDto> getReasonOptions() {
        return rescheduleRepository.findReasonOptions().stream()
                .map(row -> new RescheduleReasonOptionDto(str(row, "reason")))
                .toList();
    }
}
