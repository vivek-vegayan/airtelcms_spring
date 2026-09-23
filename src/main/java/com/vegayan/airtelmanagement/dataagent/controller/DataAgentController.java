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
