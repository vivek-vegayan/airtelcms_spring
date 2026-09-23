package com.vegayan.airtelmanagement.audit;

import java.util.Locale;
import java.util.Map;

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

    public static final String START      = "START";
    public static final String PAUSE      = "PAUSE";
    public static final String COMPLETE   = "COMPLETE";
    public static final String DELEGATE   = "DELEGATE";
    public static final String VALIDATE   = "VALIDATE";


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

    public static String fromStatusValue(Object value) {
        if (value == null) {
            return null;
        }
        return STATUS_VERBS.get(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    }
}
