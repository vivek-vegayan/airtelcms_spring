package com.vegayan.airtelmanagement.cabmanager.service;

import com.vegayan.airtelmanagement.cabmanager.dto.CrqDto;
import com.vegayan.airtelmanagement.cabmanager.dto.ImplementationDetailDto;
import com.vegayan.airtelmanagement.cabmanager.dto.NocInfoDto;
import com.vegayan.airtelmanagement.cabmanager.dto.SeRingDto;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CabImplementationService extends BaseService {

    public ImplementationDetailDto getImplementation() {

        LOGGER.info("call sp_get_cab_implementation_crq();");
        List<CrqDto> crqRows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_implementation_crq()",
                CrqDto.class
        );

        if (crqRows.isEmpty()) {
            throw new BusinessException("Implementation CRQ not found");
        }

        LOGGER.info("call sp_get_cab_implementation_noc();");
        List<NocInfoDto> nocRows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_implementation_noc()",
                NocInfoDto.class
        );
        NocInfoDto noc = nocRows.isEmpty() ? new NocInfoDto() : nocRows.get(0);

        LOGGER.info("call sp_get_cab_implementation_rings();");
        List<SeRingDto> rings = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_implementation_rings()",
                SeRingDto.class
        );

        ImplementationDetailDto detail = new ImplementationDetailDto();
        detail.setCrq(crqRows.get(0));
        detail.setNoc(noc);
        detail.setRings(rings);
        return detail;
    }

    public ApiResponse proceedRing(String crqId, String ringId, Long actorUserId) {
        LOGGER.info("call sp_proceed_cab_ring('{}','{}','{}');", crqId, ringId, actorUserId);
        String sql = "CALL sp_proceed_cab_ring(?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, crqId, ringId, actorUserId);
    }

    public ApiResponse blockRing(String crqId, String ringId, String comment, Long actorUserId) {
        LOGGER.info("call sp_block_cab_ring('{}','{}','{}','{}');", crqId, ringId, comment, actorUserId);
        String sql = "CALL sp_block_cab_ring(?,?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, crqId, ringId, comment, actorUserId);
    }
}
