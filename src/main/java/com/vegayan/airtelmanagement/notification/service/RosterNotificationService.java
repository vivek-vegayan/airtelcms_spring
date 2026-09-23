package com.vegayan.airtelmanagement.notification.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.notification.dto.CabRejectReasonDto;
import com.vegayan.airtelmanagement.notification.dto.NotificationCountDto;
import com.vegayan.airtelmanagement.notification.dto.UnreadNotifiactionsDto;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Service
public class RosterNotificationService extends BaseService {

    /**
     * An inbox with nothing left in it is an empty list, not a failure.
     *
     * sp_get_unread_notifications reports "nothing to show" as an
     * {@code error_message} row, which executeProcedureGetDataWithError turns
     * into a DatabaseOperationException and the global handler into a 500. That
     * 500 is what kept an actioned item on screen: the UI keeps the list it
     * already has whenever a refetch errors, so approving the last pending
     * notification left it sitting in the inbox instead of clearing it.
     *
     * A procedure-raised message arrives with no cause attached - a genuine
     * JDBC failure always carries one - so only the former is downgraded here
     * and real database errors keep answering 500 as before.
     */
    public List<UnreadNotifiactionsDto> getUnreadNotifications(String actorUserId,Boolean readFlag) {
        String sql = "CALL sp_get_unread_notifications(?,?)";
        LOGGER.info("call sp_get_unread_notifications('{}','{}');", actorUserId,readFlag);
        try {
            return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, UnreadNotifiactionsDto.class, actorUserId,readFlag);
        } catch (DatabaseOperationException ex) {
            if (ex.getCause() == null) {
                LOGGER.debug("No notifications for user {} (readFlag {}): {}", actorUserId, readFlag, ex.getMessage());
                return Collections.emptyList();
            }
            throw ex;
        }
    }

    public ApiResponse changedNotificationReadStatus(Long notificationId) {
        String sql = "CALL sp_change_notification_read_status(?)";
        LOGGER.info("call sp_change_notification_read_status('{}');", notificationId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                notificationId
        );
    }

    public ApiResponse shiftChangeStatusChange(Long actorUserId, Long affectedUserId, String status, LocalDate shiftDate ,Long notificationId) {
        String sql = "CALL sp_shift_change_status_change(?,?,?,?,?)";
        LOGGER.info("call sp_shift_change_status_change('{}','{}','{}','{}','{}');",  actorUserId, affectedUserId, status, shiftDate, notificationId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                affectedUserId,
                status,
                shiftDate,
                notificationId
        );
    }

    public NotificationCountDto getNotificationCount(Long actorUserId, Boolean readFlag) {
        String sql = "CALL sp_get_notification_count(?,?)";
                LOGGER.info("call sp_get_notification_count('{}','{}');", actorUserId, readFlag);

        List<NotificationCountDto> result =
                databaseUtils.executeProcedureGetDataWithError(
                        jdbcTemplateTwo,
                        sql,
                        NotificationCountDto.class,
                        actorUserId,
                        readFlag
                );

        // No row means no notifications - the badge is zero, not an error.
        return result.isEmpty() ? new NotificationCountDto(0L) : result.get(0);
    }


    //request approve by 2nd member
    public ApiResponse swapReqEmpAction(Long actorUserId,Long notificationId,String status,String rejectReason) {
        String sql = "CALL sp_swap_req_emp_action(?,?,?,?)";
        LOGGER.info("call sp_swap_req_emp_action('{}','{}','{}','{}');",  actorUserId,notificationId, status, rejectReason);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                notificationId,
                status,
                rejectReason
        );
    }

    //Swap request approve by manager
    public ApiResponse swapReqManagerAction(Long actorUserId,Long notificationId,String status,String rejectReason) {
        String sql = "CALL sp_swap_req_manager_action(?,?,?,?)";
        LOGGER.info("call sp_swap_req_manager_action('{}','{}','{}','{}');",  actorUserId,notificationId, status, rejectReason);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                notificationId,
                status,
                rejectReason
        );
    }

    //Leave Request Approved By Manger
    public ApiResponse rosterLeaveStatusChange(Long actorUserId,Long notificationId,String status,String rejectReason) {
        String sql = "CALL sp_roster_leave_status_change(?,?,?,?)";
        LOGGER.info("call sp_roster_leave_status_change('{}','{}','{}','{}');",  actorUserId,notificationId, status, rejectReason);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                notificationId,
                status,
                rejectReason
        );
    }

    //Shift change request approved/rejected from the notification inbox
    public ApiResponse shiftChangeNotificationAction(Long actorUserId, Long notificationId, String status, String rejectReason) {
        String sql = "CALL sp_shift_change_notification_action(?,?,?,?)";
        LOGGER.info("call sp_shift_change_notification_action('{}','{}','{}','{}');", actorUserId, notificationId, status, rejectReason);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                notificationId,
                status,
                rejectReason
        );
    }

    //CAB CRQ approved/rejected from the notification inbox
    public ApiResponse cabCrqNotificationAction(Long actorUserId, Long notificationId, String status, String reason, String comment) {
        String sql = "CALL sp_cab_crq_notification_action(?,?,?,?,?)";
        LOGGER.info("call sp_cab_crq_notification_action('{}','{}','{}','{}','{}');", actorUserId, notificationId, status, reason, comment);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                notificationId,
                status,
                reason,
                comment
        );
    }

    /** The only two decisions the CAB reschedule endpoint accepts. */
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    /**
     * CAB reschedule request approved/rejected from the notification inbox.
     *
     * Calls the two dedicated procedures - sp_approve_crq_cab_reschedule_req
     * and sp_reject_crq_cab_reschedule_req - directly rather than going through
     * the sp_cab_reschedule_notification_action wrapper, because the wrapper
     * hid the outcome twice over:
     *
     *  - its blanket {@code EXIT HANDLER FOR SQLEXCEPTION} replaced every
     *    refusal the approve procedure RESIGNALs ("CRQ not found for given
     *    crq_no") with a generic "Something went wrong";
     *  - on the reject path the child procedure's own error_message was
     *    followed by the wrapper's success_message, and
     *    executeProcedureForMessageV1 reads a later success_message as "the
     *    operation committed" - so a rolled-back reject was reported to the
     *    inbox as a success.
     *
     * The lookups and the two writes below are exactly what the wrapper did, so
     * nothing about the stored logic changes - only where it is driven from,
     * and the fact that a refusal now reaches the caller as a 409 carrying the
     * procedure's real message.
     */
    public ApiResponse cabRescheduleNotificationAction(Long actorUserId, Long notificationId, String status, String reason, String comment) {

        String decision = status == null ? "" : status.trim().toUpperCase();

        if (!STATUS_APPROVED.equals(decision) && !STATUS_REJECTED.equals(decision)) {
            throw new BusinessException("Invalid status. Use APPROVED or REJECTED.");
        }

        Long requestId = resolveRescheduleRequestId(notificationId);
        CabRescheduleRequest request = loadRescheduleRequest(requestId);

        if (request.resultStatus() != null && !request.resultStatus().isBlank()) {
            throw new BusinessException(
                    "This reschedule request was already " + request.resultStatus().toLowerCase() + ".");
        }

        ApiResponse response;

        if (STATUS_APPROVED.equals(decision)) {

            LOGGER.info("call sp_approve_crq_cab_reschedule_req('{}','{}','{}','{}');",
                    actorUserId, request.crqNo(), request.requestedStart(), request.requestedEnd());

            response = databaseUtils.executeProcedureForMessageV1(
                    jdbcTemplateTwo,
                    "CALL sp_approve_crq_cab_reschedule_req(?,?,?,?)",
                    actorUserId,
                    request.crqNo(),
                    request.requestedStart(),
                    request.requestedEnd()
            );

            // The approve procedure moves the slot but leaves its own request
            // row open - closing it is what stops the same request being
            // approved twice.
            jdbcTemplateTwo.update(
                    "UPDATE CRQ_CAB_RESCHEDULE_REQUEST_TBL "
                            + "SET Result_Status = 'APPROVED', "
                            + "    Result_Message = LEFT(COALESCE(?, 'Approved'), 255) "
                            + "WHERE Request_Id = ?",
                    comment,
                    requestId
            );

        } else {

            LOGGER.info("call sp_reject_crq_cab_reschedule_req('{}','{}','{}','{}');",
                    actorUserId, requestId, reason, comment);

            // This one closes its own request row, so there is nothing to
            // follow up with here.
            response = databaseUtils.executeProcedureForMessageV1(
                    jdbcTemplateTwo,
                    "CALL sp_reject_crq_cab_reschedule_req(?,?,?,?)",
                    actorUserId,
                    requestId,
                    reason,
                    comment
            );
        }

        // Only once the decision itself committed: take the notification out of
        // the unread inbox and record how it ended, so the item disappears from
        // the list on the next fetch instead of coming back still pending.
        jdbcTemplateTwo.update(
                "UPDATE NOTIFICATION_QUEUE SET read_flag = 1, request_status = ? WHERE notification_id = ?",
                STATUS_APPROVED.equals(decision) ? "COMPLETED" : "CLOSED",
                notificationId
        );

        return response;
    }

    /** The reschedule request a CAB notification refers to. */
    private record CabRescheduleRequest(
            String crqNo,
            Timestamp requestedStart,
            Timestamp requestedEnd,
            String resultStatus
    ) {}

    /**
     * The reschedule request behind a notification. The link is the payload's
     * entity_id, written there when the request raised the notification.
     */
    private Long resolveRescheduleRequestId(Long notificationId) {

        List<Long> entityIds = jdbcTemplateTwo.query(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.entity_id')) AS entity_id "
                        + "FROM NOTIFICATION_QUEUE "
                        + "WHERE notification_id = ? AND channel = 'EMAIL'",
                (rs, rowNum) -> {
                    String value = rs.getString("entity_id");
                    if (value == null || value.isBlank()) {
                        return null;
                    }
                    try {
                        return Long.valueOf(value.trim());
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                },
                notificationId
        );

        if (entityIds.isEmpty()) {
            throw new BusinessException("Notification not found.");
        }

        Long requestId = entityIds.get(0);

        if (requestId == null || requestId <= 0) {
            throw new BusinessException("Could not determine which reschedule request this notification refers to.");
        }

        return requestId;
    }

    /** The slot the requester asked for, plus whether it has been decided already. */
    private CabRescheduleRequest loadRescheduleRequest(Long requestId) {

        List<CabRescheduleRequest> rows = jdbcTemplateTwo.query(
                "SELECT Crq_No, Requested_Start, Requested_End, Result_Status "
                        + "FROM CRQ_CAB_RESCHEDULE_REQUEST_TBL "
                        + "WHERE Request_Id = ? LIMIT 1",
                (rs, rowNum) -> new CabRescheduleRequest(
                        rs.getString("Crq_No"),
                        rs.getTimestamp("Requested_Start"),
                        rs.getTimestamp("Requested_End"),
                        rs.getString("Result_Status")
                ),
                requestId
        );

        if (rows.isEmpty() || rows.get(0).crqNo() == null) {
            throw new BusinessException("Reschedule request not found.");
        }

        return rows.get(0);
    }


}
