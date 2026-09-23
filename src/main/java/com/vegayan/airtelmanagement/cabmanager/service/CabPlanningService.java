package com.vegayan.airtelmanagement.cabmanager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.cabmanager.dto.CabPlanDateDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabPlanDateRowDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabQueueRowDto;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class CabPlanningService extends BaseService {

    @Autowired
    private ObjectMapper objectMapper;

    public List<CabQueueRowDto> getCabQueue(String domainId,String subDomainId) {

        String sql = "CALL sp_get_cab_queue(?,?)";
        LOGGER.info("call sp_get_cab_queue('{}','{}');", domainId,subDomainId);

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                CabQueueRowDto.class,
                domainId,subDomainId
        );
    }

    public List<CabPlanDateDto> getCabPlanDates() {

        String sql = "CALL sp_get_cab_plan_dates()";

        LOGGER.info("call sp_get_cab_plan_dates();");

        List<CabPlanDateRowDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                CabPlanDateRowDto.class
        );

        return rows.stream().map(row -> {
            CabPlanDateDto dto = new CabPlanDateDto();
            dto.setDate(row.getDate());
            dto.setDayName(row.getDayName());
            dto.setDayNum(row.getDayNum());
            dto.setMonthName(row.getMonthName());
            dto.setSessionId(row.getSessionId());
            dto.setType(row.getType());

            List<String> crqIds = List.of();
            if (row.getCrqIds() != null && !row.getCrqIds().isBlank()) {
                try {
                    crqIds = objectMapper.readValue(row.getCrqIds(), new TypeReference<List<String>>() {});
                } catch (Exception e) {
                    LOGGER.error("Failed to parse crqIds JSON for session {}", row.getSessionId(), e);
                }
            }
            dto.setCrqIds(crqIds);

            return dto;
        }).toList();
    }
}
