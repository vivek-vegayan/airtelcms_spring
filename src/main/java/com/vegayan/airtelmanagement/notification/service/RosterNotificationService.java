package com.vegayan.airtelmanagement.notification.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.notification.dto.CabRejectReasonDto;
import com.vegayan.airtelmanagement.notification.dto.NotificationCountDto;
import com.vegayan.airtelmanagement.notification.dto.UnreadNotifiactionsDto;
import org.springframework.stereotype.Service;

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

    //CAB reschedule request approved/rejected from the notification inbox
    public ApiResponse cabRescheduleNotificationAction(Long actorUserId, Long notificationId, String status, String comment) {
        String sql = "CALL sp_decide_crq_cab_reschedule_req(?,?,?,?)";
        LOGGER.info("call sp_decide_crq_cab_reschedule_req('{}','{}','{}','{}');", actorUserId, notificationId, status, comment);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                notificationId,
                status,
                comment
        );
    }


}
