package com.vegayan.airtelmanagement.cabmanager.service;

import com.vegayan.airtelmanagement.cabmanager.dto.ApprovalChainStepDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabServiceDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CrqConflictDetailDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CrqDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CrqJourneyDto;
import com.vegayan.airtelmanagement.cabmanager.dto.JourneyMetaDto;
import com.vegayan.airtelmanagement.cabmanager.dto.JourneyRemarkDto;
import com.vegayan.airtelmanagement.cabmanager.dto.MyCrqDetailDto;
import com.vegayan.airtelmanagement.cabmanager.dto.MyCrqsMetaDto;
import com.vegayan.airtelmanagement.cabmanager.dto.MyCrqsResponseDto;
import com.vegayan.airtelmanagement.cabmanager.dto.MyCrqsStatsDto;
import com.vegayan.airtelmanagement.cabmanager.dto.NewCrqRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.ParallelTrackDto;
import com.vegayan.airtelmanagement.cabmanager.dto.SpocFeDetailsDto;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.notification.dto.CabRejectReasonDto;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CabCrqService extends BaseService {

    // ── ALL CRQs ────────────────────────────────────────────────────────────

    public List<CrqDto> getAllCrqs(String stage, String domain, String circle, String impact, String serviceCode, String search) {

        String sql = "CALL sp_get_cab_crqs(?,?,?,?,?,?)";

        LOGGER.info(
                "call sp_get_cab_crqs('{}','{}','{}','{}','{}','{}');",
                stage, domain, circle, impact, serviceCode, search
        );

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                CrqDto.class,
                stage, domain, circle, impact, serviceCode, search
        );
    }


    public List<CabServiceDto> getCabServices() {
        String sql = "CALL sp_crq_cab_services_get_dropdown()";
        LOGGER.info("call sp_crq_cab_services_get_dropdown();");
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, CabServiceDto.class);
    }

    public CrqDto getAllCrqById(Long serviceApprovalId) {

        String sql = "CALL sp_get_cab_crq_by_id(?)";

        LOGGER.info("call sp_get_cab_crq_by_id('{}');", serviceApprovalId);

        List<CrqDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                CrqDto.class,
                serviceApprovalId
        );

        if (rows.isEmpty()) {
            throw new BusinessException("CRQ not found: " + serviceApprovalId);
        }

        return rows.get(0);
    }

    public MyCrqDetailDto getMyCrqById(Long serviceApprovalId) {

        String sql = "CALL sp_get_cab_my_crq_by_id(?)";

        LOGGER.info("call sp_get_cab_my_crq_by_id('{}');", serviceApprovalId);

        List<MyCrqDetailDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                MyCrqDetailDto.class,
                serviceApprovalId
        );

        if (rows.isEmpty()) {
            throw new BusinessException("CRQ not found for service approval id: " + serviceApprovalId);
        }

        MyCrqDetailDto row = rows.get(0);
        // The proc does not select s.Id; echo it back so the client keeps the key
        // it looked the row up with.
        row.setServiceApprovalId(serviceApprovalId);
        return row;
    }

    // ── MY CRQs ─────────────────────────────────────────────────────────────

    public MyCrqsResponseDto getMyCrqs(Long actorUserId, String role) {
        LOGGER.info("call sp_get_my_crqs_stats('{}','{}');", actorUserId, role);
        List<MyCrqsStatsDto> statsRows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_my_crqs_stats(?,?)",
                MyCrqsStatsDto.class,
                actorUserId, role
        );
        MyCrqsStatsDto stats = statsRows.isEmpty() ? new MyCrqsStatsDto() : statsRows.get(0);

        LOGGER.info("call sp_get_my_crqs_rows('{}');", actorUserId);
        List<CrqDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_my_crqs_rows(?)",
                CrqDto.class,
                actorUserId
        );

        MyCrqsResponseDto response = new MyCrqsResponseDto();
        response.setStats(stats);
        response.setRows(rows);
        return response;
    }

    // ── WORKFLOW ACTIONS ────────────────────────────────────────────────────


    public List<CabRejectReasonDto> getCabRejectReasons() {
        String sql = "CALL sp_get_cab_reject_reasons()";
        LOGGER.info("call sp_get_cab_reject_reasons();");
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, CabRejectReasonDto.class);
    }

    public ApiResponse saveCabRejectReason(Integer reasonId, String reasonText) {
        LOGGER.info("call sp_get_cab_reject_reasons_update_and_insert('{}','{}');", reasonId, reasonText);
        String sql = "CALL sp_get_cab_reject_reasons_update_and_insert(?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, reasonId, reasonText);
    }

    public ApiResponse deleteCabRejectReason(Integer reasonId) {
        LOGGER.info("call sp_get_cab_reject_reasons_delete('{}');", reasonId);
        String sql = "CALL sp_get_cab_reject_reasons_delete(?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, reasonId);
    }

    public ApiResponse approveCrq(Long serviceApprovalId, String comment, Long actorUserId,
                                  String spocName, String spocMobNo, String spocEmail) {
        LOGGER.info(
                "call sp_approve_cab_crq('{}','{}','{}','{}','{}','{}');",
                serviceApprovalId, comment, actorUserId, spocName, spocMobNo, spocEmail
        );
        String sql = "CALL sp_approve_cab_crq(?,?,?,?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, serviceApprovalId, comment, actorUserId, spocName, spocMobNo, spocEmail);
    }

    public ApiResponse rejectCrq(Long serviceApprovalId, Integer reasonId, String comment, Long actorUserId) {
        LOGGER.info("call sp_reject_cab_crq('{}','{}','{}','{}');", serviceApprovalId, actorUserId, reasonId, comment);
        String sql = "CALL sp_reject_cab_crq(?,?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, serviceApprovalId, actorUserId, reasonId, comment);
    }

    public ApiResponse delegateCrq(Long serviceApprovalId, String delegateTo, String reason, Long actorUserId) {
        LOGGER.info("call sp_delegate_cab_crq('{}','{}','{}','{}');", serviceApprovalId, delegateTo, reason, actorUserId);
        String sql = "CALL sp_delegate_cab_crq(?,?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, serviceApprovalId, delegateTo, reason, actorUserId);
    }

    public ApiResponse rescheduleCrq(Long serviceApprovalId, String newDate, String newWindow, String reason, Long actorUserId) {
        LOGGER.info(
                "call sp_reschedule_cab_crq('{}','{}','{}','{}','{}');",
                serviceApprovalId, newDate, newWindow, reason, actorUserId
        );
        String sql = "CALL sp_reschedule_cab_crq(?,?,?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, serviceApprovalId, newDate, newWindow, reason, actorUserId);
    }

    public ApiResponse approveCabRescheduleRequest(String crqNo, LocalDateTime slotStart, LocalDateTime slotEnd, Long actorUserId) {
        LOGGER.info(
                "call sp_approve_crq_cab_reschedule_req('{}','{}','{}','{}');",
                actorUserId, crqNo, slotStart, slotEnd
        );
        String sql = "CALL sp_approve_crq_cab_reschedule_req(?,?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, actorUserId, crqNo, slotStart, slotEnd);
    }

    public ApiResponse assignSpoc(String crqId, String spocOlmId, Long actorUserId) {
        LOGGER.info("call sp_assign_cab_crq_spoc('{}','{}','{}');", crqId, spocOlmId, actorUserId);
        String sql = "CALL sp_assign_cab_crq_spoc(?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, crqId, spocOlmId, actorUserId);
    }

    public ApiResponse assignFe(String crqId, String fieldEngineerOlmId, Long actorUserId) {
        LOGGER.info("call sp_assign_cab_crq_fe('{}','{}','{}');", crqId, fieldEngineerOlmId, actorUserId);
        String sql = "CALL sp_assign_cab_crq_fe(?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, crqId, fieldEngineerOlmId, actorUserId);
    }

    // ── SPOC / FIELD ENGINEER DETAILS ───────────────────────────────────────


    public SpocFeDetailsDto getSpocFeDetails(String crqNo) {
        String sql = "CALL sp_get_SPOC_FE_details(?)";
        LOGGER.info("call sp_get_SPOC_FE_details('{}');", crqNo);
        List<SpocFeDetailsDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, SpocFeDetailsDto.class, crqNo);
        return (rows == null || rows.isEmpty()) ? null : rows.get(0);
    }

    // ── CONFLICT CHECK ──────────────────────────────────────────────────────

    public List<CrqConflictDetailDto> getCrqConflicts(String crqNo) {
        String sql = "CALL sp_crq_cab_get_conflict_check_details(?)";
        LOGGER.info("call sp_crq_cab_get_conflict_check_details('{}');", crqNo);
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, CrqConflictDetailDto.class, crqNo);
    }

    public ApiResponse saveCrqConflictDecision(String crqNo, String flag, Long actorUserId) {
        LOGGER.info("call crq_conflict_check('{}','{}','{}');", crqNo, actorUserId, flag);
        String sql = "CALL crq_conflict_check(?,?,?)";
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, crqNo, actorUserId, flag);
    }

}
