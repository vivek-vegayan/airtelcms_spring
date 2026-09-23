package com.vegayan.airtelmanagement.audit;

/**
 * Module / sub-module labels written to {@code UI_ACTIONS_LOGGER.module} and
 * {@code .sub_module}.
 *
 * <p>The module names mirror the {@code WEB_MODULE.module_name} values the
 * RBAC layer and the sidebar already use ("User Management", "Scheduler",
 * "Cab Manager", ...) so that an audit row reads the same way as the screen
 * the action was performed on. Sub-module names are the screen or entity
 * within that module and are NOT constrained to {@code WEB_SUB_MODULE} rows -
 * several audited actions happen on screens that have no RBAC sub-module row
 * of their own, and inventing grants for them was explicitly out of scope.
 *
 * <p>Constants, not an enum, for the same reason as {@link AuditAction}: the
 * columns are free-text VARCHARs that must keep accepting anything already
 * written to them.
 */
public final class AuditModule {

    private AuditModule() {
    }

    // ── Modules ──────────────────────────────────────────────────────────
    public static final String USER_MANAGEMENT      = "User Management";
    public static final String TEAM_MANAGEMENT      = "Team Management";
    public static final String GLOBAL_SETTINGS      = "Global Settings";
    public static final String ORGANIZATION         = "Organization Hierarchy";
    public static final String RBAC                 = "Role-Based Access Control";
    public static final String SCHEDULER            = "Scheduler";
    public static final String CAB_MANAGER          = "Cab Manager";
    public static final String AUTHENTICATION       = "Authentication";
    public static final String NOTIFICATION_SYSTEM  = "Notification System";
    public static final String ME                   = "Me";

    // ── Sub-modules ──────────────────────────────────────────────────────
    public static final String SUB_USER             = "User";
    public static final String SUB_EMPLOYEE_UPLOAD  = "Employee Bulk Upload";
    public static final String SUB_SESSION          = "Session";
    public static final String SUB_VERTICAL         = "Vertical";
    public static final String SUB_FUNCTION         = "Function";
    public static final String SUB_DOMAIN           = "Domain";
    public static final String SUB_SUB_DOMAIN       = "Sub Domain";
    public static final String SUB_ROLE             = "Role";
    public static final String SUB_MODULE           = "Module";
    public static final String SUB_SUB_MODULE       = "Sub Module";
    public static final String SUB_PERMISSION       = "Permission";
    public static final String SUB_CRQ_REVIEW       = "CRQ Review";
    public static final String SUB_IMPACT_ANALYSIS  = "Impact Analysis";
    public static final String SUB_MOP_CREATE       = "MOP Create";
    public static final String SUB_MOP_VALIDATION   = "MOP Validation";
    public static final String SUB_CRQ_VALIDATION   = "CRQ Validation";
    public static final String SUB_CRQ_SCHEDULING   = "CRQ Scheduling";
    public static final String SUB_ACTIVITY_IMPL    = "Activity Implementation";
    public static final String SUB_CRQ_CLOSURE      = "CRQ Closure";
    public static final String SUB_CHECKPOINT       = "Checkpoint Summary";
    public static final String SUB_CRQ_RESCHEDULE   = "CRQ Reschedule";
    public static final String SUB_CRQ_APPROVAL     = "CRQ Approval";
    public static final String SUB_CRQ_ASSIGNMENT   = "CRQ Assignment";
    public static final String SUB_CAB_SESSION      = "Cab Session";
    public static final String SUB_REJECT_REASON    = "Reject Reason";
    public static final String SUB_NOTIFICATION_CFG = "Notification Configuration";
    public static final String SUB_LEAVE            = "Leave";
    public static final String SUB_AUDIT_LOG        = "Audit Log";
}
