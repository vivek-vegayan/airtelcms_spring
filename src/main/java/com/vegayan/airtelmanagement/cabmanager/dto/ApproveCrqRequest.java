package com.vegayan.airtelmanagement.cabmanager.dto;

/**
 * Body of POST /cab/crqs/{serviceApprovalId}/approve.
 *
 * The SPOC trio maps to sp_approve_cab_crq's p_spoc_name / p_spoc_mob_no /
 * p_spoc_email and is mandatory: an approval assigns the SPOC who owns the
 * change, so the controller rejects a blank one rather than letting the proc
 * record NULL contacts. Only {@code comment} stays optional.
 */
public record ApproveCrqRequest(
        String comment,
        String spocName,
        String spocMobNo,
        String spocEmail
) {
}
