package com.vegayan.airtelmanagement.schedular.service;

import com.vegayan.airtelmanagement.common.exception.StageActionBlockedException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates {@code Update_CRQ_Scheduling_To_Done_Or_Failed}'s raw
 * {@code error_message} rows into a coded, user-facing refusal, and builds
 * the success text for the two outcomes it does accept.
 *
 * <p>The procedure is the authority and is not modified here. It guards the
 * Scheduling -> Execution transition with exactly five checks, each of which
 * rolls the transaction back and selects one {@code error_message}:
 *
 * <ol>
 *   <li>{@code CRQ not found: <no>} - no CRQ_MASTER_TBL row for that crq_no.</li>
 *   <li>{@code CRQ <no> is not in SCHEDULING_APPROVAL (current: <stage>)} -
 *       the CRQ has already moved on (or never arrived).</li>
 *   <li>{@code Deployment and Operation task is not closed} - Pass only;
 *       {@code prep_push_received <> 1}.</li>
 *   <li>{@code Cab Approval is pending} - Pass only; {@code cab_approval_flag = 'PENDING'}.</li>
 *   <li>{@code Cab Approval request already rejected, ...} - Pass only;
 *       {@code cab_approval_flag = 'REJECTED'}.</li>
 * </ol>
 *
 * <p>Matching is done on stable substrings rather than whole strings so a
 * cosmetic reword in the database degrades to the generic branch instead of
 * breaking. Anything unrecognised is passed through verbatim under
 * {@link #CODE_UNKNOWN} - never swallowed.
 */
final class SchedulingOutcomeMessages {

    static final String CODE_CRQ_NOT_FOUND        = "CRQ_NOT_FOUND";
    static final String CODE_STAGE_MISMATCH       = "STAGE_MISMATCH";
    static final String CODE_OPS_DEPLOY_TASK_OPEN = "OPS_DEPLOY_TASK_OPEN";
    static final String CODE_CAB_PENDING          = "CAB_APPROVAL_PENDING";
    static final String CODE_CAB_REJECTED         = "CAB_APPROVAL_REJECTED";
    static final String CODE_INVALID_OUTCOME      = "INVALID_OUTCOME";
    static final String CODE_UNKNOWN              = "STAGE_ACTION_FAILED";

    private static final String STAGE_LABEL = "Scheduling";

    /** Pulls "EXECUTION" out of "... (current: EXECUTION)". */
    private static final Pattern CURRENT_STAGE =
            Pattern.compile("current:\\s*([A-Z_]+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    private SchedulingOutcomeMessages() {
    }

    /**
     * The only two outcomes the procedure acts on. Any other value falls
     * through both of its branches and reaches {@code COMMIT} having changed
     * nothing - which would otherwise be reported to the user as a success.
     */
    static boolean isSupportedOutcome(String localStatus) {
        return localStatus != null
                && ("DONE".equalsIgnoreCase(localStatus.trim())
                 || "FAILED".equalsIgnoreCase(localStatus.trim()));
    }

    static StageActionBlockedException unsupportedOutcome(String localStatus, String crqNo) {
        return new StageActionBlockedException(
                CODE_INVALID_OUTCOME,
                "\"" + localStatus + "\" is not a valid Scheduling outcome.",
                "Record the stage as Pass, Failed or Cancelled.",
                STAGE_LABEL,
                crqNo,
                400);
    }

    /** Success copy - the procedure returns no success_message of its own. */
    static String successMessage(String localStatus, String crqNo) {
        return "DONE".equalsIgnoreCase(localStatus.trim())
                ? "Scheduling completed for CRQ " + crqNo + ". It has moved to Activity Implement."
                : "Scheduling marked as Failed for CRQ " + crqNo + ".";
    }

    /**
     * @param procMessage the procedure's {@code error_message}, verbatim
     * @param crqNo       the CRQ the action was attempted on
     */
    static StageActionBlockedException translate(String procMessage, String crqNo) {

        String raw = procMessage == null ? "" : procMessage.trim();
        String lower = raw.toLowerCase();

        if (lower.startsWith("crq not found")) {
            return blocked(CODE_CRQ_NOT_FOUND,
                    "CRQ " + crqNo + " no longer exists in the workflow.",
                    "Refresh the Scheduling list and open the CRQ again.",
                    crqNo, 404);
        }

        if (lower.contains("is not in scheduling_approval")) {
            return blocked(CODE_STAGE_MISMATCH,
                    "CRQ " + crqNo + " is no longer at the Scheduling stage"
                            + currentStageSuffix(raw) + ".",
                    "Someone else has already actioned it. Refresh to load its current stage.",
                    crqNo, 409);
        }

        if (lower.contains("deployment and operation task is not closed")) {
            return blocked(CODE_OPS_DEPLOY_TASK_OPEN,
                    "The Deployment & Operation task for CRQ " + crqNo + " is still open, "
                            + "so Scheduling cannot be passed yet.",
                    "Close the Deployment & Operation task first, then submit Pass again. "
                            + "Failed and Cancelled can still be recorded now.",
                    crqNo, 409);
        }

        if (lower.contains("cab approval is pending")) {
            return blocked(CODE_CAB_PENDING,
                    "CAB approval for CRQ " + crqNo + " is still pending.",
                    "Wait for the CAB decision - or follow it up in CAB Manager - "
                            + "before passing this stage.",
                    crqNo, 409);
        }

        if (lower.contains("already rejected")) {
            return blocked(CODE_CAB_REJECTED,
                    "CAB has rejected the approval request for CRQ " + crqNo + ", "
                            + "so it cannot move to Activity Implement.",
                    "Record this stage as Failed or Cancelled, or raise a fresh CAB request.",
                    crqNo, 409);
        }

        // Unrecognised - surface the procedure's own wording rather than a
        // guess, so a newly added guard is still reported accurately.
        return blocked(CODE_UNKNOWN,
                raw.isEmpty() ? "Scheduling could not be updated for CRQ " + crqNo + "." : raw,
                "No change was made. Resolve the reported issue and try again.",
                crqNo, 409);
    }

    /** " - it has moved to Activity Implement", when the procedure said which. */
    private static String currentStageSuffix(String procMessage) {
        Matcher m = CURRENT_STAGE.matcher(procMessage);
        if (!m.find()) return "";
        String stage = m.group(1).toUpperCase();
        String label = CrqWorkflowService.STAGE_LABELS.getOrDefault(stage, stage);
        return " - it has moved to " + label;
    }

    private static StageActionBlockedException blocked(String code, String message, String hint,
                                                       String crqNo, int httpStatus) {
        return new StageActionBlockedException(code, message, hint, STAGE_LABEL, crqNo, httpStatus);
    }
}
