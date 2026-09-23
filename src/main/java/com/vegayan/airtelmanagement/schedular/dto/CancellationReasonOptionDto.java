package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One row of {@code sp_Get_Distinct_Cancellation_Reasons} - a cancellation
 * reason plus the owner it always rolls back to.
 *
 * <p>The pairing is fixed by the procedure, not chosen by the user: the UI
 * shows the reason in a dropdown and fills "Cancellation Rejection Owner"
 * from the same row, which is why both columns travel together instead of
 * being two independent lookups.
 *
 * <p>A class rather than a record: these rows are mapped by
 * BeanPropertyRowMapper (via DatabaseUtils), which needs setters.
 */
@Getter
@Setter
public class CancellationReasonOptionDto {

    /** Column {@code Cancellation_reason}. */
    private String cancellationReason;

    /** Column {@code Cancellation_rollback_owner}. */
    private String cancellationRollbackOwner;
}
