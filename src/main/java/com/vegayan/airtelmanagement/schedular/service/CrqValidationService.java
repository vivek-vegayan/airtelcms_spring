package com.vegayan.airtelmanagement.schedular.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.schedular.dto.CrqValidationDetailsDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqValidationSaveRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backs the Plan &amp; Inventory "Validate" dialog over the two procedures in
 * db/migration/2026-07-28_crq_validation_details.sql.
 *
 * Both procedures follow this codebase's error convention - a single
 * `error_message` column on a guard failure - so DatabaseUtils raises a
 * DatabaseOperationException carrying the procedure's own message and
 * GlobalExceptionHandler renders it as ApiResponse{status,message}.
 *
 * Neither editable field is length-capped: both columns are TEXT and the
 * procedure stores whatever is sent, so a CRQ can carry as many nodes and
 * interfaces as it actually touches. Only CRQ Number is still required, and
 * the procedure re-checks that server-side.
 */
@Service
public class CrqValidationService extends BaseService {

    /** Read-only load for the dialog. Returns exactly one row for a known CRQ. */
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

    /**
     * Saves the two editable attributes and returns the refreshed row, so the
     * dialog can re-render from authoritative (trimmed, upserted) DB values
     * without a second round trip.
     */
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

    /** Kept for callers that only need the outcome, not the refreshed row. */
    public ApiResponse saveAndAcknowledge(CrqValidationSaveRequest request) {
        saveValidationDetails(request);
        return new ApiResponse("Success", "Validation details saved successfully.");
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
