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

/**
 * Read API for the UI action audit trail - backs User Management -> Audit Log.
 *
 * <h3>Read-only, by design</h3>
 * There is no POST, PUT, PATCH or DELETE here and there never should be. Audit
 * records are written only as a side effect of the actions they describe (see
 * {@code AuditLogAspect}); nothing in the application, and therefore nothing a
 * user can reach, can edit or remove one through an API.
 *
 * <h3>Super admin only, enforced here and not only in the browser</h3>
 * Every method resolves the caller from the authenticated principal - the id
 * {@code JwtAuthenticationFilter} established from a verified token and a live
 * AUTH_JWT_TOKENS row - and passes it to the service, which refuses anyone who
 * is not a super admin with 403. Typing the URL, replaying the request or
 * calling it with curl all take exactly the same path.
 *
 * <p>Note there is deliberately no {@code @PreAuthorize} here: the authority
 * source it would consult ({@code UserPermissionService.getPermissionsByUserIdV1})
 * queries MODULE / SUB_MODULE / PERMISSION tables that no longer exist in this
 * schema, so annotating a method with it makes the endpoint fail for everyone.
 * Fixing that is a separate concern from this feature, and touching it would
 * have changed behaviour elsewhere.
 *
 * <h3>No per-record endpoint</h3>
 * The list already returns every stored column plus the resolved identities,
 * so the View Details dialog renders from the row the table is holding. A
 * {@code GET /audit-logs/{id}} would be a second round trip for data the client
 * already has.
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogController.class);

    private final AuditLogService auditLogService;

    /**
     * One page of the audit trail. Every parameter is optional; an omitted one
     * simply does not narrow the population.
     *
     * <p>Paging, filtering, searching and sorting are all executed by
     * {@code sp_get_ui_actions_log} - no part of this endpoint reads rows in
     * order to discard them, which is what makes it safe against a table
     * expected to reach millions of records.
     *
     * @param fromDate inclusive, whole day (00:00:00.000000 onwards)
     * @param toDate   inclusive, whole day (through 23:59:59.999999)
     */
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

    /**
     * Values behind the filter dropdowns, derived from rows that exist - so a
     * filter can never offer a value that would return an empty page.
     *
     * @param module when given, narrows the Sub Module facet to that module
     */
    @GetMapping("/filters")
    public AuditLogFiltersDto getFilterOptions(
            @RequestParam(required = false) String module,
            Authentication authentication) {

        return auditLogService.getFilterOptions(requesterId(authentication), module);
    }

    /**
     * The caller's id, taken only from the authenticated principal.
     *
     * <p>{@code JwtAuthenticationFilter} puts a {@code Long} there for a real
     * login and a username String for the static access-token route. The latter
     * is not a person and cannot be a super admin, so it resolves to null and
     * the service refuses it.
     */
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

    /**
     * Keeps raw database text out of this API's responses.
     *
     * <p>A controller-local handler takes precedence over the global
     * {@code @RestControllerAdvice}, which answers a
     * {@code DatabaseOperationException} with {@code ex.getMessage()} - fine for
     * procedures that raise readable business rules, wrong for an audit screen
     * where the message would be JDBC plumbing. The real cause still goes to
     * the application log; the caller gets a sentence they can act on.
     *
     * <p>Scoped to this controller only - the global handler's behaviour
     * elsewhere is untouched.
     */
    @ExceptionHandler(DatabaseOperationException.class)
    public ResponseEntity<ApiResponse> handleDatabaseFailure(DatabaseOperationException ex,
                                                             HttpServletRequest request) {
        LOGGER.error("Audit log query failed at [{} {}]", request.getMethod(), request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse("Error", "Audit logs are temporarily unavailable. Please try again."));
    }
}
