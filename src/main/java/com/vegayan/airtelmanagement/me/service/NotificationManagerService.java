package com.vegayan.airtelmanagement.me.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.me.dto.NotificationManagerDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationManagerService extends BaseService {

    public List<NotificationManagerDto> getNotificationManager() {
        String sql = "CALL sp_get_notification_manager()";
        LOGGER.info("call sp_get_notification_manager();");
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, NotificationManagerDto.class);
    }


    public ApiResponse addNotificationManager(Long actorUserId, NotificationManagerDto dto) {

        String sql = "CALL sp_add_notification_manager(?,?,?,?,?,?,?,?,?,?)";

        LOGGER.info("call sp_add_notification_manager('{}','{}','{}','{}');",
                actorUserId, dto.getModuleCode(), dto.getSubModuleCode(), dto.getActionCode());

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                dto.getModuleCode(),
                dto.getSubModuleCode(),
                dto.getActionCode(),
                dto.getNotifySuperAdmin(),
                dto.getNotifyVerticalHead(),
                dto.getNotifyFunctionHead(),
                dto.getNotifyDomainHead(),
                dto.getNotifySubDomainHead(),
                dto.getNotifyTeamMember()
        );
    }

    public ApiResponse updateNotificationManager(Long actorUserId,
                                                 Integer configId,
                                                 String columnName,
                                                 Boolean newValue) {

        String sql = "CALL sp_update_notification_manager(?,?,?,?)";

        LOGGER.info("call sp_update_notification_manager('{}','{}','{}','{}');",
                actorUserId, configId, columnName, newValue);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                configId,
                columnName,
                newValue
        );
    }

    public ApiResponse deleteNotificationManager(Long actorUserId, Integer configId) {

        String sql = "CALL sp_delete_notification_manager(?,?)";

        LOGGER.info("call sp_delete_notification_manager('{}','{}');",
                actorUserId, configId);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                configId
        );
    }

}
