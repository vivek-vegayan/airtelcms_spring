package com.vegayan.airtelmanagement.audit.service;

import com.vegayan.airtelmanagement.audit.dto.AuditAccessDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogEntry;
import com.vegayan.airtelmanagement.audit.dto.AuditLogFilterOptionDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogFiltersDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogQuery;
import com.vegayan.airtelmanagement.audit.dto.AuditUserOptionDto;
import com.vegayan.airtelmanagement.audit.repository.AuditLogRepository;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.exception.PermissionDeniedException;
import com.vegayan.airtelmanagement.common.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place the application talks to the UI action audit trail.
 *
 * <p>Write side ({@link #logAction}) - called by {@code AuditLogAspect} for
 * every {@code @Auditable} controller method, and directly by the handful of
 * call sites where the actor is not yet (or no longer) in the SecurityContext,
 * i.e. login and logout.
 *
 * <p>Read side ({@link #getAuditLogs}, {@link #getFilterOptions}) - backs the
 * Audit Log screen. Every read is authorised first: see
 * {@link #assertMayReadAuditLog}.
 *
 * <p>No module ever gets its own copy of this logic. A feature that wants to
 * be audited annotates its endpoint; it does not build audit rows itself.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final AuditLogWriter auditLogWriter;
    private final AuditActorResolver actorResolver;

    /**
     * How long an identical action by the same actor is treated as a repeat of
     * the one already recorded rather than as a new one. Covers a double-click,
     * a React double-invoke, a component remount that re-fires its effect, and
     * a client-side retry - all of which are one user action arriving twice.
     *
     * <p>Set to 0 to record every request. Anything above a few seconds starts
     * to swallow genuine repeated actions, so the default is deliberately short.
     */
    @Value("${audit.log.dedupe-window-ms:4000}")
    private long dedupeWindowMs;

    /**
     * Last-seen time per action fingerprint. Bounded by {@link #DEDUPE_MAX_KEYS}
     * and swept whenever it grows past that, so a long-running instance cannot
     * accumulate keys without limit. In-memory and per-instance: behind a load
     * balancer the two halves of a double-submit can land on different nodes and
     * both be recorded. That is the correct trade - suppressing a real action is
     * worse than an occasional duplicate, and the row carries a timestamp that
     * makes a duplicate obvious to a reader.
     */
    private final Map<String, Long> recentActions = new ConcurrentHashMap<>();

    private static final int DEDUPE_MAX_KEYS = 5_000;

    // =====================================================================
    // WRITE - recording a user action
    // =====================================================================

    /**
     * Records one user action performed by the currently authenticated user.
     *
     * <p>The actor is resolved from the SecurityContext, never from a caller
     * argument, so nothing an API client sends can attribute an action to
     * someone else.
     *
     * @param module         business module, e.g. {@code AuditModule.USER_MANAGEMENT}
     * @param subModule      screen or entity within it; null writes NULL
     * @param action         the verb, e.g. {@code AuditAction.CREATE}
     * @param affectedUserId the user this was done to, or null when the action
     *                       targets something other than a person
     * @param remark         short description of what happened
     */
    public void logAction(String module, String subModule, String action,
                          Long affectedUserId, String remark) {
        logActionAs(actorResolver.currentActorUserId(), module, subModule, action, affectedUserId, remark);
    }

    /**
     * Records an action on behalf of an explicitly known actor.
     *
     * <p>Exists for exactly two situations, both in the authentication flow:
     * at sign-in the SecurityContext is not populated yet, and at sign-out it
     * is about to be discarded. Both take the id from a token or credential the
     * server itself has just verified - not from the request body - so this
     * overload is no weaker than the one above.
     */
    public void logActionAs(Long actorUserId, String module, String subModule, String action,
                            Long affectedUserId, String remark) {
        try {
            AuditLogEntry entry = new AuditLogEntry(
                    module, subModule, action, actorUserId, affectedUserId, remark);

            if (isDuplicate(entry)) {
                LOGGER.debug("Audit row suppressed as a repeat within {}ms: {}", dedupeWindowMs, entry.dedupeKey());
                return;
            }

            auditLogWriter.write(entry);

        } catch (Exception ex) {
            // Belt and braces. AuditLogWriter already swallows its own
            // failures; this catches anything that goes wrong before the hand-
            // off (a rejected task, a resolver problem) so that no path from a
            // business operation into the audit trail can throw back into it.
            LOGGER.error("Failed to enqueue an audit row for module={} action={}", module, action, ex);
        }
    }

    /**
     * True when this exact action, by this exact actor, was recorded within the
     * dedupe window. Also sweeps the map when it has grown too large.
     */
    private boolean isDuplicate(AuditLogEntry entry) {
        if (dedupeWindowMs <= 0) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (recentActions.size() > DEDUPE_MAX_KEYS) {
            recentActions.entrySet().removeIf(e -> now - e.getValue() > dedupeWindowMs);
        }

        Long previous = recentActions.put(entry.dedupeKey(), now);
        return previous != null && (now - previous) < dedupeWindowMs;
    }

    // =====================================================================
    // READ - the Audit Log screen
    // =====================================================================

    /**
     * One page of the audit trail for a super admin.
     *
     * <p>Filtering, searching, sorting and paging all happen in the database.
     * The only thing done in Java is lifting the procedure's per-row
     * {@code Total_Count} into the page envelope and clearing it from the rows,
     * so the same number is not repeated on every record of the response.
     *
     * @throws PermissionDeniedException when the caller is not a super admin
     */
    public PageResponseDto<AuditLogDto> getAuditLogs(Long requesterUserId, AuditLogQuery query) {

        assertMayReadAuditLog(requesterUserId);

        LOGGER.info("call sp_get_ui_actions_log('{}','{}','{}','{}','{}','{}','{}','{}','{}','{}','{}','{}');",
                query.module(), query.subModule(), query.action(), query.actorUserId(),
                query.affectedUserId(), query.fromDate(), query.toDate(), query.search(),
                query.sortBy(), query.sortDirection(), query.size(), query.offset());

        List<AuditLogDto> rows = auditLogRepository.findPage(query);
        if (rows == null) {
            rows = Collections.emptyList();
        }

        long totalElements = rows.stream()
                .map(AuditLogDto::getTotalCount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0L);
        rows.forEach(row -> row.setTotalCount(null));

        return PaginationUtils.buildPageResponse(
                rows, PageRequest.of(query.page(), query.size()), totalElements);
    }

    /**
     * The values behind the filter dropdowns, grouped by facet.
     *
     * @param module when set, narrows the Sub Module facet to that module
     * @throws PermissionDeniedException when the caller is not a super admin
     */
    public AuditLogFiltersDto getFilterOptions(Long requesterUserId, String module) {

        assertMayReadAuditLog(requesterUserId);

        String moduleFilter = module == null || module.isBlank() ? null : module.trim();

        LOGGER.info("call sp_get_ui_actions_log_filters('{}');", moduleFilter);

        List<AuditLogFilterOptionDto> options = auditLogRepository.findFilterOptions(moduleFilter);
        if (options == null) {
            options = Collections.emptyList();
        }

        return new AuditLogFiltersDto(
                textFacet(options, "MODULE"),
                textFacet(options, "SUB_MODULE"),
                textFacet(options, "ACTION"),
                userFacet(options, "ACTOR"),
                userFacet(options, "AFFECTED"));
    }

    private List<String> textFacet(List<AuditLogFilterOptionDto> options, String type) {
        return options.stream()
                .filter(o -> type.equalsIgnoreCase(o.getFilterType()))
                .map(AuditLogFilterOptionDto::getFilterValue)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<AuditUserOptionDto> userFacet(List<AuditLogFilterOptionDto> options, String type) {
        List<AuditUserOptionDto> users = new ArrayList<>();
        for (AuditLogFilterOptionDto option : options) {
            if (!type.equalsIgnoreCase(option.getFilterType()) || option.getFilterValue() == null) {
                continue;
            }
            try {
                users.add(new AuditUserOptionDto(
                        Long.valueOf(option.getFilterValue().trim()),
                        option.getFilterLabel()));
            } catch (NumberFormatException ignored) {
                // A user facet whose value is not a number cannot be a user id.
                // Skipping it drops one dropdown entry rather than failing the
                // whole filter bar.
            }
        }
        return users;
    }

    // =====================================================================
    // AUTHORISATION
    // =====================================================================

    /**
     * Server-side super-admin gate for every audit read.
     *
     * <p>Independent of anything the browser does. Hiding the menu item and
     * guarding the route in React stop an ordinary user stumbling onto the
     * screen; this is what stops one who types the URL, replays the request in
     * a console, or calls the endpoint with curl.
     *
     * <p>The decision itself is made by {@code sp_get_ui_actions_log_access}
     * against the same USER_ROLE_MAP / ROLE_MASTER tables login already uses -
     * so this is the existing role architecture being consulted, not a second
     * authorisation system.
     */
    public void assertMayReadAuditLog(Long requesterUserId) {

        if (requesterUserId == null) {
            LOGGER.warn("Audit log read refused: no authenticated user on the request");
            throw new PermissionDeniedException("You are not authorised to view audit logs.");
        }

        AuditAccessDto access = auditLogRepository.findAccess(requesterUserId);

        if (access == null || access.getIsAllowed() == null || access.getIsAllowed() != 1) {
            LOGGER.warn("Audit log read refused for userId={} (role={})",
                    requesterUserId, access == null ? "unknown" : access.getRoleCode());
            throw new PermissionDeniedException("You are not authorised to view audit logs.");
        }
    }
}
