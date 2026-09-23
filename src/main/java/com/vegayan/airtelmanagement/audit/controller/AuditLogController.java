package com.vegayan.airtelmanagement.audit.controller;

import com.vegayan.airtelmanagement.audit.dto.AuditLogDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogFiltersDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogQuery;
import com.vegayan.airtelmanagement.audit.service.AuditLogService;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogController.class);

    private final AuditLogService auditLogService;

    @GetMapping
    public PageResponseDto<AuditLogDto> getAuditLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String subModule,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long affectedUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "25") Integer size,
            Authentication authentication) {

        AuditLogQuery query = AuditLogQuery.of(
                module, subModule, action, actorUserId, affectedUserId,
                fromDate, toDate, search, sortBy, sortDirection, page, size);

        return auditLogService.getAuditLogs(requesterId(authentication), query);
    }

    @GetMapping("/filters")
    public AuditLogFiltersDto getFilterOptions(
            @RequestParam(required = false) String module,
            Authentication authentication) {

        return auditLogService.getFilterOptions(requesterId(authentication), module);
    }

    private Long requesterId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        String name = authentication.getName();
        return name != null && name.matches("\\d+") ? Long.valueOf(name) : null;
    }

    @ExceptionHandler(DatabaseOperationException.class)
    public ResponseEntity<ApiResponse> handleDatabaseFailure(DatabaseOperationException ex,
                                                             HttpServletRequest request) {
        LOGGER.error("Audit log query failed at [{} {}]", request.getMethod(), request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse("Error", "Audit logs are temporarily unavailable. Please try again."));
    }
}
