package com.vegayan.airtelmanagement.common.exception;

/**
 * A stage outcome (Pass / Failed) that the stored procedure refused.
 *
 * <p>The {@code *_To_Done_Or_Failed} procedures validate their own
 * preconditions, and when one fails they roll the transaction back and
 * return a single-column {@code error_message} result set instead of
 * throwing. Nothing in the CRQ changes in that case, so the caller must be
 * told the action did <em>not</em> happen - and told precisely why.
 *
 * <p>Alongside the human message this carries a stable {@link #getCode()
 * code} so the UI can pick the right copy and the right follow-up action
 * (refresh, chase CAB, choose a different outcome) without string-matching
 * the procedure's wording, which lives in the database and can be reworded
 * there at any time.
 */
public class StageActionBlockedException extends BusinessException {

    private final String code;
    private final String hint;
    private final String stage;
    private final String crqNo;
    private final int httpStatus;

    public StageActionBlockedException(String code,
                                       String message,
                                       String hint,
                                       String stage,
                                       String crqNo,
                                       int httpStatus) {
        super(message);
        this.code = code;
        this.hint = hint;
        this.stage = stage;
        this.crqNo = crqNo;
        this.httpStatus = httpStatus;
    }

    /** Stable machine code, e.g. {@code CAB_APPROVAL_PENDING}. */
    public String getCode() {
        return code;
    }

    /** What the user should do next, in one sentence. May be null. */
    public String getHint() {
        return hint;
    }

    /** Human label of the stage that refused the action, e.g. "Scheduling". */
    public String getStage() {
        return stage;
    }

    public String getCrqNo() {
        return crqNo;
    }

    /** 404 when the CRQ itself is gone, 400 for a bad outcome, else 409. */
    public int getHttpStatus() {
        return httpStatus;
    }
}
