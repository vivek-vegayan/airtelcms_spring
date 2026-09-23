package com.vegayan.airtelmanagement.schedular.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.schedular.dto.CrqValidationDetailsDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqValidationSaveRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CrqValidationService extends BaseService {

    public CrqValidationDetailsDto getValidationDetails(String crqNo) {
        String trimmedCrqNo = trimToNull(crqNo);
        if (trimmedCrqNo == null) {
            throw new BusinessException("CRQ Number is required.");
        }

        LOGGER.info("call get_crq_validation_details('{}');", trimmedCrqNo);
        List<CrqValidationDetailsDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL get_crq_validation_details(?)",
                CrqValidationDetailsDto.class,
                trimmedCrqNo);

        if (rows.isEmpty()) {
            throw new BusinessException("No validation details found for CRQ " + trimmedCrqNo + ".");
        }
        return rows.get(0);
    }

    public CrqValidationDetailsDto saveValidationDetails(CrqValidationSaveRequest request) {
        if (request == null) {
            throw new BusinessException("Validation details are required.");
        }

        String crqNo = require(request.crqNo(), "CRQ Number");
        String nodeName = trimToNull(request.nodeName());
        String interfacePair = trimToNull(request.nameInterfacePair());

        LOGGER.info("call update_validation_details('{}', '{}', '{}');", crqNo, nodeName, interfacePair);
        databaseUtils.executeProcedureWithError(
                jdbcTemplateTwo,
                "CALL update_validation_details(?, ?, ?)",
                crqNo, nodeName, interfacePair);

        return getValidationDetails(crqNo);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String require(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(label + " is required.");
        }
        return trimmed;
    }
}
