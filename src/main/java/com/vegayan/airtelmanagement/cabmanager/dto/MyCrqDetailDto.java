package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

/**
 * Row-detail payload behind GET /cab/crqs/mine/{serviceApprovalId}, mapped from
 * sp_get_cab_my_crq_by_id.
 *
 * Deliberately narrower than {@link CrqDto}: the proc was rewritten to key off
 * CRQ_CAB_SERVICE_TBL.Id (the same Service_Approval_Id the My CRQs list proc
 * emits) and dropped the approver / impact / service-approval-status columns,
 * so those are no longer part of this contract rather than being silently null
 * on every response. The list row still carries Service_Approval_Status for the
 * drawer header.
 *
 * serviceApprovalId is echoed back from the request path — the proc does not
 * select it — so the client can round-trip the row it asked for.
 */
@Data
public class MyCrqDetailDto {
    private Long serviceApprovalId;
    private String crqNo;
    private String planId;
    private String domainName;
    private String circleCode;
    private String currentStage;
    private String serviceCode;
    private String stageStatus;
    /** ROUND(...,2) in the proc — kept fractional rather than truncated to an int. */
    private Double slaPercentage;
}
