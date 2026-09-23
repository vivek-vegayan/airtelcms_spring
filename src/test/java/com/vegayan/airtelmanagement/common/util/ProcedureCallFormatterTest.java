package com.vegayan.airtelmanagement.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcedureCallFormatterTest {

    @Test
    void rendersARunnableCall() {
        assertEquals(
                "CALL Update_CRQ_Scheduling_To_Done_Or_Failed('OLM1', 'CRQ01', '12', 'DONE', 'ok');",
                ProcedureCallFormatter.render("Update_CRQ_Scheduling_To_Done_Or_Failed",
                        "OLM1", "CRQ01", "12", "DONE", "ok"));
    }

    @Test
    void nullBecomesSqlNullNotTheWordNull() {
        // The optional cancellation params arrive null far more often than not,
        // and 'null' would be a five-character string once pasted into MySQL.
        assertEquals("CALL P('a', NULL, 'b');",
                ProcedureCallFormatter.render("P", "a", null, "b"));
    }

    @Test
    void apostropheInARemarkDoesNotCloseTheLiteral() {
        assertEquals("CALL P('Didn\\'t pass');",
                ProcedureCallFormatter.render("P", "Didn't pass"));
    }

    @Test
    void backslashIsEscaped() {
        assertEquals("CALL P('C:\\\\temp');",
                ProcedureCallFormatter.render("P", "C:\\temp"));
    }

    @Test
    void newlinesStayOnOneLogLine() {
        assertEquals("CALL P('line1\\nline2');",
                ProcedureCallFormatter.render("P", "line1\nline2"));
    }

    @Test
    void numbersAndBooleansAreNotQuoted() {
        assertEquals("CALL P(42, true, '7');",
                ProcedureCallFormatter.render("P", 42, true, "7"));
    }

    @Test
    void noArgsStillRenders() {
        assertEquals("CALL P();", ProcedureCallFormatter.render("P"));
    }

    // ---------------- renderPrepared: what the automatic logging uses --------

    @Test
    void fillsPlaceholdersFromTheBoundArguments() {
        assertEquals("CALL Get_MOP_Create_Details(404,2,6);",
                ProcedureCallFormatter.renderPrepared(
                        "CALL Get_MOP_Create_Details(?,?,?)", 404L, 2L, 6L));
    }

    @Test
    void addsTheTrailingSemicolonOnlyWhenMissing() {
        assertEquals("CALL P('a');", ProcedureCallFormatter.renderPrepared("CALL P(?)", "a"));
        assertEquals("CALL P('a');", ProcedureCallFormatter.renderPrepared("CALL P(?);", "a"));
    }

    @Test
    void preparedNullBecomesSqlNull() {
        assertEquals("CALL P('a',NULL);",
                ProcedureCallFormatter.renderPrepared("CALL P(?,?)", "a", null));
    }

    @Test
    void preparedEscapesQuotesSoTheLineStaysRunnable() {
        assertEquals("CALL P('Didn\\'t pass');",
                ProcedureCallFormatter.renderPrepared("CALL P(?)", "Didn't pass"));
    }

    @Test
    void aQuestionMarkInsideALiteralIsNotAPlaceholder() {
        // 'Done?' is data. Treating it as a bind slot would consume an argument
        // and shift every value after it onto the wrong parameter.
        assertEquals("CALL P('Done?','x');",
                ProcedureCallFormatter.renderPrepared("CALL P('Done?',?)", "x"));
    }

    @Test
    void argumentCountMismatchIsNotSilentlyMisFilled() {
        // A plausible-but-wrong statement in a log is worse than an obviously
        // incomplete one - the placeholders stay put and the args are appended.
        assertEquals("CALL P(?,?)  -- args: 'only-one'",
                ProcedureCallFormatter.renderPrepared("CALL P(?,?)", "only-one"));
    }

    @Test
    void hugeArgumentIsTruncatedAndMarkedUnrunnable() {
        // A MOP document reaches SP_STORE_CRQ_MOP_CREATE_PDF as ~33 MB of
        // base64; logging it whole would flood the console and every appender.
        String huge = "A".repeat(5000);
        String rendered = ProcedureCallFormatter.renderPrepared("CALL P(?)", huge);

        assertTrue(rendered.length() < 400, "expected a truncated line, got " + rendered.length());
        assertTrue(rendered.contains("<truncated, 5000 chars total>"), rendered);
    }

    @Test
    void binaryArgumentIsSummarisedNotDumped() {
        assertEquals("CALL P(<binary, 3 bytes>);",
                ProcedureCallFormatter.renderPrepared("CALL P(?)", new byte[] {1, 2, 3}));
    }

    @Test
    void noArgsPreparedStillRenders() {
        assertEquals("CALL P();", ProcedureCallFormatter.renderPrepared("CALL P()"));
    }
}
