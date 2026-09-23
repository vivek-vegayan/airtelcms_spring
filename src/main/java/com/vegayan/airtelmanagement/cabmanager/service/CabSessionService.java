package com.vegayan.airtelmanagement.cabmanager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.cabmanager.dto.AddCrqToSessionRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.AddCrqToSessionResultDto;
import com.vegayan.airtelmanagement.cabmanager.dto.AddCrqToSessionRowDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabAgendaRowDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabPlanConflictDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabPlanConflictRowDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabSessionCrqActionRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.CabSessionCrqActionResultDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabSessionDto;
import com.vegayan.airtelmanagement.cabmanager.dto.PlanCabRequest;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Set;

@Service
public class CabSessionService extends BaseService {

    /** What sp_cab_session_crq_action accepts for its action argument. */
    private static final Set<String> ALLOWED_DECISIONS = Set.of("APPROVE", "REJECT", "RESCHEDULE");

    @Autowired
    private ObjectMapper objectMapper;

    public List<CabSessionDto> getCabSessions() {

        String sql = "CALL sp_get_cab_sessions()";

        LOGGER.info("call sp_get_cab_sessions();");

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                CabSessionDto.class
        );
    }

    /**
     * The agenda board for one CAB session - one row per CRQ tabled, carrying
     * the decision recorded against it so far.
     *
     * <p>Every row repeats the session's date and chair, so the board renders its
     * header from this one call. An agenda with nothing on it is an empty list,
     * not an error: a session is planned before its CRQs are added.
     */
    public List<CabAgendaRowDto> getCabSessionAgenda(String sessionId) {

        LOGGER.info("call sp_get_crq_cab_agenda_v2('{}');", sessionId);

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_crq_cab_agenda_v2(?)",
                CabAgendaRowDto.class,
                sessionId
        );
    }

    /**
     * Records the CAB's decision on one tabled CRQ.
     *
     * <p>Keyed on the mapping id rather than the CRQ number: the same CRQ can be
     * tabled again at a later session, and a decision belongs to the sitting that
     * took it.
     */
    public CabSessionCrqActionResultDto recordCrqDecision(
            Long mappingId, CabSessionCrqActionRequest body, Long actorUserId) {

        String action = body.action() == null ? null : body.action().trim().toUpperCase();

        if (action == null || action.isBlank()) {
            throw new BusinessException("Decision is required.");
        }
        if (!ALLOWED_DECISIONS.contains(action)) {
            throw new BusinessException("Unsupported CAB decision: " + body.action());
        }

        String reason = blankToNull(body.reason());
        String comment = blankToNull(body.comment());

        // A change turned away with no stated ground cannot be answered by the
        // circle that raised it, so REJECT and RESCHEDULE must carry one.
        if (reason == null && !"APPROVE".equals(action)) {
            throw new BusinessException("A reason is required to " + action.toLowerCase() + " a CRQ.");
        }

        LOGGER.info(
                "call sp_cab_session_crq_action({},'{}',{},'{}','{}');",
                mappingId, action, actorUserId, reason, comment
        );

        List<CabSessionCrqActionResultDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_cab_session_crq_action(?,?,?,?,?)",
                CabSessionCrqActionResultDto.class,
                mappingId, action, actorUserId, reason, comment
        );

        if (rows.isEmpty()) {
            throw new BusinessException("CRQ not found on this CAB agenda: mapping " + mappingId);
        }

        return rows.get(0);
    }

    /**
     * Pulls further CRQs onto an agenda that is already open.
     *
     * <p>A CRQ the session already carries comes back under skipped_crq_list -
     * a normal outcome, not a failure - so both halves are reported and the
     * caller decides what to say about them.
     */
    public AddCrqToSessionResultDto addCrqsToSession(
            String sessionId, AddCrqToSessionRequest body, Long actorUserId) {

        if (body.crqIds() == null || body.crqIds().isEmpty()) {
            throw new BusinessException("Select at least one CRQ to add.");
        }

        try {
            String crqListJson = objectMapper.writeValueAsString(body.crqIds());

            LOGGER.info(
                    "call sp_add_crq_to_cab_session('{}','{}',{});",
                    sessionId, crqListJson, actorUserId
            );

            List<AddCrqToSessionRowDto> rows = databaseUtils.executeProcedureGetDataWithError(
                    jdbcTemplateTwo,
                    "CALL sp_add_crq_to_cab_session(?,?,?)",
                    AddCrqToSessionRowDto.class,
                    sessionId, crqListJson, actorUserId
            );

            if (rows.isEmpty()) {
                throw new BusinessException("Session not found: " + sessionId);
            }

            AddCrqToSessionRowDto row = rows.get(0);
            return new AddCrqToSessionResultDto(
                    row.getCabId() == null ? sessionId : row.getCabId(),
                    row.getAddedCount() == null ? 0 : row.getAddedCount(),
                    row.getSkippedCount() == null ? 0 : row.getSkippedCount(),
                    parseCrqList(row.getAddedCrqList()),
                    parseCrqList(row.getSkippedCrqList())
            );

        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to convert CRQ list to JSON", e);
            throw new BusinessException("Failed to prepare request.");
        }
    }

    public ApiResponse planCab(PlanCabRequest body, Long actorUserId) {

        try {

            String sql = "CALL sp_plan_cab_session(?,?,?,?,?,?,?)";
            String crqListJson = objectMapper.writeValueAsString(body.crqIds());
            // The procedure types p_email_list as JSON, so "no recipients" has to
            // travel as an empty array - a null would fail the JSON cast.
            String emailListJson = objectMapper.writeValueAsString(
                    body.emailList() == null ? List.<String>of() : body.emailList());
            // The procedure reads the flag as Yes/No, so the boolean the client
            // sends (echoing what GET /cab/sessions/conflict told it) is mapped
            // here rather than making every caller spell the literal.
            String conflictFlag = Boolean.TRUE.equals(body.conflict()) ? "Yes" : "No";

            LOGGER.info(
                    "call sp_plan_cab_session('{}','{}','{}','{}','{}','{}','{}');",
                    body.sessionDateTime(), body.type(), actorUserId, crqListJson,
                    body.sessionLink(), conflictFlag, emailListJson
            );

            return databaseUtils.executeProcedureForMessageV1(
                    jdbcTemplateTwo,
                    sql,
                    body.sessionDateTime(),
                    body.type(),
                    actorUserId,
                    crqListJson,
                    body.sessionLink(),
                    conflictFlag,
                    emailListJson
            );

        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to convert CRQ list to JSON", e);
            throw new BusinessException("Failed to prepare request.");
        }
    }

    /**
     * Whether a CAB session is already booked for this date + time, and what it
     * holds. The planner calls this before POSTing a session so the CRQs can be
     * added to the existing one - on its own link - instead of a second session
     * landing in the same slot.
     *
     * An empty result set is a free slot, not an error.
     */
    public CabPlanConflictDto checkPlanConflict(String date, String time) {

        String sql = "CALL sp_check_cab_plan_conflict(?,?)";

        LOGGER.info("call sp_check_cab_plan_conflict('{}','{}');", date, time);

        List<CabPlanConflictRowDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, CabPlanConflictRowDto.class, date, time);

        if (rows.isEmpty()) {
            return new CabPlanConflictDto(false, null, null, List.of());
        }

        CabPlanConflictRowDto row = rows.get(0);
        return new CabPlanConflictDto(
                row.getIsConflict() != null && "YES".equalsIgnoreCase(row.getIsConflict().trim()),
                row.getCabId(),
                row.getSessionLink(),
                parseCrqList(row.getCrqList())
        );
    }

    /**
     * The procedure hands crq_list back as a JSON array literal. A slot that
     * cannot be parsed still answers the only question that matters - is the slot
     * taken - so a bad list is logged and reported as empty rather than failing
     * the whole check.
     */
    private List<String> parseCrqList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse crq_list from sp_check_cab_plan_conflict: {}", json, e);
            return List.of();
        }
    }

    /** Empty text from a form field means "not given", which the procs read as NULL. */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
