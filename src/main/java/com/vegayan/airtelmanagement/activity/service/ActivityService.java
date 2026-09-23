package com.vegayan.airtelmanagement.activity.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.activity.dto.*;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.usermanagement.dto.InsertPlanDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ActivityService extends BaseService {

    @Autowired
    private ObjectMapper objectMapper;

    public List<ActivityDTO> getActivityDetails(Long userId, Integer type) {
        String sql = "CALL sp_get_activity_details(?,?)";
        LOGGER.info("call sp_get_activity_details('{}','{}');", userId, type);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                ActivityDTO.class,
                userId,
                type
        );
    }
    public ActivityPhaseViewDTO getActivityPhaseView(Long userId, Long planId) {

        String sql = "CALL sp_get_activity_phase_view(?,?)";
        LOGGER.info("call sp_get_activity_phase_view('{}','{}');",userId,planId);
        
        List<ActivityPhaseDBRow> rows =
                databaseUtils.executeProcedureAndFetchObjectsV1(
                        jdbcTemplateTwo,
                        sql,
                        ActivityPhaseDBRow.class,
                        userId,
                        planId
                );

        ActivityPhaseViewDTO dto = new ActivityPhaseViewDTO();

        Map<String, ActivityPhaseViewDTO.ActivityEntry> activityMap = new LinkedHashMap<>();

        ActivityPhaseViewDTO.BasicInfo basicInfo = new ActivityPhaseViewDTO.BasicInfo();
        boolean basicSet = false;

        for (ActivityPhaseDBRow row : rows) {

            // ---------------- BASIC INFO (SET ONCE) ----------------
            if (!basicSet) {
                basicInfo.setChmDomain(row.getChmDomain());
                basicInfo.setChmSubDomain(row.getChmSubDomain());
                basicInfo.setDomain(row.getDomain());
                basicInfo.setLayer(row.getLayer());
                basicInfo.setPlanType(row.getPlanType());
                basicInfo.setVendorOem(row.getVendorOem());
                basicInfo.setChangeImpact(row.getChangeImpact());

                dto.setBasicInfo(basicInfo);
                basicSet = true;
            }

            // ---------------- GET / CREATE ACTIVITY ----------------
            ActivityPhaseViewDTO.ActivityEntry activity =
                    activityMap.computeIfAbsent(row.getActivityId(), id -> {
                        ActivityPhaseViewDTO.ActivityEntry a =
                                new ActivityPhaseViewDTO.ActivityEntry();
                        a.setActivityId(id);
                        a.setActivityName(row.getActivityName());
                        a.setPhases(new LinkedHashMap<>());
                        a.setExecution(null);
                        return a;
                    });

            String phaseKey = mapPhaseKey(row.getPhaseName());
            if (phaseKey == null) continue;

            // ---------------- EXECUTION PHASE ----------------
            if ("execution".equalsIgnoreCase(phaseKey)) {

                ActivityPhaseViewDTO.ExecutionPhase exec = activity.getExecution();

                if (exec == null) {
                    exec = new ActivityPhaseViewDTO.ExecutionPhase();
                    activity.setExecution(exec);
                }

                exec.setShift(row.getShift());
                exec.setMinimumLevelRequirement(row.getMinimumLevelRequirement());
                exec.setAssignTeam(row.getAssignedTeamName() != null ? row.getAssignedTeamName() : "");
                exec.setAssignedSubDomainId(row.getAssignedSubDomainId());
                exec.setTime(row.getRequiredTimeMinutes() != null ? row.getRequiredTimeMinutes() : 0);
                exec.setActivityPhaseConfigId(row.getActivityPhaseConfigId());

                exec.setDaysMargin(row.getDaysMargin());
                exec.setReservationMargin(row.getReservationMargin());
                exec.setRollbackTime(row.getRollbackTime());

            }
            // ---------------- NORMAL PHASES ----------------
            else {

                ActivityPhaseViewDTO.Phase phase = new ActivityPhaseViewDTO.Phase();

                phase.setShift(row.getShift());
                phase.setMinimumLevelRequirement(row.getMinimumLevelRequirement());
                phase.setAssignTeam(row.getAssignedTeamName() != null ? row.getAssignedTeamName() : "");
                phase.setAssignedSubDomainId(row.getAssignedSubDomainId());
                phase.setTime(row.getRequiredTimeMinutes() != null ? row.getRequiredTimeMinutes() : 0);
                phase.setActivityPhaseConfigId(row.getActivityPhaseConfigId());

                activity.getPhases().put(phaseKey, phase);
            }
        }

        dto.setActivities(new ArrayList<>(activityMap.values()));
        return dto;
    }

    private Integer toInt(Object val) {
        if (val == null) return 0;
        return ((Number) val).intValue();
    }

    private String mapPhaseKey(String dbPhase) {

        if (dbPhase == null) return null;

        return switch (dbPhase.toLowerCase()) {
            case "crq review" -> "review";
            case "impact analysis" -> "impactAnalysis";
            case "scheduling" -> "scheduling";
            case "mop creation" -> "mopCreation";
            case "mop validation" -> "mopValidation";
            case "execution" -> "execution";
            default -> dbPhase;
        };
    }

    public ApiResponse insertActivity(
            Long actorUserId,
            ActivityInsertRequestDTO request
    ) {

        String sql = "CALL sp_insert_activity(" +
                "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        LOGGER.info("Calling sp_insert_activity for planId={}, activityName={}",
                request.getPlanId(),
                request.getActivityName());

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,

                actorUserId,
                request.getPlanId(),
                request.getActivityName(),

                // CRQ REVIEW
                request.getCrqReviewShift(),
                request.getCrqReviewMinimumLevelRequirement(),
                request.getCrqReviewRequiredTimeMinutes(),
                request.getCrqReviewAssignedToTeam(),

                // IMPACT ANALYSIS
                request.getImpactAnalysisShift(),
                request.getImpactAnalysisMinimumLevelRequirement(),
                request.getImpactAnalysisRequiredTimeMinutes(),
                request.getImpactAnalysisAssignedToTeam(),

                // SCHEDULING
                request.getSchedulingShift(),
                request.getSchedulingMinimumLevelRequirement(),
                request.getSchedulingRequiredTimeMinutes(),
                request.getSchedulingAssignedToTeam(),

                // MOP CREATE
                request.getMopCreateShift(),
                request.getMopCreateMinimumLevelRequirement(),
                request.getMopCreateRequiredTimeMinutes(),
                request.getMopCreateAssignedToTeam(),

                // MOP VALIDATE
                request.getMopValidateShift(),
                request.getMopValidateMinimumLevelRequirement(),
                request.getMopValidateRequiredTimeMinutes(),
                request.getMopValidateAssignedToTeam(),

                // CRQ EXECUTION
                request.getCrqExecutionShift(),
                request.getCrqExecutionMinimumLevelRequirement(),
                request.getCrqExecutionRequiredTimeMinutes(),
                request.getCrqExecutionDaysMargin(),
                request.getCrqExecutionReservationMargin(),
                request.getCrqExecutionRollbackTime(),
                request.getCrqExecutionAssignedToTeam()
        );
    }


    public ApiResponse insertPlan(Long actorUserId, InsertPlanDto request) {

        String sql = "CALL sp_insert_plan(?,?,?,?,?,?,?,?)";

        LOGGER.info(
                "CALL sp_insert_plan({}, {}, {}, {}, {}, {}, {}, {})",
                actorUserId,
                request.getChmDomain(),
                request.getChmSubDomain(),
                request.getNetworkDomain(),
                request.getLayer(),
                request.getPlanType(),
                request.getVendorOem(),
                request.getChangeImpact()
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                request.getChmDomain(),
                request.getChmSubDomain(),
                request.getNetworkDomain(),
                request.getLayer(),
                request.getPlanType(),
                request.getVendorOem(),
                request.getChangeImpact()
        );
    }

    public ApiResponse updateActivityPhase(Long actorUserId, UpdateActivityPhaseDto request) {

        String sql = "CALL sp_update_activity_phase(?,?,?,?,?,?,?,?,?)";

        LOGGER.info(
                "CALL sp_update_activity_phase({}, {}, {}, {}, {}, {}, {}, {}, {})",
                actorUserId,
                request.getActivityPhaseConfigId(),
                request.getShift(),
                request.getMinimumLevelRequirement(),
                request.getRequiredTimeMinutes(),
                request.getAssignedToTeam(),
                request.getDaysMargin(),
                request.getReservationMargin(),
                request.getRollbackTime()
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                request.getActivityPhaseConfigId(),
                request.getShift(),
                request.getMinimumLevelRequirement(),
                request.getRequiredTimeMinutes(),
                request.getAssignedToTeam(),
                request.getDaysMargin(),
                request.getReservationMargin(),
                request.getRollbackTime()
        );
    }

    public ApiResponse updatePlan(Long actorUserId, UpdatePlanDto request) {

        String sql = "CALL sp_update_plan(?,?,?,?,?,?,?,?,?,?)";

        LOGGER.info(
                "CALL sp_update_plan({}, {}, {}, {}, {}, {}, {}, {}, {}, {})",
                actorUserId,
                request.getPlanId(),
                request.getPlanType(),
                request.getStatus(),
                request.getChmDomainId(),
                request.getChmSubDomain(),
                request.getNetworkDomain(),
                request.getLayer(),
                request.getPlanVendor(),
                request.getChangeImpact()
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                request.getPlanId(),
                request.getPlanType(),
                request.getStatus(),
                request.getChmDomainId(),
                request.getChmSubDomain(),
                request.getNetworkDomain(),
                request.getLayer(),
                request.getPlanVendor(),
                request.getChangeImpact()
        );
    }

    public ApiResponse changePlanStatus(Long actorUserId, Integer planId, String status) {

        String sql = "CALL sp_plan_status_change(?,?,?)";

        LOGGER.info("CALL sp_plan_status_change({}, {}, {})", actorUserId, planId, status);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                planId,
                status
        );
    }

    public ApiResponse changeActivityStatus(Long actorUserId, Integer planId, String activityId, String status) {

        String sql = "CALL sp_activity_status_change(?,?,?,?)";

        LOGGER.info("CALL sp_activity_status_change({}, {}, {}, {})", actorUserId, planId, activityId, status);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                planId,
                activityId,
                status
        );
    }

}