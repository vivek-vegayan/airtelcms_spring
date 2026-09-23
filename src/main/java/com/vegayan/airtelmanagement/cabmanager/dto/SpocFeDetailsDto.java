package com.vegayan.airtelmanagement.cabmanager.dto;

/**
 * One row of sp_get_SPOC_FE_details(crqNo).
 *
 * Only Crq_No is guaranteed: the proc routinely returns a SPOC with a NULL
 * email, or a SPOC with no Field Engineer assigned yet, so every other
 * component is independently nullable.
 *
 * Column mapping is by DataClassRowMapper (records) inside
 * DatabaseUtils.executeProcedureGetDataWithError, which matches case- and
 * underscore-insensitively - Spoc_Olm_Id -> spocOlmId, Fe_Olm_Id -> feOlmId.
 * These component names are also the JSON keys the React client reads, so keep
 * them in step with SpocFeDetails in
 * CHM_airtel_beta/src/features/cabManager/types/types.ts.
 */
public record SpocFeDetailsDto(
        String crqNo,
        String spocOlmId,
        String spocName,
        String spocNumber,
        String spocEmail,
        String feOlmId,
        String feName,
        String feNumber,
        String feEmail
) {
}
