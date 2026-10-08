package com.vegayan.airtelmanagement.activity.service;

import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelPhaseDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelRowDto;

public final class PlanActivityExcelMapper {

    private PlanActivityExcelMapper() {
    }

    /** Parameters for one sp_insert_plan_activity call (one phase), in procedure order. */
    public static Object[] toSqlParams(Long actorUserId, PlanActivityExcelRowDto activity,
                                       PlanActivityExcelPhaseDto phase, String teamName) {
        return new Object[]{
                actorUserId,

                // Plan / Activity details
                activity.getVerticalName(),
                activity.getFunctionName(),
                activity.getChmDomainName(),
                activity.getChmSubDomainName(),
                activity.getNetworkDomain(),
                activity.getLayer(),
                activity.getPlanType(),
                activity.getVendorOem(),
                activity.getChangeImpact(),
                activity.getActivityName(),

                // Phase details
                phase.getPhase(),
                phase.getShift(),
                phase.getMinimumLevelRequirement(),
                phase.getRequiredTimeMinutes(),
                teamName,

                // Execution details (procedure nulls these for non-Execution phases)
                phase.getDaysMargin(),
                phase.getReservationMargin(),
                phase.getRollbackTime()
        };
    }
}
