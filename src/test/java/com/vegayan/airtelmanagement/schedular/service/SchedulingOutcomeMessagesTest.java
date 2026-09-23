package com.vegayan.airtelmanagement.schedular.service;

import com.vegayan.airtelmanagement.common.exception.StageActionBlockedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the translation of every refusal
 * {@code Update_CRQ_Scheduling_To_Done_Or_Failed} can produce. The strings
 * below are the procedure's own, copied verbatim from its live definition -
 * if the procedure is reworded, this test is where that shows up first.
 */
class SchedulingOutcomeMessagesTest {

    private static final String CRQ = "CRQ000000123";

    @Test
    void missingCrqIsNotFound() {
        StageActionBlockedException ex =
                SchedulingOutcomeMessages.translate("CRQ not found: " + CRQ, CRQ);

        assertEquals(SchedulingOutcomeMessages.CODE_CRQ_NOT_FOUND, ex.getCode());
        assertEquals(404, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains(CRQ));
    }

    @Test
    void wrongStageNamesTheStageTheCrqMovedTo() {
        StageActionBlockedException ex = SchedulingOutcomeMessages.translate(
                "CRQ " + CRQ + " is not in SCHEDULING_APPROVAL (current: EXECUTION)", CRQ);

        assertEquals(SchedulingOutcomeMessages.CODE_STAGE_MISMATCH, ex.getCode());
        assertEquals(409, ex.getHttpStatus());
        // EXECUTION is the enum; the user is shown the stage's label.
        assertTrue(ex.getMessage().contains("Activity Implement"), ex.getMessage());
    }

    @Test
    void wrongStageStillReadableWhenTheStageIsUnknown() {
        StageActionBlockedException ex = SchedulingOutcomeMessages.translate(
                "CRQ " + CRQ + " is not in SCHEDULING_APPROVAL (current: SOMETHING_NEW)", CRQ);

        assertEquals(SchedulingOutcomeMessages.CODE_STAGE_MISMATCH, ex.getCode());
        assertTrue(ex.getMessage().contains("SOMETHING_NEW"), ex.getMessage());
    }

    @Test
    void openOpsTaskIsItsOwnCode() {
        StageActionBlockedException ex = SchedulingOutcomeMessages.translate(
                "Deployment and Operation task is not closed", CRQ);

        assertEquals(SchedulingOutcomeMessages.CODE_OPS_DEPLOY_TASK_OPEN, ex.getCode());
        assertEquals(409, ex.getHttpStatus());
        assertTrue(ex.getHint().toLowerCase().contains("close"));
    }

    @Test
    void pendingCabIsRetryable() {
        StageActionBlockedException ex =
                SchedulingOutcomeMessages.translate("Cab Approval is pending", CRQ);

        assertEquals(SchedulingOutcomeMessages.CODE_CAB_PENDING, ex.getCode());
        assertEquals(409, ex.getHttpStatus());
    }

    @Test
    void rejectedCabIsTerminal() {
        StageActionBlockedException ex = SchedulingOutcomeMessages.translate(
                "Cab Approval request already rejected, So we can't pass CRQ to next stage", CRQ);

        assertEquals(SchedulingOutcomeMessages.CODE_CAB_REJECTED, ex.getCode());
        assertEquals(409, ex.getHttpStatus());
    }

    @Test
    void unrecognisedRefusalKeepsTheProceduresOwnWording() {
        StageActionBlockedException ex =
                SchedulingOutcomeMessages.translate("Some brand new guard fired", CRQ);

        assertEquals(SchedulingOutcomeMessages.CODE_UNKNOWN, ex.getCode());
        assertEquals("Some brand new guard fired", ex.getMessage());
    }

    @Test
    void blankRefusalStillSaysWhichCrq() {
        StageActionBlockedException ex = SchedulingOutcomeMessages.translate("   ", CRQ);

        assertEquals(SchedulingOutcomeMessages.CODE_UNKNOWN, ex.getCode());
        assertTrue(ex.getMessage().contains(CRQ));
    }

    @Test
    void onlyDoneAndFailedReachTheProcedure() {
        assertTrue(SchedulingOutcomeMessages.isSupportedOutcome("DONE"));
        assertTrue(SchedulingOutcomeMessages.isSupportedOutcome("done"));
        assertTrue(SchedulingOutcomeMessages.isSupportedOutcome("Failed"));
        assertTrue(SchedulingOutcomeMessages.isSupportedOutcome(" FAILED "));

        // Cancellation is routed to Update_CRQ_To_Cancel before this check,
        // so anything else here would be a silent no-op inside the procedure.
        assertFalse(SchedulingOutcomeMessages.isSupportedOutcome("Cancelled"));
        assertFalse(SchedulingOutcomeMessages.isSupportedOutcome(""));
        assertFalse(SchedulingOutcomeMessages.isSupportedOutcome(null));
    }

    @Test
    void successCopyNamesTheStageTheCrqMovesInto() {
        assertTrue(SchedulingOutcomeMessages.successMessage("DONE", CRQ)
                .contains("Activity Implement"));
        assertTrue(SchedulingOutcomeMessages.successMessage("FAILED", CRQ)
                .contains("Failed"));
    }
}
