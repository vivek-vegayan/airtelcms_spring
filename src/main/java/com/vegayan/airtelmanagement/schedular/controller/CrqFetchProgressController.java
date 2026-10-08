package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.schedular.dto.CrqFetchProgressDto;
import com.vegayan.airtelmanagement.schedular.service.CrqFetchProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crqworkflow/fetch")
@RequiredArgsConstructor
public class CrqFetchProgressController {

    private final CrqFetchProgressService crqFetchProgressService;

    // 204 when the daemon has not started a job for this CRQ yet - the UI
    // shows "waiting for plan data" and keeps polling, so it is not an error.
    @GetMapping("/by-crq/{crqNo}")
    public ResponseEntity<CrqFetchProgressDto> getLatestJob(
            @PathVariable String crqNo,
            @RequestParam(defaultValue = "VALIDATE") String stage) {
        CrqFetchProgressDto job = crqFetchProgressService.getLatestJob(crqNo, stage);
        return job == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(job);
    }

    // Impact Analysis batch strip (Batch1..4) - an empty list until the
    // daemon has run any batch for this CRQ.
    @GetMapping("/by-crq/{crqNo}/batches")
    public List<CrqFetchProgressDto> getImpactBatches(@PathVariable String crqNo) {
        return crqFetchProgressService.getImpactBatches(crqNo);
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_REVIEW,
               action = AuditAction.CANCEL,
               remark = "Cancelled CRQ node/interface fetch",
               keyParams = {"jobId"})
    @PostMapping("/{jobId}/cancel")
    public ApiResponse cancelJob(@PathVariable Long jobId) {
        return crqFetchProgressService.cancelJob(jobId);
    }
}
