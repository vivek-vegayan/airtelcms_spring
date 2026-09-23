package com.vegayan.airtelmanagement.remedycygnetsync.dto;

import java.time.LocalDateTime;

/**
 * One row of get_remedy_cygnet_crq() - a CRQ from SEND_TO_REMEDY_CYGNET_TBL
 * whose scheduled window still has to reach Remedy.
 *
 * Column names map case- and underscore-insensitively (crq_no -> crqNo).
 */
public record RemedyCygnetCrqDto(
        String crqNo,
        LocalDateTime scheduledStartTime,
        LocalDateTime scheduledEndTime,
        String timeflag,
        /** 'PENDING' until the push succeeds; update_remedy_cygnet_crq sets it to 'DONE'. */
        String status
) {
}
