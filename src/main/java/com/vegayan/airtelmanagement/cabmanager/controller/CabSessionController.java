package com.vegayan.airtelmanagement.cabmanager.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.cabmanager.dto.AddCrqToSessionRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.AddCrqToSessionResultDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabAgendaRowDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabPlanConflictDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabSessionCrqActionRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.CabSessionCrqActionResultDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabSessionDto;
import com.vegayan.airtelmanagement.cabmanager.dto.PlanCabRequest;
import com.vegayan.airtelmanagement.cabmanager.service.CabSessionService;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cab/sessions")
public class CabSessionController {

    private final CabSessionService cabSessionService;

    public CabSessionController(CabSessionService cabSessionService) {
        this.cabSessionService = cabSessionService;
    }

    @GetMapping
    public List<CabSessionDto> getCabSessions() {
        return cabSessionService.getCabSessions();
    }

    /**
     * The agenda board for one session - the CRQs tabled at it and the decision
     * standing against each. Empty until the CAB Engineer adds CRQs.
     */
    @GetMapping("/{sessionId}/agenda")
    public List<CabAgendaRowDto> getCabSessionAgenda(@PathVariable String sessionId) {
        return cabSessionService.getCabSessionAgenda(sessionId);
    }

    /**
     * Records the CAB's decision - APPROVE / REJECT / RESCHEDULE - on one CRQ
     * tabled at a session.
     *
     * <p>Addressed by the agenda mapping id rather than the CRQ number, because
     * the same CRQ may be tabled again later and each sitting keeps its own
     * decision.
     */
    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CAB_SESSION,
               // APPROVE and REJECT resolve from the body; RESCHEDULE has no
               // status mapping, so it is the static fallback and the verb is
               // right for all three.
               action = AuditAction.RESCHEDULE,
               actionParam = "body.action",
               remark = "Recorded a CAB decision on a tabled CRQ",
               keyParams = {"mappingId"})
    @PostMapping("/agenda/{mappingId}/action")
    public CabSessionCrqActionResultDto recordCrqDecision(
            @PathVariable Long mappingId,
            @RequestBody CabSessionCrqActionRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return cabSessionService.recordCrqDecision(mappingId, body, actorUserId);
    }

    /** Adds further CRQs to a session whose agenda is already open. */
    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CAB_SESSION,
               action = AuditAction.UPDATE,
               remark = "Added CRQs to a CAB session",
               keyParams = {"sessionId"})
    @PostMapping("/{sessionId}/crqs")
    public AddCrqToSessionResultDto addCrqsToSession(
            @PathVariable String sessionId,
            @RequestBody AddCrqToSessionRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return cabSessionService.addCrqsToSession(sessionId, body, actorUserId);
    }

    /**
     * Whether the given slot already has a CAB session, and what it holds - the
     * planner calls this before POSTing so the CRQs can join the existing session
     * on its own link. date is yyyy-MM-dd, time is HH:mm:ss.
     */
    @GetMapping("/conflict")
    public CabPlanConflictDto checkPlanConflict(
            @RequestParam String date,
            @RequestParam String time) {
        return cabSessionService.checkPlanConflict(date, time);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CAB_SESSION,
               action = AuditAction.CREATE,
               remark = "Planned a CAB session")
    @PostMapping
    public ApiResponse planCab(
            @RequestBody PlanCabRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return cabSessionService.planCab(body, actorUserId);
    }
}
