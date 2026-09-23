package com.vegayan.airtelmanagement.schedular.dto;

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
