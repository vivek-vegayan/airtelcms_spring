package com.vegayan.airtelmanagement.cabmanager.controller;

import com.vegayan.airtelmanagement.cabmanager.dto.BlockRingRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.ImplementationDetailDto;
import com.vegayan.airtelmanagement.cabmanager.service.CabImplementationService;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cab/crqs")
public class CabImplementationController {

    private final CabImplementationService cabImplementationService;

    public CabImplementationController(CabImplementationService cabImplementationService) {
        this.cabImplementationService = cabImplementationService;
    }

    @GetMapping("/implementation")
    public ImplementationDetailDto getImplementation() {
        return cabImplementationService.getImplementation();
    }

    @PostMapping("/{crqId}/rings/{ringId}/proceed")
    public ResponseEntity<ApiResponse> proceedRing(
            @PathVariable String crqId,
            @PathVariable String ringId,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabImplementationService.proceedRing(crqId, ringId, actorUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{crqId}/rings/{ringId}/block")
    public ResponseEntity<ApiResponse> blockRing(
            @PathVariable String crqId,
            @PathVariable String ringId,
            @RequestBody(required = false) BlockRingRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        String comment = body != null ? body.comment() : null;
        ApiResponse response = cabImplementationService.blockRing(crqId, ringId, comment, actorUserId);
        return ResponseEntity.ok(response);
    }
}
