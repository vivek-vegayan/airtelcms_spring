package com.vegayan.airtelmanagement.remedycygnetsync.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.remedycygnetsync.dto.RemedyCygnetCrqDto;
import com.vegayan.airtelmanagement.remedycygnetsync.dto.RemedyCygnetSyncResultDto;
import com.vegayan.airtelmanagement.remedycygnetsync.service.RemedyCygnetSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Manual controls for the sync that RemedyCygnetSyncScheduler runs every minute. */
@RestController
@RequestMapping("/remedycygnetsync")
public class RemedyCygnetSyncController {

    private final RemedyCygnetSyncService remedyCygnetSyncService;

    public RemedyCygnetSyncController(RemedyCygnetSyncService remedyCygnetSyncService) {
        this.remedyCygnetSyncService = remedyCygnetSyncService;
    }

    /** What the next cycle will pick up - get_remedy_cygnet_crq(), no push. */
    @GetMapping("/queue")
    public List<RemedyCygnetCrqDto> queue() {
        return remedyCygnetSyncService.fetchQueuedCrqs();
    }

    /**
     * Runs one cycle now. Returns 200 with status "Skipped" when a cycle is
     * already running, and "Partial" when only some CRQs pushed.
     */
    @PostMapping("/run")
    public ResponseEntity<RemedyCygnetSyncResultDto> run() {
        RemedyCygnetSyncResultDto result = remedyCygnetSyncService.runOnce();
        return "Error".equals(result.status())
                ? ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result)
                : ResponseEntity.ok(result);
    }

    /** Marks one CRQ status = 'DONE' by hand (update_remedy_cygnet_crq). */
    @PostMapping("/mark-done/{crqNo}")
    public ApiResponse markDone(@PathVariable String crqNo) {
        return remedyCygnetSyncService.markDone(crqNo);
    }
}
