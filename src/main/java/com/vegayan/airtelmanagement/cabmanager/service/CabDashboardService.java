package com.vegayan.airtelmanagement.cabmanager.service;

import com.vegayan.airtelmanagement.cabmanager.dto.CrqEscalationDto;
import com.vegayan.airtelmanagement.cabmanager.dto.DashboardDataDto;
import com.vegayan.airtelmanagement.cabmanager.dto.DashboardKpiDto;
import com.vegayan.airtelmanagement.cabmanager.dto.StageBarDto;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CabDashboardService extends BaseService {

    public DashboardDataDto getDashboard(Long actorUserId) {

        LOGGER.info("call sp_get_cab_dashboard_kpis('{}');", actorUserId);
        List<DashboardKpiDto> kpis = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_dashboard_kpis(?)",
                DashboardKpiDto.class,
                actorUserId
        );

        LOGGER.info("call sp_get_cab_dashboard_stage_bars('{}');", actorUserId);
        List<StageBarDto> stageBars = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_dashboard_stage_bars(?)",
                StageBarDto.class,
                actorUserId
        );

        LOGGER.info("call sp_get_cab_dashboard_escalations('{}');", actorUserId);
        List<CrqEscalationDto> escalations = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_dashboard_escalations(?)",
                CrqEscalationDto.class,
                actorUserId
        );

        DashboardDataDto response = new DashboardDataDto();
        response.setKpis(kpis);
        response.setStageBars(stageBars);
        response.setEscalations(escalations);
        return response;
    }
}
