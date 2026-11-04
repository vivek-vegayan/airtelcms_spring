package com.vegayan.airtelmanagement.activity.service;

import java.util.List;

import static org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties.UiService.LOGGER;

import com.vegayan.airtelmanagement.activity.dto.PlanActivityExportRowDto;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidation;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PlanActivityExcelExportService extends BaseService {

    private static final String TEMPLATE_PATH = "excel-templates/Plan_Activity_Upload_Template.xlsx";
    private static final String UPLOAD_SHEET_NAME = "Upload";
    private static final int DATA_ROW_START = 2;      // 0-based row index (Excel row 3)
    private static final int TEMPLATE_LAST_ROW = 151; // 0-based row index (Excel row 152) — 25 blocks of 6
    private static final int TEMPLATE_ROW_COUNT = TEMPLATE_LAST_ROW - DATA_ROW_START + 1;


    private static final int STYLE_ROW_START = 8;
    private static final int STYLE_ROW_COUNT = 12;

    // Upload sheet columns (0-based) — same as PlanActivityExcelService
    private static final int COL_REF = 0, COL_VERTICAL = 1, COL_FUNCTION = 2, COL_DOMAIN = 3, COL_SUB_DOMAIN = 4,
            COL_NETWORK_DOMAIN = 5, COL_LAYER = 6, COL_PLAN_TYPE = 7, COL_VENDOR = 8, COL_IMPACT = 9,
            COL_ACTIVITY_NAME = 10, COL_PHASE = 11, COL_SHIFT = 12, COL_LEVEL = 13, COL_TIME = 14,
            COL_TEAM = 15, COL_DAYS_MARGIN = 16, COL_RESERVATION_MARGIN = 17, COL_ROLLBACK_TIME = 18,
            COL_SEND_TO_CAB = 19, COL_ROW_CHECK = 20;

    private static final String CRQ_REVIEW = "CRQ Review";
    private static final String EXECUTION = "Execution";
    private static final List<String> PHASES = List.of(
            CRQ_REVIEW, "Impact Analysis", "Scheduling", "MOP Creation", "MOP Validation", EXECUTION);

    // Column U helper, same formula as the template (%1$d = Excel row number).
    private static final String ROW_CHECK_FORMULA =
            "IF($A%1$d=\"\",\"\",IF($L%1$d=\"CRQ Review\",IF(COUNTBLANK($B%1$d:$K%1$d)>0,\"Fill plan + activity\","
                    + "IF($O%1$d=\"\",\"Time (Min) missing\",\"OK\")),IF($O%1$d=\"\",\"Time (Min) missing\",\"OK\")))";

    public byte[] exportPlanActivities(Long actorUserId, Long verticalId, Long functionId, Long domainId,
                                       Long subDomainId, String statusFilter) throws IOException {

        String sql = "CALL sp_export_plan_activity_excel(?,?,?,?,?,?)";
        LOGGER.info("call sp_export_plan_activity_excel('{}','{}','{}','{}','{}','{}');",
                actorUserId, verticalId, functionId, domainId, subDomainId, statusFilter);

        List<PlanActivityExportRowDto> rows = databaseUtils.executeProcedureAndFetchObjectsV1(
                jdbcTemplateTwo, sql, PlanActivityExportRowDto.class,
                actorUserId, verticalId, functionId, domainId, subDomainId, statusFilter);

        // One block per plan + activity, phases keyed by name; the procedure already orders them.
        Map<String, Map<String, PlanActivityExportRowDto>> activities = new LinkedHashMap<>();
        for (PlanActivityExportRowDto r : rows) {
            activities.computeIfAbsent(r.getPlanId() + "|" + r.getActivityId(),
                    k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)).put(r.getPhaseName(), r);
        }

        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.getSheet(UPLOAD_SHEET_NAME);
            CellStyle[][] styles = snapshotStyles(sheet);
            clearTemplateData(sheet);

            int rowIdx = DATA_ROW_START;
            int refNo = 1;
            for (Map<String, PlanActivityExportRowDto> byPhase : activities.values()) {
                PlanActivityExportRowDto activity = byPhase.values().iterator().next();
                String ref = String.format("ACT-%03d", refNo++);
                // Always 6 rows so every block lines up with the template's phase rows/colours.
                for (String phase : PHASES) {
                    writeRow(sheet, styles, rowIdx++, ref, phase, activity, byPhase.get(phase));
                }
            }

            extendValidations(sheet, rowIdx - 1);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeRow(XSSFSheet sheet, CellStyle[][] styles, int rowIdx, String ref, String phase,
                          PlanActivityExportRowDto activity, PlanActivityExportRowDto config) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        // Rows past the template reuse its styles, 150 rows (25 blocks) per cycle.
        CellStyle[] rowStyles = styles[(rowIdx - DATA_ROW_START) % STYLE_ROW_COUNT];

        Object[] values = new Object[COL_ROW_CHECK];
        values[COL_REF] = ref;
        values[COL_PHASE] = phase;

        if (CRQ_REVIEW.equals(phase)) {
            values[COL_VERTICAL] = activity.getVerticalName();
            values[COL_FUNCTION] = activity.getFunctionName();
            values[COL_DOMAIN] = activity.getChmDomain();
            values[COL_SUB_DOMAIN] = activity.getChmSubDomain();
            values[COL_NETWORK_DOMAIN] = activity.getNetworkDomain();
            values[COL_LAYER] = activity.getLayer();
            values[COL_PLAN_TYPE] = activity.getPlanType();
            values[COL_VENDOR] = activity.getVendorOem();
            values[COL_IMPACT] = activity.getChangeImpact();
            values[COL_ACTIVITY_NAME] = activity.getActivityName();
            values[COL_SEND_TO_CAB] = activity.getSendToCab();
        }

        if (config != null) {
            values[COL_SHIFT] = config.getShift();
            values[COL_LEVEL] = config.getMinimumLevelRequirement();
            values[COL_TIME] = config.getRequiredTimeMinutes();
            values[COL_TEAM] = config.getTeamPath();
            // Upload only reads the margins on the Execution row.
            if (EXECUTION.equals(phase)) {
                values[COL_DAYS_MARGIN] = config.getDaysMargin();
                values[COL_RESERVATION_MARGIN] = config.getReservationMargin();
                values[COL_ROLLBACK_TIME] = config.getRollbackTime();
            }
        }

        for (int c = COL_REF; c < COL_ROW_CHECK; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) cell = row.createCell(c);
            if (rowStyles[c] != null) cell.setCellStyle(rowStyles[c]);

            Object v = values[c];
            if (v == null) cell.setBlank();
            else if (v instanceof Number n) cell.setCellValue(n.doubleValue());
            else cell.setCellValue(v.toString());
        }

        // Template rows already carry the Row Check formula; rows past it need one.
        Cell check = row.getCell(COL_ROW_CHECK);
        if (check == null || check.getCellType() != CellType.FORMULA) {
            if (check == null) check = row.createCell(COL_ROW_CHECK);
            if (rowStyles[COL_ROW_CHECK] != null) check.setCellStyle(rowStyles[COL_ROW_CHECK]);
            check.setCellFormula(String.format(ROW_CHECK_FORMULA, rowIdx + 1));
        }
    }

    private CellStyle[][] snapshotStyles(XSSFSheet sheet) {
        CellStyle[][] styles = new CellStyle[STYLE_ROW_COUNT][COL_ROW_CHECK + 1];
        for (int i = 0; i < STYLE_ROW_COUNT; i++) {
            Row row = sheet.getRow(STYLE_ROW_START + i);
            if (row == null) continue;
            for (int c = COL_REF; c <= COL_ROW_CHECK; c++) {
                Cell cell = row.getCell(c);
                if (cell != null) styles[i][c] = cell.getCellStyle();
            }
        }
        return styles;
    }


    /** Drops the template's sample values; Activity Ref (A), Phase (L) and Row Check (U) stay,
     *  so blocks after the exported ones are still empty template blocks for new activities. */
    private void clearTemplateData(XSSFSheet sheet) {
        for (int r = DATA_ROW_START; r <= TEMPLATE_LAST_ROW; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = COL_VERTICAL; c <= COL_SEND_TO_CAB; c++) {
                if (c == COL_PHASE) continue;
                Cell cell = row.getCell(c);
                if (cell != null) cell.setBlank();
            }
        }
    }

    /** Template dropdowns stop at row 152; stretch them over any rows written past it. */
    private void extendValidations(XSSFSheet sheet, int lastDataRow) {
        if (lastDataRow <= TEMPLATE_LAST_ROW) return;
        CTDataValidations validations = sheet.getCTWorksheet().getDataValidations();
        if (validations == null) return;

        for (CTDataValidation dv : validations.getDataValidationArray()) {
            List<String> refs = new ArrayList<>();
            for (Object ref : dv.getSqref()) {
                CellRangeAddress range = CellRangeAddress.valueOf(ref.toString());
                if (range.getFirstRow() == DATA_ROW_START && range.getLastRow() == TEMPLATE_LAST_ROW) {
                    // Whole-column range (e.g. F3:F152)
                    range.setLastRow(lastDataRow);
                    refs.add(range.formatAsString());
                    continue;
                }
                refs.add(range.formatAsString());
                // Per-block range (e.g. Execution-only Q14:S14) — repeat it every 150 rows.
                if (range.getFirstRow() >= DATA_ROW_START && range.getLastRow() <= TEMPLATE_LAST_ROW) {
                    for (int shift = TEMPLATE_ROW_COUNT; range.getFirstRow() + shift <= lastDataRow;
                         shift += TEMPLATE_ROW_COUNT) {
                        refs.add(new CellRangeAddress(range.getFirstRow() + shift,
                                Math.min(range.getLastRow() + shift, lastDataRow),
                                range.getFirstColumn(), range.getLastColumn()).formatAsString());
                    }
                }
            }
            dv.setSqref(refs);
        }
    }
}
