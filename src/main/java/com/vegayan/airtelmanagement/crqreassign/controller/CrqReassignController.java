package com.vegayan.airtelmanagement.crqreassign.controller;

import com.vegayan.airtelmanagement.crqreassign.dto.*;
import com.vegayan.airtelmanagement.crqreassign.service.CrqReassignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crq/reassign")
@RequiredArgsConstructor
public class CrqReassignController {

    private final CrqReassignService reassignService;

    /* ── read ─────────────────────────────────────────────────────────────── */

    /** page is 1-based, as CRQ_SP_REASSIGN_GRID expects. */
    @GetMapping("/grid")
    public List<Map<String, Object>> getGrid(@RequestParam(required = false) String search,
                                             @RequestParam(required = false) Integer teamId,
                                             @RequestParam(required = false) String cab,
                                             @RequestParam(defaultValue = "false") boolean onlyGaps,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        return reassignService.getGrid(search, teamId, cab, onlyGaps, page, size);
    }

    @GetMapping("/timeline")
    public List<Map<String, Object>> getTimeline(@RequestParam String from,
                                                 @RequestParam String to,
                                                 @RequestParam(required = false) Integer teamId,
                                                 @RequestParam(required = false) String level,
                                                 @RequestParam(required = false) String shift,
                                                 @RequestParam(required = false) String search) {
        return reassignService.getTimeline(from, to, teamId, level, shift, search);
    }

    @GetMapping("/candidates")
    public List<Map<String, Object>> getCandidates(@RequestParam String crqNo,
                                                   @RequestParam String stage,
                                                   @RequestParam(required = false) String level,
                                                   @RequestParam(defaultValue = "false") boolean sameTeam) {
        return reassignService.getCandidates(crqNo, stage, level, sameTeam);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(@RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) String batchId) {
        return reassignService.getStats(from, to, batchId);
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getHistory(@RequestParam(required = false) String crqNo,
                                                @RequestParam(required = false) String batchId,
                                                @RequestParam(required = false) String fromTs,
                                                @RequestParam(required = false) String toTs) {
        return reassignService.getHistory(crqNo, batchId, fromTs, toTs);
    }

    /* ── actions ──────────────────────────────────────────────────────────── */

    @PostMapping("/member")
    public ReassignActionResponseDto reassignMember(Authentication auth, @Valid @RequestBody ReassignMemberRequest request) {
        return reassignService.reassignMember(userId(auth), request);
    }

    @PostMapping("/time")
    public ReassignActionResponseDto reassignTime(Authentication auth, @Valid @RequestBody ReassignTimeRequest request) {
        return reassignService.reassignTime(userId(auth), request);
    }

    @PostMapping("/cab")
    public ReassignActionResponseDto reassignCab(Authentication auth, @Valid @RequestBody ReassignCabRequest request) {
        return reassignService.reassignCab(userId(auth), request);
    }

    @PostMapping("/undo")
    public ReassignActionResponseDto undo(Authentication auth, @Valid @RequestBody ReassignBatchRequest request) {
        return reassignService.undo(userId(auth), request);
    }

    @PostMapping("/publish")
    public ReassignActionResponseDto publish(Authentication auth, @Valid @RequestBody ReassignBatchRequest request) {
        return reassignService.publish(userId(auth), request);
    }

    private static Long userId(Authentication auth) {
        return Long.valueOf(auth.getName());
    }
}
