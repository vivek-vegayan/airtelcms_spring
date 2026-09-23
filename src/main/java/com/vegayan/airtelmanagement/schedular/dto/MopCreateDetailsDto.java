package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Read-only header shown above the MOP document uploader on the MOP Create
 * stage dialog.
 *
 * <p>The six CRQ facts below are the columns {@code SP_GET_MOP_DETAILS_BY_CRQN}
 * returns ({@code crq_number}, {@code title}, {@code window_start},
 * {@code window_end}, {@code region}, {@code vendor}) - but that procedure is a
 * <em>create</em>, not a read: it INSERTs into {@code mop}, {@code mop_version}
 * and {@code mop_file} and SIGNALs "MOP already exists for this CRQ" on a
 * second call. So the header is served by reading those same columns back off
 * the {@code mop} row the procedure wrote, and the procedure itself is called
 * only from the explicit create endpoint.
 *
 * <p>{@code windowStart}, {@code windowEnd}, {@code region} and {@code vendor}
 * are routinely null: region and vendor come from {@code CRQ_DETAIL_TBL},
 * which is still empty, and the window needs a current {@code CRQ_SCHEDULE_TBL}
 * row. They are nullable by design and render as a dash rather than being
 * hidden, so they light up on their own once that data lands.
 */
@Getter
@Setter
public class MopCreateDetailsDto {

    /** The CRQ this MOP belongs to. */
    private String crqNo;

    /** Plan type from CRQ_PLAN_TBL, used as the MOP title. */
    private String title;

    /** Execution start of the current schedule. */
    private LocalDateTime windowStart;

    /** Execution end of the current schedule. */
    private LocalDateTime windowEnd;

    /** Region from CRQ_DETAIL_TBL. */
    private String region;

    /** Vendor from CRQ_DETAIL_TBL. */
    private String vendor;

    /**
     * False until the MOP record has been created for this CRQ. The five
     * fields above are null in that state - they only exist on the {@code mop}
     * row, which the create procedure writes.
     */
    private boolean mopExists;

    /** {@code mop.mop_id}, or null when no MOP has been created yet. */
    private Long mopId;

    /**
     * {@code mop.status} - draft / pending_validation / in_review / rejected /
     * validated / cancelled. Null when no MOP has been created yet.
     */
    private String mopStatus;

    /** True when a MOP document is already stored for this CRQ. */
    private boolean documentAttached;

    /**
     * Format of the stored document - "PDF", "XLSX" or "XLS" - or null when
     * none is attached. Sniffed from the stored bytes rather than read from a
     * column: CRQ_PDF_TBL records neither a MIME type nor a filename. Lets the
     * panel mount the right viewer before it fetches the document itself.
     */
    private String documentType;
}
