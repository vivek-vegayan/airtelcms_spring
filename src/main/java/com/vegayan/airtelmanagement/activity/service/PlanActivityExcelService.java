package com.vegayan.airtelmanagement.activity.service;

import com.vegayan.airtelmanagement.activity.dto.ActivityPhaseViewDTO;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelParseResponseDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelRowDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelRowResultDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelUploadSummaryDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityInsertResultDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityValidationErrorDto;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.schedular.dto.PlanDetailsDto;
import com.vegayan.airtelmanagement.schedular.service.PlanSetupService;
import com.vegayan.airtelmanagement.user.dto.DomainDto;
import com.vegayan.airtelmanagement.user.dto.OrgHierarchyResponse;
import com.vegayan.airtelmanagement.user.dto.SubDomainDto;
import com.vegayan.airtelmanagement.user.dto.TeamFunctionDto;
import com.vegayan.airtelmanagement.user.dto.VerticalDto;
import com.vegayan.airtelmanagement.user.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
public class PlanActivityExcelService extends BaseService {

    private final UserService userService;
    private final ActivityService activityService;
    private final PlanSetupService planSetupService;

    public PlanActivityExcelService(UserService userService, ActivityService activityService,
                                     PlanSetupService planSetupService) {
        this.userService = userService;
        this.activityService = activityService;
        this.planSetupService = planSetupService;
    }

    private static final String UPLOAD_SHEET_NAME = "Plan_Activity_Upload";
    private static final String MASTER_SHEET_NAME = "MASTER_DATA";
    private static final String INSTRUCTIONS_SHEET_NAME = "Instructions";
    private static final int DATA_ROW_START = 1; // 0-based row index (Excel row 2)
    private static final int DATA_ROW_END = 50;

    private static final List<String> LAYER_OPTIONS = List.of(
            "Access", "Aggregation", "Core", "Backhaul", "Transmission", "IP/MPLS");
    private static final List<String> PLAN_TYPE_OPTIONS = List.of(
            "IMPLEMENTATION", "Upgrade", "Greenfield", "Rollout", "Migration", "Decommission", "Maintenance");
    private static final List<String> CHANGE_IMPACT_OPTIONS = List.of("SA", "NSA");
    private static final List<String> SHIFT_OPTIONS = List.of("A", "B", "G", "LG", "N");
    private static final List<String> LEVEL_OPTIONS = List.of("L1", "L2", "L3", "L4");

    private static final String[] HEADERS = {
            "Vertical*", "Team Function*", "CHM Domain*", "CHM Sub Domain*",
            "Layer*", "Plan Type*", "Vendor / OEM*", "Change Impact*",
            "Activity Name*",
            "CRQ Review Shift*", "CRQ Review Min Level*", "CRQ Review Time (Min)*", "CRQ Review Team*",
            "Impact Analysis Shift*", "Impact Analysis Min Level*", "Impact Analysis Time (Min)*", "Impact Analysis Team*",
            "Scheduling Shift*", "Scheduling Min Level*", "Scheduling Time (Min)*", "Scheduling Team*",
            "MOP Create Shift*", "MOP Create Min Level*", "MOP Create Time (Min)*", "MOP Create Team*",
            "MOP Validate Shift*", "MOP Validate Min Level*", "MOP Validate Time (Min)*", "MOP Validate Team*",
            "CRQ Execution Shift*", "CRQ Execution Min Level*", "CRQ Execution Time (Min)*",
            "CRQ Execution Days Margin*", "CRQ Execution Reservation Margin*", "CRQ Execution Rollback Time*", "CRQ Execution Team*"
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Template generation
    // ─────────────────────────────────────────────────────────────────────────

    public Workbook generateTemplate(Long actorUserId) {
        OrgHierarchyResponse hierarchy = userService.getOrgHierarchyByUserV1(actorUserId);
        return buildTemplate(hierarchy);
    }

    private Workbook buildTemplate(OrgHierarchyResponse hierarchy) {
        Workbook workbook = new XSSFWorkbook();
        Sheet instructionsSheet = workbook.createSheet(INSTRUCTIONS_SHEET_NAME);
        Sheet uploadSheet = workbook.createSheet(UPLOAD_SHEET_NAME);
        Sheet masterSheet = workbook.createSheet(MASTER_SHEET_NAME);

        createInstructionsSheet(workbook, instructionsSheet);
        createHeader(workbook, uploadSheet);
        writeSampleRow(uploadSheet, hierarchy);
        applyAutoFilter(uploadSheet);
        autoFitColumns(uploadSheet, HEADERS.length);
        createMasterData(workbook, masterSheet, hierarchy);
        applyDropdowns(workbook, uploadSheet);

        uploadSheet.createFreezePane(0, 1);
        workbook.setActiveSheet(workbook.getSheetIndex(UPLOAD_SHEET_NAME));
        return workbook;
    }

    private void applyAutoFilter(Sheet sheet) {
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
    }

    private void autoFitColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.max(3000, Math.min(sheet.getColumnWidth(i), 7500));
            sheet.setColumnWidth(i, width);
        }
    }

    private void createInstructionsSheet(Workbook workbook, Sheet sheet) {
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        titleStyle.setFont(titleFont);

        CellStyle sectionStyle = workbook.createCellStyle();
        Font sectionFont = workbook.createFont();
        sectionFont.setBold(true);
        sectionFont.setFontHeightInPoints((short) 11);
        sectionStyle.setFont(sectionFont);

        CellStyle bodyStyle = workbook.createCellStyle();
        bodyStyle.setWrapText(true);
        Font bodyFont = workbook.createFont();
        bodyFont.setFontHeightInPoints((short) 10);
        bodyStyle.setFont(bodyFont);

        String[] lines = {
                "TITLE::Plan & Activity — Bulk Excel Upload — Instructions",
                "",
                "SECTION::How this works",
                "BODY::Fill one row per Activity on the 'Plan_Activity_Upload' sheet. Each row creates (or reuses, if an identical Plan already exists) one Plan and always creates one new Activity with its 6 phases: CRQ Review, Impact Analysis, Scheduling, MOP Create, MOP Validate, CRQ Execution.",
                "BODY::The uploading user is taken automatically from your logged-in session — there is no 'Actor User Id' column to fill in.",
                "",
                "SECTION::Sheet names — do not rename",
                "BODY::Do not rename or delete the 'Plan_Activity_Upload' or 'MASTER_DATA' sheet tabs. The upload reads the 'Plan_Activity_Upload' sheet by name, and the dropdowns on it reference named ranges defined on 'MASTER_DATA'.",
                "",
                "SECTION::Mandatory fields",
                "BODY::Every column marked with * on the header row is mandatory. Rows with missing mandatory values will be listed in the Validation Errors grid and will not be uploaded.",
                "",
                "SECTION::Organization Hierarchy columns",
                "BODY::Vertical, Team Function, CHM Domain and CHM Sub Domain must be chosen in order, left to right — each dropdown only offers valid children of the value chosen to its left. Type only using the dropdown; free-text values that don't match an existing name will fail validation.",
                "",
                "SECTION::Assigned Team columns",
                "BODY::Each phase's 'Assigned Team' represents your organization's Sub Domain / team unit. Pick a value from the Team dropdown — do not type free text. An unrecognized team name will fail validation and the row will not be uploaded.",
                "",
                "SECTION::Allowed values",
                "BODY::Shift: A, B, G, LG, N.",
                "BODY::Minimum Level: L1, L2, L3, L4.",
                "BODY::Layer, Plan Type and Change Impact must match one of the values offered in their dropdown.",
                "BODY::Time (Min), Days Margin, Reservation Margin and Rollback Time must be whole, non-negative numbers.",
                "",
                "SECTION::Duplicates",
                "BODY::Rows that share the same Plan combination (Domain / Sub Domain / Layer / Plan Type / Vendor / Change Impact) and the same Activity Name — either within this file or against an Activity that already exists for that Plan — are flagged as duplicates and will not be uploaded.",
                "",
                "SECTION::Before you upload",
                "BODY::1. Fill in the 'Plan_Activity_Upload' sheet using the dropdowns.",
                "BODY::2. Upload the file — invalid rows are shown in a Validation Errors grid before anything is saved.",
                "BODY::3. Fix and re-upload, or continue — only valid rows are sent to the system.",
                "BODY::4. Review the Upload Summary once the valid rows have been processed.",
                "",
                "BODY::Need help? Contact your CHM administrator.",
        };

        sheet.setColumnWidth(0, 22000);
        int rowIdx = 0;
        for (String line : lines) {
            Row row = sheet.createRow(rowIdx);
            Cell cell = row.createCell(0);
            if (line.startsWith("TITLE::")) {
                cell.setCellValue(line.substring("TITLE::".length()));
                cell.setCellStyle(titleStyle);
                row.setHeightInPoints(24);
            } else if (line.startsWith("SECTION::")) {
                cell.setCellValue(line.substring("SECTION::".length()));
                cell.setCellStyle(sectionStyle);
            } else if (line.startsWith("BODY::")) {
                cell.setCellValue(line.substring("BODY::".length()));
                cell.setCellStyle(bodyStyle);
                row.setHeightInPoints(30);
            }
            rowIdx++;
        }
    }

    private void createHeader(Workbook workbook, Sheet sheet) {
        CellStyle planHeaderStyle = workbook.createCellStyle();
        Font planFont = workbook.createFont();
        planFont.setBold(true);
        planFont.setColor(IndexedColors.WHITE.getIndex());
        planHeaderStyle.setFont(planFont);
        planHeaderStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        planHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        planHeaderStyle.setBorderBottom(BorderStyle.THIN);
        planHeaderStyle.setWrapText(true);

        CellStyle activityHeaderStyle = workbook.createCellStyle();
        activityHeaderStyle.cloneStyleFrom(planHeaderStyle);
        activityHeaderStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());

        Row header = sheet.createRow(0);
        header.setHeightInPoints(32);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(i < 8 ? planHeaderStyle : activityHeaderStyle);
        }
    }

    private void writeSampleRow(Sheet sheet, OrgHierarchyResponse hierarchy) {
        String sampleVertical = "Sample Vertical";
        String sampleFunction = "Sample Function";
        String sampleDomain = "Sample Domain";
        String sampleSubDomain = "Sample Sub Domain";

        if (!hierarchy.getSubDomains().isEmpty()) {
            SubDomainDto sd = hierarchy.getSubDomains().get(0);
            sampleSubDomain = sd.getName();

            DomainDto matchedDomain = null;
            for (DomainDto d : hierarchy.getDomains()) {
                if (d.getId().equals(sd.getDomainId())) {
                    matchedDomain = d;
                    break;
                }
            }
            if (matchedDomain != null) {
                sampleDomain = matchedDomain.getName();

                TeamFunctionDto matchedFunction = null;
                for (TeamFunctionDto f : hierarchy.getTeamFunction()) {
                    if (f.getId().equals(matchedDomain.getFunctionId())) {
                        matchedFunction = f;
                        break;
                    }
                }
                if (matchedFunction != null) {
                    sampleFunction = matchedFunction.getName();

                    for (VerticalDto v : hierarchy.getVerticals()) {
                        if (v.getId().equals(matchedFunction.getVerticalId())) {
                            sampleVertical = v.getName();
                            break;
                        }
                    }
                }
            }
        }

        String[] sample = {
                sampleVertical, sampleFunction, sampleDomain, sampleSubDomain,
                "Access", "Upgrade", "Cisco", "NSA",
                "Sample Router Upgrade",
                "G", "L2", "60", sampleSubDomain,
                "G", "L2", "45", sampleSubDomain,
                "LG", "L3", "30", sampleSubDomain,
                "A", "L2", "90", sampleSubDomain,
                "A", "L2", "45", sampleSubDomain,
                "N", "L3", "120", "5", "2", "60", sampleSubDomain
        };
        Row row = sheet.createRow(1);
        for (int i = 0; i < sample.length; i++) {
            row.createCell(i).setCellValue(sample[i]);
        }
    }

    private void createMasterData(Workbook workbook, Sheet master, OrgHierarchyResponse hierarchy) {
        CellStyle listHeaderStyle = buildListHeaderStyle(workbook);
        CellStyle hintStyle = buildHintStyle(workbook);

        record ListDef(String rangeName, List<String> values) {
        }

        List<ListDef> flatLists = List.of(
                new ListDef("VERTICAL_LIST", verticalNames(hierarchy)),
                new ListDef("LAYER_OPTIONS", LAYER_OPTIONS),
                new ListDef("PLAN_TYPE_OPTIONS", PLAN_TYPE_OPTIONS),
                new ListDef("CHANGE_IMPACT_OPTIONS", CHANGE_IMPACT_OPTIONS),
                new ListDef("SHIFT_OPTIONS", SHIFT_OPTIONS),
                new ListDef("LEVEL_OPTIONS", LEVEL_OPTIONS),
                new ListDef("TEAM_OPTIONS", subDomainNames(hierarchy))
        );

        int col = 0;
        for (ListDef def : flatLists) {
            writeFlatListColumn(workbook, master, def.rangeName(), def.values(), col++, listHeaderStyle, hintStyle);
        }

        // Vertical -> Team Function cascade (range names prefixed FN_ to avoid
        // colliding with the Domain/Sub Domain cascades below, in case a name
        // is reused across hierarchy levels).
        Map<Long, String> verticalNameById = new HashMap<>();
        for (VerticalDto v : hierarchy.getVerticals()) verticalNameById.put(v.getId(), v.getName());

        Map<String, Set<String>> verticalFunctionMap = new TreeMap<>();
        for (TeamFunctionDto f : hierarchy.getTeamFunction()) {
            String verticalName = verticalNameById.get(f.getVerticalId());
            if (verticalName == null) continue;
            verticalFunctionMap.computeIfAbsent(verticalName, k -> new TreeSet<>()).add(f.getName());
        }
        for (Map.Entry<String, Set<String>> entry : verticalFunctionMap.entrySet()) {
            writeFlatListColumn(workbook, master, "FN_" + sanitize(entry.getKey()),
                    new ArrayList<>(entry.getValue()), col++, listHeaderStyle, hintStyle);
        }

        // Team Function -> CHM Domain cascade
        Map<Long, String> functionNameById = new HashMap<>();
        for (TeamFunctionDto f : hierarchy.getTeamFunction()) functionNameById.put(f.getId(), f.getName());

        Map<String, Set<String>> functionDomainMap = new TreeMap<>();
        for (DomainDto d : hierarchy.getDomains()) {
            String functionName = functionNameById.get(d.getFunctionId());
            if (functionName == null) continue;
            functionDomainMap.computeIfAbsent(functionName, k -> new TreeSet<>()).add(d.getName());
        }
        for (Map.Entry<String, Set<String>> entry : functionDomainMap.entrySet()) {
            writeFlatListColumn(workbook, master, "DM_" + sanitize(entry.getKey()),
                    new ArrayList<>(entry.getValue()), col++, listHeaderStyle, hintStyle);
        }

        // CHM Domain -> CHM Sub Domain cascade
        Map<Long, String> domainNameById = new HashMap<>();
        for (DomainDto d : hierarchy.getDomains()) domainNameById.put(d.getId(), d.getName());

        Map<String, Set<String>> domainSubDomainMap = new TreeMap<>();
        for (SubDomainDto sd : hierarchy.getSubDomains()) {
            String domainName = domainNameById.get(sd.getDomainId());
            if (domainName == null) continue;
            domainSubDomainMap.computeIfAbsent(domainName, k -> new TreeSet<>()).add(sd.getName());
        }
        for (Map.Entry<String, Set<String>> entry : domainSubDomainMap.entrySet()) {
            writeFlatListColumn(workbook, master, "SD_" + sanitize(entry.getKey()),
                    new ArrayList<>(entry.getValue()), col++, listHeaderStyle, hintStyle);
        }
    }

    private List<String> verticalNames(OrgHierarchyResponse hierarchy) {
        return hierarchy.getVerticals().stream().map(VerticalDto::getName).distinct().sorted().toList();
    }

    private List<String> subDomainNames(OrgHierarchyResponse hierarchy) {
        return hierarchy.getSubDomains().stream().map(SubDomainDto::getName).distinct().sorted().toList();
    }

    private void writeFlatListColumn(Workbook workbook, Sheet master, String rangeName, List<String> values,
                                      int col, CellStyle headerStyle, CellStyle hintStyle) {
        if (values == null || values.isEmpty()) return;
        if (workbook.getName(rangeName) != null) return;

        Row headerRow = getOrCreateRow(master, 0);
        Cell headerCell = headerRow.createCell(col);
        headerCell.setCellValue(rangeName.replace("_", " "));
        headerCell.setCellStyle(headerStyle);

        int row = 1;
        for (String value : values) {
            getOrCreateRow(master, row++).createCell(col).setCellValue(value);
        }

        // Named range spans exactly the real values — no padding. The sheet is
        // rebuilt from the live DB on every download, so there's nothing to
        // "grow into" later, and padding it with blank rows previously buried
        // the real options under dozens of blank entries, making Excel's
        // dropdown open scrolled past everything real.
        String colLetter = CellReference.convertNumToColString(col);
        String formula = MASTER_SHEET_NAME + "!$" + colLetter + "$2" + ":$" + colLetter + "$" + row;

        Name namedRange = workbook.createName();
        namedRange.setNameName(rangeName);
        namedRange.setRefersToFormula(formula);

        Cell hint = getOrCreateRow(master, row).createCell(col);
        hint.setCellValue("(reference list — do not edit)");
        hint.setCellStyle(hintStyle);

        master.autoSizeColumn(col);
    }

    private void applyDropdowns(Workbook workbook, Sheet sheet) {
        DataValidationHelper helper = sheet.getDataValidationHelper();

        addDropdown(helper, sheet, "VERTICAL_LIST", 0);
        addFormulaDropdown(helper, sheet, "INDIRECT(\"FN_\"&SUBSTITUTE($A2,\" \",\"_\"))", 1);
        addFormulaDropdown(helper, sheet, "INDIRECT(\"DM_\"&SUBSTITUTE($B2,\" \",\"_\"))", 2);
        addFormulaDropdown(helper, sheet, "INDIRECT(\"SD_\"&SUBSTITUTE($C2,\" \",\"_\"))", 3);
        addDropdown(helper, sheet, "LAYER_OPTIONS", 4);
        addDropdown(helper, sheet, "PLAN_TYPE_OPTIONS", 5);
        addDropdown(helper, sheet, "CHANGE_IMPACT_OPTIONS", 7);

        int[] shiftCols = {9, 13, 17, 21, 25, 29};
        int[] levelCols = {10, 14, 18, 22, 26, 30};
        int[] teamCols = {12, 16, 20, 24, 28, 35};
        for (int c : shiftCols) addDropdown(helper, sheet, "SHIFT_OPTIONS", c);
        for (int c : levelCols) addDropdown(helper, sheet, "LEVEL_OPTIONS", c);
        for (int c : teamCols) addDropdown(helper, sheet, "TEAM_OPTIONS", c);
    }

    private void addDropdown(DataValidationHelper helper, Sheet sheet, String rangeName, int column) {
        DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeName);
        DataValidation validation = helper.createValidation(
                constraint, new CellRangeAddressList(DATA_ROW_START, DATA_ROW_END, column, column));
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
        validation.createErrorBox("Value Not In List",
                "This value is not in the predefined list. You may still proceed, but double check spelling.");
        validation.setShowPromptBox(true);
        validation.createPromptBox("Tip", "Select a value from the dropdown list.");
        sheet.addValidationData(validation);
    }

    private void addFormulaDropdown(DataValidationHelper helper, Sheet sheet, String formula, int column) {
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        DataValidation validation = helper.createValidation(
                constraint, new CellRangeAddressList(DATA_ROW_START, DATA_ROW_END, column, column));
        validation.setShowErrorBox(false);
        sheet.addValidationData(validation);
    }

    private CellStyle buildListHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private CellStyle buildHintStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setItalic(true);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        return s;
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private String sanitize(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank()) cleaned = "DEFAULT";
        if (Character.isDigit(cleaned.charAt(0))) cleaned = "_" + cleaned;
        return cleaned;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────────

    public List<PlanActivityExcelRowDto> parseExcel(MultipartFile file) throws Exception {
        List<PlanActivityExcelRowDto> list = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(UPLOAD_SHEET_NAME);
            if (sheet == null) {
                throw new RuntimeException(
                        "Sheet '" + UPLOAD_SHEET_NAME + "' not found. Please use the provided template.");
            }

            for (int i = DATA_ROW_START; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                PlanActivityExcelRowDto dto = new PlanActivityExcelRowDto();
                dto.setRowNumber(i + 1);

                dto.setVerticalName(text(fmt, row, 0));
                dto.setFunctionName(text(fmt, row, 1));
                dto.setChmDomainName(text(fmt, row, 2));
                dto.setChmSubDomainName(text(fmt, row, 3));
                dto.setLayer(text(fmt, row, 4));
                dto.setPlanType(text(fmt, row, 5));
                dto.setVendorOem(text(fmt, row, 6));
                dto.setChangeImpact(text(fmt, row, 7));
                dto.setActivityName(text(fmt, row, 8));

                dto.setCrqReviewShift(text(fmt, row, 9));
                dto.setCrqReviewMinimumLevelRequirement(text(fmt, row, 10));
                dto.setCrqReviewRequiredTimeMinutes(intVal(fmt, row, 11));
                dto.setCrqReviewTeamName(text(fmt, row, 12));

                dto.setImpactAnalysisShift(text(fmt, row, 13));
                dto.setImpactAnalysisMinimumLevelRequirement(text(fmt, row, 14));
                dto.setImpactAnalysisRequiredTimeMinutes(intVal(fmt, row, 15));
                dto.setImpactAnalysisTeamName(text(fmt, row, 16));

                dto.setSchedulingShift(text(fmt, row, 17));
                dto.setSchedulingMinimumLevelRequirement(text(fmt, row, 18));
                dto.setSchedulingRequiredTimeMinutes(intVal(fmt, row, 19));
                dto.setSchedulingTeamName(text(fmt, row, 20));

                dto.setMopCreateShift(text(fmt, row, 21));
                dto.setMopCreateMinimumLevelRequirement(text(fmt, row, 22));
                dto.setMopCreateRequiredTimeMinutes(intVal(fmt, row, 23));
                dto.setMopCreateTeamName(text(fmt, row, 24));

                dto.setMopValidateShift(text(fmt, row, 25));
                dto.setMopValidateMinimumLevelRequirement(text(fmt, row, 26));
                dto.setMopValidateRequiredTimeMinutes(intVal(fmt, row, 27));
                dto.setMopValidateTeamName(text(fmt, row, 28));

                dto.setCrqExecutionShift(text(fmt, row, 29));
                dto.setCrqExecutionMinimumLevelRequirement(text(fmt, row, 30));
                dto.setCrqExecutionRequiredTimeMinutes(intVal(fmt, row, 31));
                dto.setCrqExecutionDaysMargin(intVal(fmt, row, 32));
                dto.setCrqExecutionReservationMargin(intVal(fmt, row, 33));
                dto.setCrqExecutionRollbackTime(intVal(fmt, row, 34));
                dto.setCrqExecutionTeamName(text(fmt, row, 35));

                list.add(dto);
            }
        }
        return list;
    }

    private String text(DataFormatter fmt, Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        String v = fmt.formatCellValue(cell).trim();
        return v.isEmpty() ? null : v;
    }

    private Integer intVal(DataFormatter fmt, Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        String raw = fmt.formatCellValue(cell).trim();
        if (raw.isEmpty()) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────────

    public PlanActivityExcelParseResponseDto parseAndValidate(Long actorUserId, MultipartFile file) throws Exception {
        List<PlanActivityExcelRowDto> rows = parseExcel(file);
        List<PlanActivityValidationErrorDto> errors = validateRows(actorUserId, rows);

        Set<Integer> invalidRows = new HashSet<>();
        for (PlanActivityValidationErrorDto e : errors) invalidRows.add(e.getRowNumber());

        return new PlanActivityExcelParseResponseDto(
                rows.size(), rows.size() - invalidRows.size(), invalidRows.size(), rows, errors);
    }

    public List<PlanActivityValidationErrorDto> validateRows(Long actorUserId, List<PlanActivityExcelRowDto> rows) {
        List<PlanActivityValidationErrorDto> errors = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return errors;

        OrgHierarchyResponse hierarchy = userService.getOrgHierarchyByUserV1(actorUserId);

        Map<String, VerticalDto> verticalByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (VerticalDto v : hierarchy.getVerticals()) verticalByName.put(v.getName(), v);

        Map<Long, Map<String, TeamFunctionDto>> functionsByVertical = new HashMap<>();
        for (TeamFunctionDto f : hierarchy.getTeamFunction()) {
            functionsByVertical
                    .computeIfAbsent(f.getVerticalId(), k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                    .put(f.getName(), f);
        }

        Map<Long, Map<String, DomainDto>> domainsByFunction = new HashMap<>();
        for (DomainDto d : hierarchy.getDomains()) {
            domainsByFunction
                    .computeIfAbsent(d.getFunctionId(), k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                    .put(d.getName(), d);
        }

        Map<Long, Map<String, SubDomainDto>> subDomainsByDomain = new HashMap<>();
        for (SubDomainDto sd : hierarchy.getSubDomains()) {
            subDomainsByDomain
                    .computeIfAbsent(sd.getDomainId(), k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                    .put(sd.getName(), sd);
        }

        // Teams are resolved by sp_insert_plan_activity as a flat, org-wide
        // lookup against ORG_SUB_DOMAIN (not scoped to the row's own Domain),
        // so team-name existence is validated the same way here.
        Map<String, SubDomainDto> subDomainByNameFlat = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SubDomainDto sd : hierarchy.getSubDomains()) subDomainByNameFlat.put(sd.getName(), sd);

        Map<String, Integer> comboActivityFirstRow = new HashMap<>();
        Map<String, Integer> comboPlanIdCache = new HashMap<>();
        Map<Integer, List<ActivityPhaseViewDTO.ActivityEntry>> planActivitiesCache = new HashMap<>();

        for (PlanActivityExcelRowDto row : rows) {
            int rn = row.getRowNumber();

            VerticalDto vertical = null;
            if (isBlank(row.getVerticalName())) {
                errors.add(err(rn, "Vertical", row.getVerticalName(), "Required"));
            } else {
                vertical = verticalByName.get(row.getVerticalName().trim());
                if (vertical == null) {
                    errors.add(err(rn, "Vertical", row.getVerticalName(), "Invalid Vertical — not found"));
                }
            }

            TeamFunctionDto function = null;
            if (isBlank(row.getFunctionName())) {
                errors.add(err(rn, "Team Function", row.getFunctionName(), "Required"));
            } else if (vertical != null) {
                function = functionsByVertical.getOrDefault(vertical.getId(), Map.of())
                        .get(row.getFunctionName().trim());
                if (function == null) {
                    errors.add(err(rn, "Team Function", row.getFunctionName(),
                            "Invalid Hierarchy Mapping — not found under Vertical '" + row.getVerticalName().trim() + "'"));
                }
            }

            DomainDto domain = null;
            if (isBlank(row.getChmDomainName())) {
                errors.add(err(rn, "CHM Domain", row.getChmDomainName(), "Required"));
            } else if (function != null) {
                domain = domainsByFunction.getOrDefault(function.getId(), Map.of())
                        .get(row.getChmDomainName().trim());
                if (domain == null) {
                    errors.add(err(rn, "CHM Domain", row.getChmDomainName(),
                            "Invalid Hierarchy Mapping — not found under Team Function '" + row.getFunctionName().trim() + "'"));
                }
            }

            SubDomainDto subDomain = null;
            if (isBlank(row.getChmSubDomainName())) {
                errors.add(err(rn, "CHM Sub Domain", row.getChmSubDomainName(), "Required"));
            } else if (domain != null) {
                subDomain = subDomainsByDomain.getOrDefault(domain.getId(), Map.of())
                        .get(row.getChmSubDomainName().trim());
                if (subDomain == null) {
                    errors.add(err(rn, "CHM Sub Domain", row.getChmSubDomainName(),
                            "Invalid Hierarchy Mapping — not found under CHM Domain '" + row.getChmDomainName().trim() + "'"));
                }
            }

            requireText(errors, rn, "Vendor / OEM", row.getVendorOem());
            requireOneOf(errors, rn, "Layer", row.getLayer(), LAYER_OPTIONS);
            requireOneOf(errors, rn, "Plan Type", row.getPlanType(), PLAN_TYPE_OPTIONS);
            requireOneOf(errors, rn, "Change Impact", row.getChangeImpact(), CHANGE_IMPACT_OPTIONS);

            if (isBlank(row.getActivityName())) {
                errors.add(err(rn, "Activity Name", row.getActivityName(), "Required"));
            } else if (row.getActivityName().trim().length() > 30) {
                errors.add(err(rn, "Activity Name", row.getActivityName(), "Must be 30 characters or fewer"));
            }

            validatePhase(errors, rn, "CRQ Review",
                    row.getCrqReviewShift(), row.getCrqReviewMinimumLevelRequirement(), row.getCrqReviewRequiredTimeMinutes(),
                    null, null, null, false, row.getCrqReviewTeamName(), subDomainByNameFlat);

            validatePhase(errors, rn, "Impact Analysis",
                    row.getImpactAnalysisShift(), row.getImpactAnalysisMinimumLevelRequirement(), row.getImpactAnalysisRequiredTimeMinutes(),
                    null, null, null, false, row.getImpactAnalysisTeamName(), subDomainByNameFlat);

            validatePhase(errors, rn, "Scheduling",
                    row.getSchedulingShift(), row.getSchedulingMinimumLevelRequirement(), row.getSchedulingRequiredTimeMinutes(),
                    null, null, null, false, row.getSchedulingTeamName(), subDomainByNameFlat);

            validatePhase(errors, rn, "MOP Create",
                    row.getMopCreateShift(), row.getMopCreateMinimumLevelRequirement(), row.getMopCreateRequiredTimeMinutes(),
                    null, null, null, false, row.getMopCreateTeamName(), subDomainByNameFlat);

            validatePhase(errors, rn, "MOP Validate",
                    row.getMopValidateShift(), row.getMopValidateMinimumLevelRequirement(), row.getMopValidateRequiredTimeMinutes(),
                    null, null, null, false, row.getMopValidateTeamName(), subDomainByNameFlat);

            validatePhase(errors, rn, "CRQ Execution",
                    row.getCrqExecutionShift(), row.getCrqExecutionMinimumLevelRequirement(), row.getCrqExecutionRequiredTimeMinutes(),
                    row.getCrqExecutionDaysMargin(), row.getCrqExecutionReservationMargin(), row.getCrqExecutionRollbackTime(),
                    true, row.getCrqExecutionTeamName(), subDomainByNameFlat);

            if (domain != null && subDomain != null && !isBlank(row.getActivityName())) {
                checkDuplicateActivity(errors, actorUserId, rn, row, domain, subDomain,
                        comboActivityFirstRow, comboPlanIdCache, planActivitiesCache);
            }
        }

        return errors;
    }

    private void checkDuplicateActivity(List<PlanActivityValidationErrorDto> errors, Long actorUserId, int rn,
                                         PlanActivityExcelRowDto row, DomainDto domain, SubDomainDto subDomain,
                                         Map<String, Integer> comboActivityFirstRow,
                                         Map<String, Integer> comboPlanIdCache,
                                         Map<Integer, List<ActivityPhaseViewDTO.ActivityEntry>> planActivitiesCache) {

        String comboKey = String.join("|",
                String.valueOf(domain.getId()), String.valueOf(subDomain.getId()),
                safe(row.getLayer()), safe(row.getPlanType()),
                safe(row.getVendorOem()), safe(row.getChangeImpact())).toLowerCase();

        String activityKey = comboKey + "::" + row.getActivityName().trim().toLowerCase();
        Integer priorRow = comboActivityFirstRow.get(activityKey);

        if (priorRow != null) {
            errors.add(err(rn, "Activity Name", row.getActivityName(),
                    "Duplicate Activity — same name already used for this Plan on row " + priorRow));
        } else {
            comboActivityFirstRow.put(activityKey, rn);
        }

        // Resolve whether a Plan matching this exact combo already exists (via the
        // working sp_get_plan_details — mirrors sp_insert_plan_activity's own
        // "Plan Check" match on chm_domain/chm_sub_domain/layer/plan_type/
        // vendor_oem/change_impact), then look up its activities (via the working
        // sp_get_activity_phase_view). Deliberately NOT using GET /activity/view here —
        // its sp_get_activity_details still joins a table named PLAN_MASTER that no
        // longer exists in this schema (pre-existing bug, out of scope to fix here).
        Integer planId = comboPlanIdCache.computeIfAbsent(comboKey, k -> {
            try {
                PageResponseDto<PlanDetailsDto> page = planSetupService.getPlanDetails(
                        actorUserId, 0L, 0L, domain.getId(), subDomain.getId(), "Active",
                        PageRequest.of(0, 2000));
                return page.getContent().stream()
                        .filter(p -> namesEqual(p.getLayer(), row.getLayer())
                                && namesEqual(p.getPlanType(), row.getPlanType())
                                && namesEqual(p.getPlanVendor(), row.getVendorOem())
                                && namesEqual(p.getChangeImpact(), row.getChangeImpact()))
                        .map(PlanDetailsDto::getPlanId)
                        .findFirst()
                        .orElse(-1);
            } catch (Exception ex) {
                return -1;
            }
        });

        if (planId == null || planId < 0) {
            return; // no existing Plan for this combo yet — nothing to collide with in the DB
        }

        List<ActivityPhaseViewDTO.ActivityEntry> existing = planActivitiesCache.computeIfAbsent(planId, id -> {
            try {
                return activityService.getActivityPhaseView(actorUserId, id.longValue()).getActivities();
            } catch (Exception ex) {
                return List.of();
            }
        });

        boolean existsInDb = existing.stream().anyMatch(a ->
                a.getActivityName() != null && a.getActivityName().trim().equalsIgnoreCase(row.getActivityName().trim()));

        if (existsInDb) {
            errors.add(err(rn, "Activity Name", row.getActivityName(),
                    "Duplicate Activity — an activity with this name already exists for this Plan"));
        }
    }

    private void validatePhase(List<PlanActivityValidationErrorDto> errors, int rn, String label,
                                String shift, String minLevel, Integer time,
                                Integer daysMargin, Integer reservationMargin, Integer rollbackTime,
                                boolean hasMargins, String teamName,
                                Map<String, SubDomainDto> subDomainByNameFlat) {

        if (isBlank(shift)) {
            errors.add(err(rn, label + " Shift", shift, "Required"));
        } else if (!SHIFT_OPTIONS.contains(shift.trim().toUpperCase())) {
            errors.add(err(rn, label + " Shift", shift, "Invalid Shift — must be one of " + SHIFT_OPTIONS));
        }

        if (isBlank(minLevel)) {
            errors.add(err(rn, label + " Min Level", minLevel, "Required"));
        } else if (!LEVEL_OPTIONS.contains(minLevel.trim().toUpperCase())) {
            errors.add(err(rn, label + " Min Level", minLevel, "Invalid Level — must be one of " + LEVEL_OPTIONS));
        }

        if (time == null) {
            errors.add(err(rn, label + " Time (Min)", "", "Required or must be a valid non-negative number"));
        } else if (time < 0) {
            errors.add(err(rn, label + " Time (Min)", String.valueOf(time), "Must be a non-negative number"));
        }

        if (hasMargins) {
            if (daysMargin == null || daysMargin < 0) {
                errors.add(err(rn, label + " Days Margin", str(daysMargin),
                        daysMargin == null ? "Required or must be a valid non-negative number" : "Must be a non-negative number"));
            }
            if (reservationMargin == null || reservationMargin < 0) {
                errors.add(err(rn, label + " Reservation Margin", str(reservationMargin),
                        reservationMargin == null ? "Required or must be a valid non-negative number" : "Must be a non-negative number"));
            }
            if (rollbackTime == null || rollbackTime < 0) {
                errors.add(err(rn, label + " Rollback Time", str(rollbackTime),
                        rollbackTime == null ? "Required or must be a valid non-negative number" : "Must be a non-negative number"));
            }
        }

        if (isBlank(teamName)) {
            errors.add(err(rn, label + " Team", teamName, "Required"));
        } else if (!subDomainByNameFlat.containsKey(teamName.trim())) {
            errors.add(err(rn, label + " Team", teamName, "Invalid Team — not found"));
        }
    }

    private void requireText(List<PlanActivityValidationErrorDto> errors, int rn, String column, String value) {
        if (isBlank(value)) errors.add(err(rn, column, value, "Required"));
    }

    private void requireOneOf(List<PlanActivityValidationErrorDto> errors, int rn, String column, String value, List<String> allowed) {
        if (isBlank(value)) {
            errors.add(err(rn, column, value, "Required"));
        } else if (allowed.stream().noneMatch(a -> a.equalsIgnoreCase(value.trim()))) {
            errors.add(err(rn, column, value, "Invalid value — must be one of " + allowed));
        }
    }

    private PlanActivityValidationErrorDto err(int rn, String column, String value, String error) {
        return new PlanActivityValidationErrorDto(rn, column, value, error);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String str(Integer i) {
        return i == null ? "" : String.valueOf(i);
    }

    private boolean namesEqual(String a, String b) {
        return safe(a).equalsIgnoreCase(safe(b));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Batch insert
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PlanActivityExcelUploadSummaryDto insertBatch(Long actorUserId, List<PlanActivityExcelRowDto> rows) {
        long start = System.currentTimeMillis();
        List<PlanActivityExcelRowResultDto> results = new ArrayList<>();

        if (rows == null) rows = List.of();

        List<PlanActivityValidationErrorDto> errors = validateRows(actorUserId, rows);
        Map<Integer, String> firstErrorByRow = new LinkedHashMap<>();
        for (PlanActivityValidationErrorDto e : errors) {
            firstErrorByRow.putIfAbsent(e.getRowNumber(), e.getColumn() + ": " + e.getError());
        }

        String sql = "CALL sp_insert_plan_activity(" + "?,".repeat(36) + "?)";

        int success = 0;
        int failed = 0;

        for (PlanActivityExcelRowDto row : rows) {
            if (firstErrorByRow.containsKey(row.getRowNumber())) {
                failed++;
                results.add(new PlanActivityExcelRowResultDto(
                        row.getRowNumber(), row.getActivityName(), "FAILED",
                        firstErrorByRow.get(row.getRowNumber()), null, null));
                continue;
            }

            try {
                Object[] params = PlanActivityExcelMapper.toSqlParams(actorUserId, row);
                LOGGER.info("Row {} -> {}", row.getRowNumber(),
                        CommonService.formatProcedureCall("sp_insert_plan_activity", params));

                List<PlanActivityInsertResultDto> resultRows = databaseUtils.executeProcedureGetDataWithError(
                        jdbcTemplateTwo, sql, PlanActivityInsertResultDto.class, params);

                PlanActivityInsertResultDto r = resultRows.isEmpty() ? null : resultRows.get(0);
                success++;
                results.add(new PlanActivityExcelRowResultDto(
                        row.getRowNumber(), row.getActivityName(), "SUCCESS",
                        r != null ? r.getMessage() : "Inserted",
                        r != null ? r.getPlanId() : null,
                        r != null ? r.getActivityId() : null));

            } catch (Exception ex) {
                LOGGER.error("Row {} failed", row.getRowNumber(), ex);
                failed++;
                results.add(new PlanActivityExcelRowResultDto(
                        row.getRowNumber(), row.getActivityName(), "FAILED", ex.getMessage(), null, null));
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return new PlanActivityExcelUploadSummaryDto(rows.size(), success, failed, elapsed, results);
    }
}
