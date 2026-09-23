package com.vegayan.airtelmanagement.schedular.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.schedular.dto.TaskConfigDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskConfigService extends BaseService {

    public List<TaskConfigDto> getTaskConfig(Integer domainId, Integer subDomainId) {

        String sql = "CALL sp_get_employee_vs_status_data(?,?)";

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                TaskConfigDto.class,
                domainId,
                subDomainId
        );
    }


    public ApiResponse updateTaskConfig(Long actorUserId, Long affectedUserId, String colName, String newValue) {
        String sql = "CALL sp_update_task_config (?,?,?,?)";
        LOGGER.info("call sp_update_task_config ('{}','{}','{}','{}');",  actorUserId, affectedUserId,colName,newValue);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                affectedUserId,
                colName,
                newValue

        );
    }
}