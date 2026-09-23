package com.vegayan.airtelmanagement.audit.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Set;

/**
 * A validated, normalised audit-log query.
 *
 * <p>The controller accepts loose request parameters; this record is where
 * they become something the procedure can be trusted with. Normalisation
 * happens once, here, rather than being spread between controller, service and
 * SQL:
 *
 * <ul>
 *   <li>blank text filters collapse to null ("no filter"), so an empty search
 *       box is not a filter for the empty string;</li>
 *   <li>page and size are clamped - a negative page or a size of 100000 is a
 *       malformed request, not a licence to read the whole table;</li>
 *   <li>the sort key is checked against a whitelist here <em>as well as</em> in
 *       the procedure, so an unknown key never reaches the database at all;</li>
 *   <li>a date-only range is widened to cover the whole day - a user asking
 *       for "4 Sep to 4 Sep" means that entire day, not the single instant of
 *       midnight.</li>
 * </ul>
 */
public record AuditLogQuery(
        String        module,
        String        subModule,
        String        action,
        Long          actorUserId,
        Long          affectedUserId,
        LocalDateTime fromDate,
        LocalDateTime toDate,
        String        search,
        String        sortBy,
        String        sortDirection,
        int           page,
        int           size
) {

    public static final int DEFAULT_PAGE_SIZE = 25;
    /** Mirrors the cap inside sp_get_ui_actions_log. */
    public static final int MAX_PAGE_SIZE     = 200;

    private static final String DEFAULT_SORT_BY  = "created_at";
    private static final String DEFAULT_SORT_DIR = "DESC";

    /** Sort keys the procedure understands. Anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of(
            "created_at", "log_id", "module", "sub_module", "action", "actor", "affected");

    public static AuditLogQuery of(
            String module,
            String subModule,
            String action,
            Long actorUserId,
            Long affectedUserId,
            LocalDate fromDate,
            LocalDate toDate,
            String search,
            String sortBy,
            String sortDirection,
            Integer page,
            Integer size) {

        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);

        return new AuditLogQuery(
                blankToNull(module),
                blankToNull(subModule),
                blankToNull(action),
                positiveOrNull(actorUserId),
                positiveOrNull(affectedUserId),
                // Inclusive on both ends: the whole of the from-day through the
                // whole of the to-day.
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate   == null ? null : toDate.atTime(LocalTime.MAX),
                blankToNull(search),
                normaliseSort(sortBy),
                normaliseDirection(sortDirection),
                safePage,
                safeSize);
    }

    /** Row offset this page starts at. */
    public int offset() {
        return page * size;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 0 is how the UI spells "All users" in a numeric dropdown. */
    private static Long positiveOrNull(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private static String normaliseSort(String sortBy) {
        if (sortBy == null) {
            return DEFAULT_SORT_BY;
        }
        String key = sortBy.trim().toLowerCase(Locale.ROOT)
                // accept the camelCase names the frontend columns use
                .replace("createdat", "created_at")
                .replace("logid", "log_id")
                .replace("submodule", "sub_module")
                .replace("actorname", "actor")
                .replace("affectedname", "affected");
        return SORTABLE.contains(key) ? key : DEFAULT_SORT_BY;
    }

    private static String normaliseDirection(String direction) {
        return direction != null && "asc".equalsIgnoreCase(direction.trim())
                ? "ASC"
                : DEFAULT_SORT_DIR;
    }
}
