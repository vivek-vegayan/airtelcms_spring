package com.vegayan.airtelmanagement.impactbatch.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import static com.vegayan.airtelmanagement.common.util.ExcelStyleUtils.applySheetFeatures;
import static com.vegayan.airtelmanagement.common.util.ExcelStyleUtils.createBodyStyle;
import static com.vegayan.airtelmanagement.common.util.ExcelStyleUtils.createHeaderStyle;
import static com.vegayan.airtelmanagement.common.util.ExcelStyleUtils.sanitize;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactBatchFileService extends BaseService {
    private final SessionFactory<?> sessionFactory;

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int ROW_ACCESS_WINDOW = 100;

    private static final Map<String, Integer> FILE_PRIORITY =
            Map.of(
                    "IP", 1,
                    "BTP", 2,
                    "MSAN", 3,
                    "PACKET", 4,
                    "OTN", 5
            );



    public byte[] generateImpactBatchExcel(List<String> fileNames) throws Exception {

        log.info("Generating Excel for {} files", fileNames.size());

        try (
                SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW);
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {

            workbook.setCompressTempFiles(true);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);

            List<String> orderedFiles = sortFiles(fileNames);

            processFiles(workbook, orderedFiles, headerStyle, bodyStyle);

            validateWorkbook(workbook);

            workbook.setForceFormulaRecalculation(false);
            workbook.write(out);

            return out.toByteArray();
        }
    }

    // SORT FILES
    private List<String> sortFiles(List<String> fileNames) {

        return fileNames.stream()
                .sorted(Comparator.comparingInt(this::getFilePriority))
                .collect(Collectors.toList());
    }

    private int getFilePriority(String fileName) {

        for (Map.Entry<String, Integer> entry : FILE_PRIORITY.entrySet()) {
            if (fileName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 999;
    }

    // PROCESS FILES
    private void processFiles(
            SXSSFWorkbook workbook,
            List<String> orderedFiles,
            CellStyle headerStyle,
            CellStyle bodyStyle
    ) {

        try (Session<?> session = sessionFactory.getSession()) {

            for (String fileName : orderedFiles) {

                try {
                    log.info("Processing file: {}", fileName);

                    processSingleFile(
                            workbook,
                            session,
                            fileName,
                            headerStyle,
                            bodyStyle
                    );

                } catch (Exception ex) {
                    log.error("Error processing file: {}", fileName, ex);
                }
            }

        } catch (Exception ex) {
            throw new RuntimeException("SFTP session failed", ex);
        }
    }

    // SINGLE FILE
    private void processSingleFile(
            SXSSFWorkbook workbook,
            Session<?> session,
            String fileName,
            CellStyle headerStyle,
            CellStyle bodyStyle
    ) throws Exception {

        String remoteFile = buildRemoteFilePath(fileName);

        if (!session.exists(remoteFile)) {
            log.warn("File not found: {}", remoteFile);
            return;
        }

        try (
                InputStream is = session.readRaw(remoteFile);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8),
                        BUFFER_SIZE
                )
        ) {

            Sheet sheet = workbook.createSheet(
                    generateUniqueSheetName(workbook, resolveSheetName(fileName))
            );

            writeCsvToSheet(reader, sheet, headerStyle, bodyStyle);
        }
    }

    // WRITE CSV
    private void writeCsvToSheet(
            BufferedReader reader,
            Sheet sheet,
            CellStyle headerStyle,
            CellStyle bodyStyle
    ) throws IOException {

        String line;
        int rowNum = 0;
        int maxColumns = 0;

        while ((line = reader.readLine()) != null) {

            Row row = sheet.createRow(rowNum);

            String[] columns =
                    StringUtils.delimitedListToStringArray(line, "^");

            maxColumns = Math.max(maxColumns, columns.length);

            for (int col = 0; col < columns.length; col++) {

                Cell cell = row.createCell(col);
                cell.setCellValue(sanitize(columns[col]));

                if (rowNum == 0) {
                    cell.setCellStyle(headerStyle);
                } else {
                    cell.setCellStyle(bodyStyle);
                }
            }

            rowNum++;
        }

        applySheetFeatures(sheet, rowNum, maxColumns);
    }

    // SHEET FEATURES (FIXED FILTER + BORDER)

    // STYLES (HEADER + BODY BORDER FIX)


    // HELPERS
    private String buildRemoteFilePath(String fileName) {
        return config.getSFTP_BATCHWISE_IMPACT_EXCEL_PATH()
                + "/" + fileName;
    }


    private String resolveSheetName(String fileName) {
        if (fileName.startsWith("IP")) return "IP";
        if (fileName.startsWith("BTP")) return "IP BTP";
        if (fileName.startsWith("MSAN")) return "IP MSAN";
        if (fileName.startsWith("PACKET")) return "Packet";
        if (fileName.startsWith("OTN")) return "OTN";
        return "OTHER";
    }

    private String generateUniqueSheetName(
            Workbook workbook,
            String baseName
    ) {

        String name = baseName;
        int i = 1;

        while (workbook.getSheet(name) != null) {
            name = baseName + "_" + i++;
        }

        return name;
    }

    private void validateWorkbook(Workbook workbook)
            throws FileNotFoundException {

        if (workbook.getNumberOfSheets() == 0) {
            throw new FileNotFoundException("No CSV files found");
        }
    }
}
