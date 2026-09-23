package com.vegayan.airtelmanagement.activity.service;

import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelRowDto;

public final class PlanActivityExcelMapper {

    private PlanActivityExcelMapper() {
    }

    public static Object[] toSqlParams(Long actorUserId, PlanActivityExcelRowDto row) {
        return new Object[]{
                actorUserId,

                // Organization Hierarchy (names — procedure resolves IDs)
                row.getVerticalName(),
                row.getFunctionName(),
                row.getChmDomainName(),
                row.getChmSubDomainName(),

                // Plan Details
                row.getLayer(),
                row.getPlanType(),
                row.getVendorOem(),
                row.getChangeImpact(),

                // Activity Details
                row.getActivityName(),

                row.getCrqReviewShift(),
                row.getCrqReviewMinimumLevelRequirement(),
                row.getCrqReviewRequiredTimeMinutes(),
                row.getCrqReviewTeamName(),

                row.getImpactAnalysisShift(),
                row.getImpactAnalysisMinimumLevelRequirement(),
                row.getImpactAnalysisRequiredTimeMinutes(),
                row.getImpactAnalysisTeamName(),

                row.getSchedulingShift(),
                row.getSchedulingMinimumLevelRequirement(),
                row.getSchedulingRequiredTimeMinutes(),
                row.getSchedulingTeamName(),

                row.getMopCreateShift(),
                row.getMopCreateMinimumLevelRequirement(),
                row.getMopCreateRequiredTimeMinutes(),
                row.getMopCreateTeamName(),

                row.getMopValidateShift(),
                row.getMopValidateMinimumLevelRequirement(),
                row.getMopValidateRequiredTimeMinutes(),
                row.getMopValidateTeamName(),

                row.getCrqExecutionShift(),
                row.getCrqExecutionMinimumLevelRequirement(),
                row.getCrqExecutionRequiredTimeMinutes(),
                row.getCrqExecutionDaysMargin(),
                row.getCrqExecutionReservationMargin(),
                row.getCrqExecutionRollbackTime(),
                row.getCrqExecutionTeamName()
        };
    }
}
