package com.vegayan.airtelmanagement.schedular.dto;

/**
 * Maps raw CRQ_MASTER_TBL.current_status enum values to the display values
 * the UI has always used ("Not Started", "In Progress", "Paused", "Done",
 * ...). Values already in display form pass through untouched, so this is
 * safe to apply on every status field regardless of which procedure (old or
 * new generation) produced it.
 */
public final class WorkflowStatusDisplay {

    private WorkflowStatusDisplay() {
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        return switch (raw) {
            case "STARTED", "DRAFT"  -> "Not Started";
            case "IN_PROGRESS"       -> "In Progress";
            case "ON_HOLD"           -> "Paused";
            case "PENDING_APPROVAL"  -> "Pending Approval";
            case "DONE", "COMPLETE"  -> "Done";
            case "FAILED"            -> "Failed";
            case "CANCELLED"         -> "canceled";
            case "RESCHEDULED"       -> "Rescheduled";
            default                  -> raw;
        };
    }
}
