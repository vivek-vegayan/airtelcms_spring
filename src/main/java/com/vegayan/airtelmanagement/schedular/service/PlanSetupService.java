package com.vegayan.airtelmanagement.schedular.service;


import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.dto.ProcedurePageResult;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.PaginationUtils;
import com.vegayan.airtelmanagement.schedular.dto.PlanDetailsDto;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;

@Service
public class PlanSetupService extends BaseService {

    public PageResponseDto<PlanDetailsDto> getPlanDetails(
            Long actorUserId, Long verticalId, Long functionId, Long domainId, Long subDomainId,
            String statusFilter, Pageable pageable) {

        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        LOGGER.info("call sp_get_plan_details('{}','{}','{}','{}','{}','{}','{}','{}');",
                actorUserId, verticalId, functionId, domainId, subDomainId, statusFilter, offset, limit);

        ProcedurePageResult<PlanDetailsDto> result = jdbcTemplateTwo.execute(
                (CallableStatementCreator) con -> {
                    CallableStatement cs = con.prepareCall("{CALL sp_get_plan_details(?,?,?,?,?,?,?,?)}");
                    cs.setLong(1, actorUserId);
                    cs.setLong(2, verticalId);
                    cs.setLong(3, functionId);
                    cs.setLong(4, domainId);
                    cs.setLong(5, subDomainId);
                    cs.setString(6, statusFilter);
                    cs.setInt(7, offset);
                    cs.setInt(8, limit);
                    return cs;
                },
                (CallableStatementCallback<ProcedurePageResult<PlanDetailsDto>>) cs ->
                        databaseUtils.extractMultiPagedResult(cs, new BeanPropertyRowMapper<>(PlanDetailsDto.class))
        );

        assert result != null;
        return PaginationUtils.buildPageResponse(result.getData(), pageable, result.getTotalCount());
    }

}
