package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.schedular.service.CrqWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mirrors the legacy standalone project's PlanningWorkflowController
 * (com.vegayan.changemanagement.planning.controller) URL contract for the
 * CRQ Review checkpoint JSON - the CheckPoint Summary Preview panel's
 * frontend calls /planningworkflow/json/raw/{crqNo}, matching that original
 * source. Only this endpoint pair is ported here; the rest of the legacy
 * controller's endpoints (mop, scheduling, reschedule, assignment, ...)
 * already have current equivalents under CrqWorkflowController.
 *
 * The SFTP read/write logic itself already lives in CrqWorkflowService
 * (reused by /crqworkflow/json/raw/{crqNo}), so this controller only adds
 * the /planningworkflow route on top of it rather than duplicating it.
 */
@RestController
@RequestMapping("/planningworkflow")
@RequiredArgsConstructor
public class PlanningWorkflowController {

    private final CrqWorkflowService crqWorkflowService;

    @GetMapping("/json/raw/{crqNo}")
    public ResponseEntity<Object> getJsonFile(@PathVariable String crqNo) {
        Object response = crqWorkflowService.fetchJsonFile(crqNo);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/json/raw/{crqNo}")
    public ResponseEntity<String> updateJsonFile(
            @PathVariable String crqNo,
            @RequestBody Map<String, Object> updateRequest) {

        try {
            String updatedJson = crqWorkflowService.updateJsonFile(crqNo, updateRequest);
            return ResponseEntity.ok(updatedJson);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating file for CRQ " + crqNo + ": " + e.getMessage());
        }
    }
}
