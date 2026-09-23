package com.vegayan.airtelmanagement.common.util;

/**
 * Renders a stored-procedure invocation as a statement that can be pasted
 * straight into a MySQL client.
 *
 * <p>The hand-rolled {@code "call Foo('{}','{}')"} log lines this replaces
 * were not actually runnable: a null argument printed as the four letters
 * {@code null} rather than {@code NULL}, and an apostrophe inside a remark
 * ("Didn't pass") closed the literal and corrupted the rest of the line.
 * Both matter here - most of the cancellation parameters are optional and
 * arrive null, and remarks are free text typed by users.
 */
public final class ProcedureCallFormatter {

    private ProcedureCallFormatter() {
    }

    /**
     * @return e.g. {@code CALL Update_CRQ_Scheduling_To_Done_Or_Failed('OLM1','CRQ01','12','DONE',NULL);}
     */
    public static String render(String procedureName, Object... args) {
        StringBuilder sb = new StringBuilder("CALL ").append(procedureName).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(literal(args[i]));
        }
        return sb.append(");").toString();
    }

    /**
     * Fills a prepared statement's {@code ?} placeholders with the values that
     * were actually bound to it, so a log line reproduces the exact call.
     *
     * <p>This is the form the JDBC layer sees - {@code "CALL Foo(?,?)"} plus an
     * argument array - which is why it, and not {@link #render}, is what the
     * automatic logging uses: nothing has to be written out by hand a second
     * time, so a log line cannot drift from the call it claims to describe.
     *
     * <p>A statement whose placeholder count does not match the argument count
     * is returned with its placeholders intact and the arguments appended,
     * rather than being silently mis-filled - a wrong-but-plausible statement
     * in a log is worse than an obviously incomplete one.
     *
     * @return e.g. {@code CALL Get_MOP_Create_Details(404,2,6);}
     */
    public static String renderPrepared(String sql, Object... args) {
        if (sql == null) {
            return "";
        }
        Object[] values = args == null ? new Object[0] : args;
        if (countPlaceholders(sql) != values.length) {
            StringBuilder sb = new StringBuilder(sql.trim());
            sb.append("  -- args: ");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(literal(values[i]));
            }
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder(sql.length() + 64);
        boolean inString = false;
        int next = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            // Placeholders only count outside a literal - a '?' inside one is
            // data, not a bind slot.
            if (c == '\'') {
                inString = !inString;
                sb.append(c);
            } else if (c == '?' && !inString) {
                sb.append(literal(values[next++]));
            } else {
                sb.append(c);
            }
        }
        String rendered = sb.toString().trim();
        return rendered.endsWith(";") ? rendered : rendered + ";";
    }

    /** Bind slots in the statement - '?' outside any string literal. */
    private static int countPlaceholders(String sql) {
        int count = 0;
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') inString = !inString;
            else if (c == '?' && !inString) count++;
        }
        return count;
    }

    /**
     * Longest string argument reproduced in full. Past this the value is cut,
     * because arguments here are not all small: a MOP document reaches
     * SP_STORE_CRQ_MOP_CREATE_PDF as ~33 MB of base64, which would otherwise
     * be written to the console and to every file appender on every upload.
     */
    private static final int MAX_LITERAL_CHARS = 256;

    /** SQL literal for one argument - unquoted for numbers, NULL for null. */
    private static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return "<binary, " + bytes.length + " bytes>";
        }
        String raw = value.toString();
        if (raw.length() > MAX_LITERAL_CHARS) {
            // Deliberately not a valid literal: a truncated value must not
            // look like something that can be pasted and run.
            return "'" + escape(raw.substring(0, MAX_LITERAL_CHARS))
                    + "'<truncated, " + raw.length() + " chars total>";
        }
        return "'" + escape(raw) + "'";
    }

    /**
     * Escapes what would otherwise break the statement or the log line:
     * backslash and quote per MySQL's own rules, newlines/tabs so one call
     * stays on one line.
     */
    private static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
