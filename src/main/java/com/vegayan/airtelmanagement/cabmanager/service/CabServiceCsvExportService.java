package com.vegayan.airtelmanagement.cabmanager.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.vegayan.airtelmanagement.common.util.ExcelStyleUtils.applySheetFeatures;
import static com.vegayan.airtelmanagement.common.util.ExcelStyleUtils.createBodyStyle;
import static com.vegayan.airtelmanagement.common.util.ExcelStyleUtils.createHeaderStyle;
import static com.vegayan.airtelmanagement.common.util.ExcelStyleUtils.sanitize;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CabServiceCsvExportService extends BaseService {

    private static final int ROW_ACCESS_WINDOW = 100;
    private static final String DELIMITER = "^";

    private static final String SAFE_ARG = "[A-Za-z0-9_.:/-]+";

    public byte[] exportServiceCsv(String crqNo, String service) throws Exception {

        if (!StringUtils.hasText(crqNo) || !StringUtils.hasText(service)) {
            throw new IllegalArgumentException("crqNo and service are both required.");
        }

        // The arguments are interpolated into a remote shell command, so they
        // are whitelisted rather than escaped - a CRQ number and a service code
        // never legitimately contain shell metacharacters.
        if (!crqNo.matches(SAFE_ARG) || !service.matches(SAFE_ARG)) {
            throw new IllegalArgumentException("crqNo and service contain unsupported characters.");
        }

        String scriptPath = config.getSSH_SERVICE_CSV_SCRIPT_PATH();
        if (!StringUtils.hasText(scriptPath)) {
            throw new IllegalStateException(
                    "SSH_SERVICE_CSV_SCRIPT_PATH is not configured on the server.");
        }

        String stdout = runScript(scriptPath, crqNo, service);

        List<String[]> rows = parseRows(stdout);
        if (rows.isEmpty()) {
            // Distinct from a script failure: the script ran fine and this
            // CRQ/service pair simply has no impacted circuits.
            throw new EmptyResultException(
                    "No rows returned for " + crqNo + " / " + service + ".");
        }

        return buildWorkbook(rows, service);
    }

    /** Thrown when the script succeeds but produces no rows - a 404, not a 500. */
    public static class EmptyResultException extends RuntimeException {
        public EmptyResultException(String message) {
            super(message);
        }
    }

    // ── SSH ─────────────────────────────────────────────────────────────────

    private String runScript(String scriptPath, String crqNo, String service) throws Exception {

        String host = config.getSSH_HOST();
        String username = config.getSSH_USERNAME();
        String password = config.getSSH_PASSWORD();
        int port = Integer.parseInt(config.getSSH_PORT());

        String command = String.format("python3 %s %s %s", scriptPath, crqNo, service);

        LOGGER.info("[CAB SERVICE EXPORT] executing: {}", command);

        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try (
                InputStream inputStream = channel.getInputStream();
                InputStream errorStream = channel.getErrStream()
        ) {
            channel.connect();
            readStream(inputStream, output);
            readStream(errorStream, errorOutput);
        } finally {
            channel.disconnect();
            session.disconnect();
        }

        // stderr alone is not treated as failure - the script can warn while
        // still producing usable rows. It only decides the outcome when stdout
        // came back empty, where it is the only clue as to why.
        if (output.isEmpty() && !errorOutput.isEmpty()) {
            throw new IllegalStateException("Script error: " + errorOutput.toString().trim());
        }
        if (!errorOutput.isEmpty()) {
            LOGGER.warn("[CAB SERVICE EXPORT] script stderr: {}", errorOutput.toString().trim());
        }

        return output.toString();
    }

    private void readStream(InputStream in, StringBuilder target) throws Exception {
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            target.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
    }

    // ── PARSE ───────────────────────────────────────────────────────────────

    private List<String[]> parseRows(String stdout) {
        List<String[]> rows = new ArrayList<>();
        for (String line : stdout.split("\\R")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            rows.add(StringUtils.delimitedListToStringArray(line.trim(), DELIMITER));
        }
        return rows;
    }

    // ── WORKBOOK ────────────────────────────────────────────────────────────

    private byte[] buildWorkbook(List<String[]> rows, String service) throws Exception {

        try (
                SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW);
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            workbook.setCompressTempFiles(true);

            Sheet sheet = workbook.createSheet(sheetName(service));

            // Same formatting as the batch-wise impact export - dark-blue header
            // band on row 0, bordered body, auto-filter, frozen header, fixed
            // widths. Row 0 is styled as the header exactly as writeCsvToSheet
            // does it, so the two workbooks read the same.
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);

            int maxColumns = 0;

            for (int r = 0; r < rows.size(); r++) {
                String[] values = rows.get(r);
                Row row = sheet.createRow(r);

                for (int c = 0; c < values.length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(sanitize(values[c]));
                    cell.setCellStyle(r == 0 ? headerStyle : bodyStyle);
                }

                maxColumns = Math.max(maxColumns, values.length);
            }

            applySheetFeatures(sheet, rows.size(), maxColumns);

            workbook.write(out);
            return out.toByteArray();
        }
    }


    /** Excel sheet names cannot exceed 31 chars or contain : \ / ? * [ ] */
    private String sheetName(String service) {
        String cleaned = service.replaceAll("[:\\\\/?*\\[\\]]", "_");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
