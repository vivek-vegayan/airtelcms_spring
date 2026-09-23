package com.vegayan.airtelmanagement.audit.dto;

public record AuditLogEntry(
        String module,
        String subModule,
        String action,
        Long   actorUserId,
        Long   affectedUserId,
        String remark
) {

    public static final int REMARK_MAX_LENGTH = 1000;
    public static final int LABEL_MAX_LENGTH = 100;
    public AuditLogEntry {
        module    = clip(module,    LABEL_MAX_LENGTH);
        subModule = clip(subModule, LABEL_MAX_LENGTH);
        action    = clip(action,    LABEL_MAX_LENGTH);
        remark    = clip(remark,    REMARK_MAX_LENGTH);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public String dedupeKey() {
        return actorUserId + "|" + module + "|" + subModule + "|" + action
                + "|" + affectedUserId + "|" + remark;
    }
}
