package com.vegayan.airtelmanagement.crqreassign.service;

import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.crqreassign.dto.*;
import com.vegayan.airtelmanagement.schedular.repository.CrqRescheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CRQ reassignment page — one method per CRQ_SP_REASSIGN_* procedure.
 * The procs do all the checks; they answer a rejection with an error_message row.
 */
@Service
@RequiredArgsConstructor
public class CrqReassignService extends BaseService {

    private final CrqRescheduleRepository rescheduleRepository;

    /* ── read ─────────────────────────────────────────────────────────────── */

    public List<Map<String, Object>> getGrid(String search, Integer teamId, String cab, boolean onlyGaps, int page, int size) {
        return call("CRQ_SP_REASSIGN_GRID", search, teamId, cab, onlyGaps, page, size);
    }

    public List<Map<String, Object>> getTimeline(String from, String to, Integer teamId, String level, String shift, String search) {
        return call("CRQ_SP_REASSIGN_TIMELINE", from, to, teamId, level, shift, search);
    }

    public List<Map<String, Object>> getCandidates(String crqNo, String stage, String level, boolean sameTeam) {
        return call("CRQ_SP_REASSIGN_CANDIDATES", crqNo, stage, level, sameTeam);
    }

    public Map<String, Object> getStats(String from, String to, String batchId) {
        return call("CRQ_SP_REASSIGN_STATS", from, to, batchId).stream().findFirst().orElse(Map.of());
    }

    public List<Map<String, Object>> getHistory(String crqNo, String batchId, String fromTs, String toTs) {
        return call("CRQ_SP_REASSIGN_HISTORY", crqNo, batchId, fromTs, toTs);
    }

    /* ── actions ──────────────────────────────────────────────────────────── */

    public ReassignActionResponseDto reassignMember(Long userId, ReassignMemberRequest req) {
        return action("CRQ_SP_REASSIGN_MEMBER", req.crqNo(), req.stage(), req.newOlmId(), req.batchId(), olmId(userId), req.remarks());
    }

    public ReassignActionResponseDto reassignTime(Long userId, ReassignTimeRequest req) {
        return action("CRQ_SP_REASSIGN_TIME", req.crqNo(), req.stage(), req.newStart(), req.keepDuration(), req.newEnd(), req.batchId(), olmId(userId));
    }

    public ReassignActionResponseDto reassignCab(Long userId, ReassignCabRequest req) {
        return action("CRQ_SP_REASSIGN_CAB", req.crqNo(), req.teamId(), req.cabFlag(), req.batchId(), olmId(userId));
    }

    public ReassignActionResponseDto undo(Long userId, ReassignBatchRequest req) {
        return action("CRQ_SP_REASSIGN_UNDO", req.batchId(), olmId(userId));
    }

    public ReassignActionResponseDto publish(Long userId, ReassignBatchRequest req) {
        return action("CRQ_SP_REASSIGN_PUBLISH", req.batchId(), olmId(userId));
    }

    /* ── helpers ──────────────────────────────────────────────────────────── */

    /** "call PROC(?,?,…)" on the CRQ database; an error_message row becomes a 400. */
    private List<Map<String, Object>> call(String procedure, Object... args) {
        String sql = "call " + procedure + "(" + String.join(",", Collections.nCopies(args.length, "?")) + ")";
        List<Map<String, Object>> rows = databaseUtils.executeProcedureLastResultSet(jdbcTemplateTwo, sql, args);

        if (!rows.isEmpty() && rows.get(0).get("error_message") != null) {
            throw new BusinessException(rows.get(0).get("error_message").toString());
        }
        return rows;
    }

    /** Every action proc ends with one (status, batch_id, message) row. */
    private ReassignActionResponseDto action(String procedure, Object... args) {
        Map<String, Object> row = call(procedure, args).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("No response from the database."));
        return new ReassignActionResponseDto(
                Objects.toString(row.get("status"), null),
                Objects.toString(row.get("batch_id"), null),
                Objects.toString(row.get("message"), null));
    }

    /** txn_by is the caller's OLM id, same as the reschedule flow; falls back to the user id. */
    private String olmId(Long userId) {
        return rescheduleRepository.findOlmIdByUserId(userId).stream()
                .map(row -> row.get("olmid"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse(String.valueOf(userId));
    }
}
