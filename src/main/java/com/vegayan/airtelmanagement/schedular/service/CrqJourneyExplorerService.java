package com.vegayan.airtelmanagement.schedular.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.schedular.dto.CrqApproverLevelDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqDetailsInfoDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqDetailsResponseDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqDetailsStageDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqJourneyPageDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqJourneyScopeDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqJourneySearchRowDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqJourneyStageStatusDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqPendingApprovalDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqServiceSpocDto;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CrqJourneyExplorerService extends BaseService {

    public List<CrqJourneySearchRowDto> getCrqsBySubDomain(Long actorUserId, Long domainId, Long subDomainId) {
        LOGGER.info("call GetCRQBySubDomainId('{}','{}','{}');", actorUserId, domainId, subDomainId);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL GetCRQBySubDomainId(?,?,?)",
                CrqJourneySearchRowDto.class,
                actorUserId, domainId, subDomainId);
    }

    public CrqJourneyPageDto getCrqJourneyDetails(String crqNo) {
        LOGGER.info("call sp_get_crq_journey_page('{}');", crqNo);
        List<List<Map<String, Object>>> resultSets = databaseUtils.executeProcedureAllResultSets(
                jdbcTemplateTwo,
                "CALL sp_get_crq_journey_page(?)",
                crqNo);

        List<Map<String, Object>> stageRows   = resultSetWithColumn(resultSets, "STAGE");
        List<Map<String, Object>> pendingRows = resultSetWithColumn(resultSets, "Pending_Service_Code");
        List<Map<String, Object>> spocRows    = resultSetWithColumn(resultSets, "Spoc_Name");
        List<Map<String, Object>> scopeRows   = resultSetWithColumn(resultSets, "domain_name");

        List<CrqJourneyStageStatusDto> stages = stageRows.stream().map(this::toStageStatusDto).toList();
        List<CrqPendingApprovalDto> pending = pendingRows.stream().map(this::toPendingApprovalDto).toList();
        List<CrqServiceSpocDto> spocs = spocRows.stream().map(this::toServiceSpocDto).toList();
        CrqJourneyScopeDto scope = scopeRows.isEmpty() ? null : toScopeDto(scopeRows.get(0));

        return new CrqJourneyPageDto(stages, pending, spocs, scope);
    }


    private static List<Map<String, Object>> resultSetWithColumn(
            List<List<Map<String, Object>>> all,
            String column
    ) {
        return all.stream()
                .filter(rows -> !rows.isEmpty() && rows.get(0).containsKey(column))
                .findFirst()
                .orElseGet(List::of);
    }

    private CrqJourneyStageStatusDto toStageStatusDto(Map<String, Object> row) {
        CrqJourneyStageStatusDto dto = new CrqJourneyStageStatusDto();
        dto.setStage(str(row, "STAGE"));
        dto.setStatus(str(row, "STATUS"));
        return dto;
    }

    private CrqPendingApprovalDto toPendingApprovalDto(Map<String, Object> row) {
        CrqPendingApprovalDto dto = new CrqPendingApprovalDto();
        dto.setServiceCode(str(row, "Pending_Service_Code"));
        dto.setStatus(str(row, "Status"));
        dto.setL1(approverLevel(row, "L1"));
        dto.setL2(approverLevel(row, "L2"));
        dto.setL3(approverLevel(row, "L3"));
        return dto;
    }

    private static CrqApproverLevelDto approverLevel(Map<String, Object> row, String level) {
        String olmId = str(row, level + "_Approver_Olm_Id");
        String name = str(row, level + "_Approver_Name");

        if (olmId == null && name == null && "L1".equals(level)) {
            olmId = str(row, "Approver_Olm_Id");
            name = str(row, "Approver_Name");
        }

        CrqApproverLevelDto dto = new CrqApproverLevelDto();
        dto.setLevel(level);
        dto.setOlmId(olmId);
        dto.setName(name);
        dto.setEscalated(str(row, level + "_Escalated_Remark") != null);
        return dto;
    }

    private CrqServiceSpocDto toServiceSpocDto(Map<String, Object> row) {
        CrqServiceSpocDto dto = new CrqServiceSpocDto();
        dto.setServiceCode(str(row, "Service_Code"));
        dto.setSpocName(str(row, "Spoc_Name"));
        dto.setSpocContact(str(row, "Spoc_Contact"));
        return dto;
    }

    private CrqJourneyScopeDto toScopeDto(Map<String, Object> row) {
        CrqJourneyScopeDto dto = new CrqJourneyScopeDto();
        dto.setDomainName(str(row, "domain_name"));
        dto.setSubDomainName(str(row, "sub_domain_name"));
        return dto;
    }


    public CrqDetailsResponseDto getCrqDetails(String crqNo) {
        LOGGER.info("call get_crq_details('{}');", crqNo);
        List<List<Map<String, Object>>> resultSets = databaseUtils.executeProcedureAllResultSets(
                jdbcTemplateTwo,
                "CALL get_crq_details(?)",
                crqNo);

        List<Map<String, Object>> infoRows = resultSets.size() > 0 ? resultSets.get(0) : List.of();
        List<Map<String, Object>> stageRows = resultSets.size() > 1 ? resultSets.get(1) : List.of();

        CrqDetailsInfoDto info = infoRows.isEmpty() ? null : toInfoDto(infoRows.get(0));
        List<CrqDetailsStageDto> stages = stageRows.stream().map(this::toStageDto).toList();

        return new CrqDetailsResponseDto(info, stages);
    }

    private CrqDetailsInfoDto toInfoDto(Map<String, Object> row) {
        CrqDetailsInfoDto dto = new CrqDetailsInfoDto();
        dto.setCrqNo(str(row, "CRQ_No"));
        dto.setCurrentStage(str(row, "Current_Stage"));
        dto.setCurrentStatus(str(row, "Current_Status"));
        dto.setTeamFunction(str(row, "Team_Function"));
        dto.setTeamSubFunction(str(row, "Team_Sub_Function"));
        dto.setCreatedDate(dateTime(row, "Created_Date"));
        dto.setRemark(str(row, "Remark"));
        return dto;
    }

    private CrqDetailsStageDto toStageDto(Map<String, Object> row) {
        CrqDetailsStageDto dto = new CrqDetailsStageDto();
        dto.setStage(str(row, "Stage"));
        dto.setStageStatus(str(row, "Stage_Status"));
        dto.setIsCurrent(Boolean.TRUE.equals(row.get("Is_Current")) || "1".equals(str(row, "Is_Current")));
        dto.setAssignedTo(str(row, "Assigned_To"));
        dto.setPerformedBy(str(row, "Performed_By"));
        dto.setAssignStart(dateTime(row, "Assign_Start"));
        dto.setAssignEnd(dateTime(row, "Assign_End"));
        dto.setStageStartDate(dateTime(row, "Stage_Start_Date"));
        dto.setStageEndDate(dateTime(row, "Stage_End_Date"));
        return dto;
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }


    private static LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.sql.Date date) return date.toLocalDate().atStartOfDay();
        return null;
    }
}
