package com.vegayan.airtelmanagement.remedycygnetsync.dto;

import java.time.LocalDateTime;

public record RemedyCygnetCrqDto(
        String crqNo,
        LocalDateTime scheduledStartTime,
        LocalDateTime scheduledEndTime,
        String timeflag,
        String status
) {
}
