package com.vegayan.airtelmanagement.dataagent.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentFeedbackRequest;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentHistoryDto;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentQueryRequest;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentQueryResponse;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentSaveHistoryRequest;
import com.vegayan.airtelmanagement.dataagent.service.DataAgentFeedbackService;
import com.vegayan.airtelmanagement.dataagent.service.DataAgentHistoryService;
import com.vegayan.airtelmanagement.dataagent.service.DataAgentQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST surface of the Data Agent module - a conversational NL-to-SQL analytics
 * assistant proxied to the external Traffic QA server (see
 * DataAgentQueryService / DataAgentFeedbackService for the /ask and /feedback
 * proxy calls, and AppPropertiesConfig#getPYTHON_SERVER_URL for its address),
 * plus per-user chat history (DataAgentHistoryService, backed by the
 * SP_DATAAGENT_* procedures in db/migration/2026-08-03_dataagent_module.sql).
 *
 * Authentication is the host app's existing JWT filter, same as every other
 * controller; the acting user for history is resolved from the JWT subject,
 * never taken from the request, exactly like CrqRescheduleController.
 *
 * No @PreAuthorize here (deliberately, not an oversight): UserPermissionService
 * .getPermissionsByUserIdV1 - the only source of @PreAuthorize authorities in
 * this app - queries MODULE/SUB_MODULE/PERMISSION tables that no longer exist
 * in the live schema (superseded by WEB_MODULE/WEB_SUB_MODULE/WEB_PERMISSION
 * per the RBAC WEB cutover), so it silently returns no authorities for every
 * user. Every other controller in this app is auth-only for the same reason -
 * this one matches that, rather than being unreachable behind a broken check.
 */
@RestController
@RequestMapping("/dataagent")
public class DataAgentController {

    private final DataAgentQueryService queryService;
    private final DataAgentFeedbackService feedbackService;
    private final DataAgentHistoryService historyService;

    public DataAgentController(
            DataAgentQueryService queryService,
            DataAgentFeedbackService feedbackService,
            DataAgentHistoryService historyService) {
        this.queryService = queryService;
        this.feedbackService = feedbackService;
        this.historyService = historyService;
    }

    private static Long actorId(Authentication authentication) {
        return authentication == null ? null : Long.valueOf(authentication.getName());
    }

    @PostMapping("/ask")
    public DataAgentQueryResponse ask(@Valid @RequestBody DataAgentQueryRequest request) {
        return queryService.ask(request.question());
    }

    @PostMapping("/feedback")
    public Map<String, Object> feedback(@Valid @RequestBody DataAgentFeedbackRequest request) {
        return feedbackService.submitFeedback(
                request.requestId(), request.panelId(), request.rating(), request.comment());
    }

    @GetMapping("/history")
    public List<DataAgentHistoryDto> getHistory(Authentication authentication) {
        return historyService.getHistory(actorId(authentication));
    }

    @PostMapping("/history")
    public ApiResponse saveHistory(Authentication authentication, @Valid @RequestBody DataAgentSaveHistoryRequest request) {
        return historyService.saveHistory(
                actorId(authentication), request.question(), request.summary(), request.intent(), request.rowCount());
    }

    @DeleteMapping("/history/{historyId}")
    public ApiResponse deleteHistory(Authentication authentication, @PathVariable Long historyId) {
        return historyService.deleteHistory(actorId(authentication), historyId);
    }

    @DeleteMapping("/history")
    public ApiResponse clearHistory(Authentication authentication) {
        return historyService.clearHistory(actorId(authentication));
    }
}
