package com.vegayan.airtelmanagement.audit;

import java.util.Locale;
import java.util.Map;

/**
 * The closed vocabulary of audit verbs written to
 * {@code UI_ACTIONS_LOGGER.action}.
 *
 * <p>Deliberately plain {@code String} constants rather than an enum: the
 * column is a {@code VARCHAR(100)} that pre-dates this class and may already
 * hold values written by other clients, so nothing here may narrow what the
 * table can store. Constants give call sites one spelling to share without
 * making the persistence layer reject anything else.
 *
 * <p>A verb describes what the <em>user</em> did, not what the HTTP method
 * was. "Opened a page" is not a verb here on purpose - see {@code Auditable}.
 */
public final class AuditAction {

    private AuditAction() {
    }

    public static final String CREATE     = "CREATE";
    public static final String UPDATE     = "UPDATE";
    public static final String DELETE     = "DELETE";
    public static final String APPROVE    = "APPROVE";
    public static final String REJECT     = "REJECT";
    public static final String UPLOAD     = "UPLOAD";
    public static final String DOWNLOAD   = "DOWNLOAD";
    public static final String ASSIGN     = "ASSIGN";
    public static final String UNASSIGN   = "UNASSIGN";
    public static final String ENABLE     = "ENABLE";
    public static final String DISABLE    = "DISABLE";
    public static final String SUBMIT     = "SUBMIT";
    public static final String CANCEL     = "CANCEL";
    public static final String RESCHEDULE = "RESCHEDULE";
    public static final String LOGIN      = "LOGIN";
    public static final String LOGOUT     = "LOGOUT";

    /** State transitions that are not one of the CRUD verbs but are still
     *  deliberate user actions worth a record. */
    public static final String START      = "START";
    public static final String PAUSE      = "PAUSE";
    public static final String COMPLETE   = "COMPLETE";
    public static final String DELEGATE   = "DELEGATE";
    public static final String VALIDATE   = "VALIDATE";

    /**
     * Maps the raw value of a status-carrying request parameter onto the verb
     * that describes it, for the handful of endpoints where one route covers
     * two opposite actions (activate/deactivate, enable/disable).
     *
     * <p>Used by {@code Auditable#actionParam()}. Anything unrecognised
     * returns null so the aspect falls back to the annotation's static
     * {@code action()} - an unmapped value degrades to a slightly vaguer
     * record, never to a wrong one.
     */
    private static final Map<String, String> STATUS_VERBS = Map.ofEntries(
            Map.entry("TRUE",     ENABLE),
            Map.entry("ACTIVE",   ENABLE),
            Map.entry("ENABLE",   ENABLE),
            Map.entry("ENABLED",  ENABLE),
            Map.entry("Y",        ENABLE),
            Map.entry("1",        ENABLE),
            Map.entry("FALSE",    DISABLE),
            Map.entry("INACTIVE", DISABLE),
            Map.entry("DISABLE",  DISABLE),
            Map.entry("DISABLED", DISABLE),
            Map.entry("N",        DISABLE),
            Map.entry("0",        DISABLE),
            Map.entry("APPROVE",  APPROVE),
            Map.entry("APPROVED", APPROVE),
            Map.entry("REJECT",   REJECT),
            Map.entry("REJECTED", REJECT),
            Map.entry("CANCEL",   CANCEL),
            Map.entry("CANCELLED", CANCEL)
    );

    /** @return the verb a status value implies, or null when it implies none. */
    public static String fromStatusValue(Object value) {
        if (value == null) {
            return null;
        }
        return STATUS_VERBS.get(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    }
}
