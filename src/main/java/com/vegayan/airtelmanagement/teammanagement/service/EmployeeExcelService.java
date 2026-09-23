package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.common.dto.DbResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.teammanagement.dto.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Service
public class EmployeeExcelService extends BaseService {

    private final TeamOverviewService teamOverviewService;

    private static final int    DATA_ROW_START            = 1;
    private static final int    DATA_ROW_END              = 5000;
    private static final int    EXTRA_ROWS_FOR_USER_INPUT = 50;
    private static final int    HIERARCHY_START_COL       = 20;
    private static final String UPLOAD_SHEET_NAME         = "Employee_Upload";
    private static final String MASTER_SHEET_NAME         = "MASTER_DATA";

    /** Shared with {@link ExcelMasterDataCache} so validation checks the same list used for the template dropdown. */
    public static final List<String> GENDER_OPTIONS = List.of("Male", "Female", "Other");

    // ─── Column definition record ─────────────────────────────────────────────
    private record ColumnDef(
            String header,
            ColType colType,
            String rangeName,
            boolean strictDrop,
            Function<Cell, String> getter,
            BiConsumer<EmployeeExcelRowDto, String> setter
    ) {}

    private enum ColType { TEXT, DATE, DROPDOWN, FORMULA_DROPDOWN }

    // ─── MASTER_DATA layout constants ────────────────────────────────────────
    // Flat lists occupy columns 0‒7; hierarchy starts at col 20 (HIERARCHY_START_COL).
    // After the flat lists we leave cols 8‒19 empty as a visual separator.
    //
    // Hierarchy column layout (starting at col 20):
    //   col 20 : Vertical_List  (flat list of all verticals)
    //   col 21+: one column per Vertical  → its Functions
    //   then  : one column per Function   → its Domains
    //   then  : one column per Domain     → its SubDomains
    //
    // These are also used for the MASTER_DATA "edit here" area that users see.

    public EmployeeExcelService(TeamOverviewService teamOverviewService) {
        this.teamOverviewService = teamOverviewService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Column definitions (order = Excel column index 0, 1, 2 …)
    // ─────────────────────────────────────────────────────────────────────────

    private List<ColumnDef> buildColumnDefs() {
        DataFormatter fmt = new DataFormatter();
        Function<Cell, String> text = c -> c == null ? "" : fmt.formatCellValue(c);

        return List.of(
                // 0
                new ColumnDef("OLMID",                        ColType.TEXT,            null,                                             false, text, EmployeeExcelRowDto::setOlmid),
                // 1
                new ColumnDef("Employee Name",                ColType.TEXT,            null,                                             false, text, EmployeeExcelRowDto::setEmployeeName),
                // 2
                new ColumnDef("Email",                        ColType.TEXT,            null,                                             false, text, EmployeeExcelRowDto::setEmailId),
                // 3
                new ColumnDef("Mobile",                       ColType.TEXT,            null,                                             false, text, EmployeeExcelRowDto::setMobileNo),
                // 4
                new ColumnDef("Employment Type",              ColType.DROPDOWN,        "EMPLOYMENT_TYPES",                               false, text, EmployeeExcelRowDto::setEmploymentType),
                // 5
                new ColumnDef("Vendor Company",               ColType.DROPDOWN,        "VENDOR_COMPANIES",                               false, text, EmployeeExcelRowDto::setVendorCompany),
                // 6
                new ColumnDef("Designation",                  ColType.DROPDOWN,        "DESIGNATIONS",                                   false, text, EmployeeExcelRowDto::setDesignation),
                // 7
                new ColumnDef("Job Level",                    ColType.DROPDOWN,        "JOB_LEVELS",                                     false, text, EmployeeExcelRowDto::setJobLevel),
                // 8
                new ColumnDef("Office Location",              ColType.DROPDOWN,        "OFFICE_LOCATIONS",                               false, text, EmployeeExcelRowDto::setOfficeLocation),
                // 9
                new ColumnDef("Gender",                       ColType.DROPDOWN,        "GENDER_LIST",                                    true,  text, EmployeeExcelRowDto::setGender),
                // 10
                new ColumnDef("Device Vendor Capability",     ColType.DROPDOWN,        "DEVICE_CAPABILITIES",                            false, text, EmployeeExcelRowDto::setDeviceVendorCapability),
                // 11  ← DATE column
                new ColumnDef("Date Of Joining (dd-MM-yyyy)", ColType.DATE,            null,                                             false, null, null),
                // 12  ← cascading hierarchy dropdowns
                new ColumnDef("Vertical",                     ColType.DROPDOWN,        "Vertical_List",                                  false, text, EmployeeExcelRowDto::setVerticalName),
                // 13
                new ColumnDef("Function",                     ColType.FORMULA_DROPDOWN,"INDIRECT(SUBSTITUTE($M2,\" \",\"_\"))",           false, text, EmployeeExcelRowDto::setFunctionName),
                // 14
                new ColumnDef("Domain",                       ColType.FORMULA_DROPDOWN,"INDIRECT(SUBSTITUTE($N2,\" \",\"_\"))",           false, text, EmployeeExcelRowDto::setDomainName),
                // 15
                new ColumnDef("SubDomain",                    ColType.FORMULA_DROPDOWN,"INDIRECT(SUBSTITUTE($O2,\" \",\"_\"))",           false, text, EmployeeExcelRowDto::setSubDomainName),
                // 16
                new ColumnDef("Role Code",                    ColType.DROPDOWN,        "ROLE_CODES",                                     false, text, EmployeeExcelRowDto::setRoleCode)
        );
    }

    private static final int DATE_COL_INDEX = 11; // must match position in buildColumnDefs()

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry points
    // ─────────────────────────────────────────────────────────────────────────

    public Workbook generateEmployeeTemplate(Long userId) {
        List<ExcelUserHierarchyDto>   hierarchy = teamOverviewService.fetchHierarchy();
        CreateUserDropdownResponseDto dropdowns = teamOverviewService.getCreateUserDropdowns();
        return buildTemplate(hierarchy, dropdowns);
    }

    public Workbook buildTemplate(
            List<ExcelUserHierarchyDto>   hierarchy,
            CreateUserDropdownResponseDto dropdowns) {

        Workbook workbook    = new XSSFWorkbook();
        Sheet    uploadSheet = workbook.createSheet(UPLOAD_SHEET_NAME);
        Sheet    masterSheet = workbook.createSheet(MASTER_SHEET_NAME);

        List<ColumnDef> cols = buildColumnDefs();

        createHeader(workbook, uploadSheet, cols);
        createMasterData(workbook, masterSheet, hierarchy, dropdowns);
        applyDropdowns(workbook, uploadSheet, cols);

        return workbook;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Header row
    // ─────────────────────────────────────────────────────────────────────────

    private void createHeader(Workbook workbook, Sheet sheet, List<ColumnDef> cols) {

        CellStyle headerStyle = workbook.createCellStyle();
        Font      headerFont  = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        short dateFormatIdx = workbook.getCreationHelper()
                                      .createDataFormat()
                                      .getFormat("dd-MM-yyyy");

        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(dateFormatIdx);
        dateStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        dateStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        dateStyle.setAlignment(HorizontalAlignment.CENTER);

        Row header = sheet.createRow(0);
        for (int i = 0; i < cols.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols.get(i).header());
            cell.setCellStyle(headerStyle);
        }

        // Pre-stamp date style on every data row in the date column (blank, styled only)
        for (int r = DATA_ROW_START; r <= DATA_ROW_END; r++) {
            Row row = sheet.getRow(r);
            if (row == null) row = sheet.createRow(r);
            Cell dateCell = row.createCell(DATE_COL_INDEX);
            dateCell.setCellStyle(dateStyle);
        }

        for (int i = 0; i < cols.size(); i++) {
            if (i == DATE_COL_INDEX) sheet.setColumnWidth(DATE_COL_INDEX, 5000);
            else                     sheet.autoSizeColumn(i);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MASTER_DATA sheet
    // ─────────────────────────────────────────────────────────────────────────
    //
    // Layout:
    //   Cols 0‒7  : flat dropdown lists (Employment Types, Vendor Companies, etc.)
    //   Cols 8‒19 : empty separator
    //   Col  20   : Vertical_List (all unique verticals)
    //   Col  21+  : one column per Vertical → Functions
    //   then      : one column per Function → Domains
    //   then      : one column per Domain   → SubDomains
    //
    // Each hierarchy group also gets a visible section header row (row 0) so users
    // can easily understand and extend the data.

    private void createMasterData(
            Workbook                      workbook,
            Sheet                         master,
            List<ExcelUserHierarchyDto>   hierarchy,
            CreateUserDropdownResponseDto dropdowns) {

        CellStyle listHeaderStyle = buildListHeaderStyle(workbook);
        CellStyle hintStyle       = buildHintStyle(workbook);

        // ── Section header style (group titles above hierarchy blocks) ──────
        CellStyle sectionStyle = workbook.createCellStyle();
        Font sf = workbook.createFont(); sf.setBold(true);
        sf.setColor(IndexedColors.WHITE.getIndex());
        sectionStyle.setFont(sf);
        sectionStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        record ListDef(String rangeName, List<String> values) {}

        List<ListDef> flatLists = List.of(
                new ListDef("EMPLOYMENT_TYPES",    dropdowns.getEmploymentTypes()),
                new ListDef("VENDOR_COMPANIES",    dropdowns.getVendorCompanies()),
                new ListDef("DESIGNATIONS",        dropdowns.getDesignations()),
                new ListDef("JOB_LEVELS",          dropdowns.getJobLevels()),
                new ListDef("OFFICE_LOCATIONS",    dropdowns.getOfficeLocations()),
                new ListDef("DEVICE_CAPABILITIES", dropdowns.getDeviceVendorCapabilities()),
                new ListDef("ROLE_CODES",          dropdowns.getRoleCode()),
                new ListDef("GENDER_LIST",         GENDER_OPTIONS)
        );

        for (int col = 0; col < flatLists.size(); col++) {
            ListDef def = flatLists.get(col);
            writeFlatListColumn(workbook, master, def.rangeName(), def.values(),
                    col, listHeaderStyle, hintStyle);
        }

        // ── Build hierarchy maps ─────────────────────────────────────────────
        Map<String, Set<String>> verticalFunctionMap = new TreeMap<>();
        Map<String, Set<String>> functionDomainMap   = new TreeMap<>();
        Map<String, Set<String>> domainSubDomainMap  = new TreeMap<>();

        for (ExcelUserHierarchyDto dto : hierarchy) {
            verticalFunctionMap.computeIfAbsent(dto.getVerticalName(),  k -> new TreeSet<>()).add(dto.getFunctionName());
            functionDomainMap  .computeIfAbsent(dto.getFunctionName(),  k -> new TreeSet<>()).add(dto.getDomainName());
            domainSubDomainMap .computeIfAbsent(dto.getDomainName(),    k -> new TreeSet<>()).add(dto.getSubDomainName());
        }

        List<String> verticals = new ArrayList<>(verticalFunctionMap.keySet());

        // ── Write Vertical_List flat column at col 20 ────────────────────────
        writeFlatListColumn(workbook, master, "Vertical_List", verticals,
                HIERARCHY_START_COL, listHeaderStyle, hintStyle);

        // Add a visible section label one row above the header (row -1 not possible,
        // so we use the header row itself with a merged area label written BEFORE
        // writeFlatListColumn; instead we annotate col 20 row 0 with group info
        // by adding a comment or simply a bold title — handled inside writeFlatListColumn).

        // ── Write cascading lookup columns ───────────────────────────────────
        int nextCol = HIERARCHY_START_COL + 1;
        nextCol = writeHierarchyCascade(workbook, master, verticalFunctionMap, nextCol, listHeaderStyle, hintStyle);
        nextCol = writeHierarchyCascade(workbook, master, functionDomainMap,   nextCol, listHeaderStyle, hintStyle);
        writeHierarchyCascade(workbook, master, domainSubDomainMap,  nextCol, listHeaderStyle, hintStyle);
    }

    private void writeFlatListColumn(Workbook workbook, Sheet master,
                                     String rangeName, List<String> values,
                                     int col, CellStyle headerStyle, CellStyle hintStyle) {
        if (values == null || values.isEmpty()) return;

        Row headerRow = getOrCreateRow(master, 0);
        Cell headerCell = headerRow.createCell(col);
        headerCell.setCellValue(rangeName.replace("_", " "));
        headerCell.setCellStyle(headerStyle);

        int row = 1;
        for (String value : values) {
            getOrCreateRow(master, row++).createCell(col).setCellValue(value);
        }

        Cell hint = getOrCreateRow(master, row++).createCell(col);
        hint.setCellValue("↓ Add new values below");
        hint.setCellStyle(hintStyle);

        int blankEnd = row + EXTRA_ROWS_FOR_USER_INPUT - 1;
        for (int r = row; r <= blankEnd; r++) {
            getOrCreateRow(master, r).createCell(col);
        }

        String colLetter = CellReference.convertNumToColString(col);
        String formula   = MASTER_SHEET_NAME + "!$" + colLetter + "$2"
                + ":$" + colLetter + "$" + (blankEnd + 1);

        Name namedRange = workbook.createName();
        namedRange.setNameName(rangeName);
        namedRange.setRefersToFormula(formula);

        master.autoSizeColumn(col);
    }

    private int writeHierarchyCascade(Workbook workbook, Sheet master,
                                      Map<String, Set<String>> map, int startCol,
                                      CellStyle headerStyle, CellStyle hintStyle) {
        int col = startCol;
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            String parent = sanitize(entry.getKey());
            if (workbook.getName(parent) != null) continue;

            Cell headerCell = getOrCreateRow(master, 0).createCell(col);
            headerCell.setCellValue(entry.getKey());
            headerCell.setCellStyle(headerStyle);

            int row = 1;
            for (String child : entry.getValue()) {
                getOrCreateRow(master, row++).createCell(col).setCellValue(child);
            }

            Cell hint = getOrCreateRow(master, row++).createCell(col);
            hint.setCellValue("↓ Add new values below");
            hint.setCellStyle(hintStyle);

            int blankEnd = row + EXTRA_ROWS_FOR_USER_INPUT - 1;
            for (int r = row; r <= blankEnd; r++) {
                getOrCreateRow(master, r).createCell(col);
            }

            String colLetter = CellReference.convertNumToColString(col);
            String formula   = MASTER_SHEET_NAME + "!$" + colLetter + "$2"
                    + ":$" + colLetter + "$" + (blankEnd + 1);

            Name namedRange = workbook.createName();
            namedRange.setNameName(parent);
            namedRange.setRefersToFormula(formula);

            master.autoSizeColumn(col);
            col++;
        }
        return col;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dropdowns on Employee_Upload — driven by ColumnDef list
    // ─────────────────────────────────────────────────────────────────────────

    private void applyDropdowns(Workbook workbook, Sheet sheet, List<ColumnDef> cols) {
        DataValidationHelper helper = sheet.getDataValidationHelper();

        for (int i = 0; i < cols.size(); i++) {
            ColumnDef def = cols.get(i);
            switch (def.colType()) {
                case DROPDOWN         -> addDropdown(helper, sheet, def.rangeName(), i, def.strictDrop());
                case FORMULA_DROPDOWN -> addFormulaDropdown(helper, sheet, def.rangeName(), i);
                case DATE             -> addDateValidation(helper, sheet, i);
                default               -> { /* TEXT — no validation */ }
            }
        }
    }

    private void addDropdown(DataValidationHelper helper, Sheet sheet,
                             String rangeName, int column, boolean strict) {
        DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeName);
        DataValidation validation = helper.createValidation(
                constraint, new CellRangeAddressList(DATA_ROW_START, DATA_ROW_END, column, column));
        if (strict) {
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("Invalid Value", "Please select a value from the dropdown list.");
        } else {
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.WARNING);
            validation.createErrorBox("Value Not In List",
                    "This value is not predefined. You may still proceed, "
                            + "but ensure you have added it to the MASTER_DATA sheet first.");
        }
        validation.setShowPromptBox(true);
        validation.createPromptBox("Tip", "Select from the list or add new values to the MASTER_DATA sheet.");
        sheet.addValidationData(validation);
    }

    private void addFormulaDropdown(DataValidationHelper helper, Sheet sheet,
                                    String formula, int column) {
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        DataValidation validation = helper.createValidation(
                constraint, new CellRangeAddressList(DATA_ROW_START, DATA_ROW_END, column, column));
        validation.setShowErrorBox(false);
        sheet.addValidationData(validation);
    }

    private void addDateValidation(DataValidationHelper helper, Sheet sheet, int column) {
        DataValidationConstraint constraint = helper.createCustomConstraint("TRUE");
        DataValidation validation = helper.createValidation(
                constraint, new CellRangeAddressList(DATA_ROW_START, DATA_ROW_END, column, column));
        validation.setShowPromptBox(true);
        validation.createPromptBox("Date Of Joining", "Enter date in dd-MM-yyyy format. Example: 25-06-2026");
        validation.setShowErrorBox(false);
        sheet.addValidationData(validation);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Excel parsing — driven by ColumnDef
    // ─────────────────────────────────────────────────────────────────────────

    public List<EmployeeExcelRowDto> parseEmployeeExcel(MultipartFile file) throws Exception {
        List<EmployeeExcelRowDto> list = new ArrayList<>();
        List<ColumnDef> cols = buildColumnDefs();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(UPLOAD_SHEET_NAME);
            if (sheet == null)
                throw new RuntimeException("Sheet '" + UPLOAD_SHEET_NAME + "' not found");

            for (int i = DATA_ROW_START; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                try {
                    EmployeeExcelRowDto dto = new EmployeeExcelRowDto();

                    for (int c = 0; c < cols.size(); c++) {
                        ColumnDef def  = cols.get(c);
                        Cell      cell = row.getCell(c);

                        if (def.colType() == ColType.DATE) {
                            try {
                                dto.setDateOfJoining(parseDateCell(cell));
                            } catch (Exception dateEx) {
                                // A malformed date on ONE row must not abort parsing of the
                                // whole file; leave it null so the validation stage reports
                                // it as a normal per-row error instead.
                                dto.setDateOfJoining(null);
                            }
                        } else if (def.setter() != null && def.getter() != null) {
                            def.setter().accept(dto, def.getter().apply(cell));
                        }
                    }
                    list.add(dto);
                } catch (Exception ex) {
                    throw new RuntimeException("Error at row " + (i + 1) + ": " + ex.getMessage(), ex);
                }
            }
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Batch create
    // ─────────────────────────────────────────────────────────────────────────
    //
    // KEY CHANGE: The stored procedure now receives vertical, function, domain,
    // and subDomain as plain VARCHAR strings instead of a pre-resolved subDomainId.
    // The DB procedure is responsible for looking up (or creating) the hierarchy IDs.
    //
    // Old call (INCORRECT):
    //   sp_create_user_excel_sheet(... subDomainId, roleCode)
    //
    // New call (CORRECT):
    //   sp_create_user_excel_sheet(... verticalName, functionName, domainName, subDomainName, roleCode)

    private static final String CREATE_USER_EXCEL_SQL =
            "CALL sp_create_user_excel_sheet(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * Whole-file, single-transaction path. Kept for backward compatibility
     * (nothing outside this class calls it, but its behavior/signature/output
     * are preserved byte-for-byte). Prefer {@link #createEmployeesBatch} via
     * an external batch processor for large files.
     */
    @Transactional
    public List<ExcelRowResultDto> batchCreateEmployees(
            Long actorUserId, List<EmployeeExcelRowDto> excelRows) {

        List<ExcelRowResultDto> results = new ArrayList<>(excelRows.size());
        for (int i = 0; i < excelRows.size(); i++) {
            results.add(createSingleEmployeeRow(actorUserId, i + 1, excelRows.get(i)));
        }
        return results;
    }

    /**
     * Runs one chunk of rows in its OWN transaction, independent of any
     * caller-level transaction (REQUIRES_NEW). Must be invoked from a
     * different Spring bean than this one (e.g. EmployeeExcelBatchProcessor)
     * for the propagation to take effect — a call via {@code this.} inside
     * EmployeeExcelService would bypass the transactional proxy.
     * <p>
     * Per-row failures are already caught and converted to a FAILED result
     * inside {@link #createSingleEmployeeRow}, so they never trigger this
     * transaction's rollback — REQUIRES_NEW here is a safety net for
     * non-row-scoped failures (dropped connection, deadlock, DB restart
     * mid-batch), bounding the blast radius to at most one batch of rows.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ExcelRowResultDto> createEmployeesBatch(
            Long actorUserId, List<EmployeeExcelRowDto> batchRows, int startingRowNumber) {

        List<ExcelRowResultDto> results = new ArrayList<>(batchRows.size());
        for (int i = 0; i < batchRows.size(); i++) {
            results.add(createSingleEmployeeRow(actorUserId, startingRowNumber + i, batchRows.get(i)));
        }
        return results;
    }

    /**
     * The ONLY place sp_create_user_excel_sheet is invoked. Same SQL, same
     * 19-parameter order, same password-encoding and result handling as the
     * original inline loop body - extracted verbatim so it can be shared by
     * both the legacy whole-file path and the new per-batch path.
     */
    private ExcelRowResultDto createSingleEmployeeRow(Long actorUserId, int rowNumber, EmployeeExcelRowDto row) {
        try {
            String encryptedPassword = passwordEncoder.encode(row.getOlmid());

            Object[] params = {
                    actorUserId,                      // 1  actor
                    row.getOlmid(),                   // 2  olmid
                    row.getEmployeeName(),             // 3  employee name
                    row.getEmailId(),                  // 4  email
                    row.getMobileNo(),                 // 5  mobile
                    row.getEmploymentType(),           // 6  employment type
                    row.getVendorCompany(),            // 7  vendor company
                    row.getDesignation(),              // 8  designation
                    row.getJobLevel(),                 // 9  job level
                    row.getOfficeLocation(),           // 10 office location
                    row.getGender(),                   // 11 gender
                    row.getDeviceVendorCapability(),   // 12 device vendor capability
                    row.getDateOfJoining(),            // 13 date of joining (LocalDate)
                    encryptedPassword,                 // 14 encrypted password
                    row.getVerticalName(),             // 15 vertical   (string, replaces old null)
                    row.getFunctionName(),             // 16 function   (string, replaces old null)
                    row.getDomainName(),               // 17 domain     (string, replaces old null)
                    row.getSubDomainName(),            // 18 sub-domain (string, replaces old subDomainId)
                    row.getRoleCode()                  // 19 role code
            };

            LOGGER.info("Row {} → {}", rowNumber,
                    CommonService.formatProcedureCall("sp_create_user_excel_sheet", params));

            DbResponse dbResponse = databaseUtils.executeProcedureForMessage(
                    jdbcTemplateTwo, CREATE_USER_EXCEL_SQL, params);

            return new ExcelRowResultDto(rowNumber, row.getOlmid(),
                    "SUCCESS", dbResponse.getSuccessMessage());

        } catch (Exception ex) {
            LOGGER.error("Row {} failed", rowNumber, ex);
            return new ExcelRowResultDto(rowNumber, row.getOlmid(),
                    "FAILED", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error report (validation-stage + DB-stage failures, unified)
    // ─────────────────────────────────────────────────────────────────────────

    public Workbook buildErrorReportWorkbook(String uploadId, List<ExcelValidationErrorDto> rows) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Error_Report");

        CellStyle headerStyle = buildListHeaderStyle(workbook);
        String[] headers = {"Row Number", "Employee ID", "Column Name", "Invalid Value", "Error Message", "Status"};

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }

        int r = 1;
        for (ExcelValidationErrorDto err : rows) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(err.getRowNumber());
            row.createCell(1).setCellValue(nullToEmpty(err.getOlmid()));
            row.createCell(2).setCellValue(nullToEmpty(err.getColumnName()));
            row.createCell(3).setCellValue(nullToEmpty(err.getInvalidValue()));
            row.createCell(4).setCellValue(nullToEmpty(err.getErrorMessage()));
            row.createCell(5).setCellValue(nullToEmpty(err.getStatus()));
        }

        for (int c = 0; c < headers.length; c++) sheet.autoSizeColumn(c);

        LOGGER.info("Upload {} → built error report with {} row(s)", uploadId, rows.size());
        return workbook;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Date parsing
    // ─────────────────────────────────────────────────────────────────────────

    private LocalDate parseDateCell(Cell cell) {
        if (cell == null) return null;

        // Case 1: native Excel date serial (calendar picker / numeric)
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        // Case 2: string / formula result
        String raw;
        try {
            raw = new DataFormatter().formatCellValue(cell).trim();
        } catch (Exception e) {
            return null;
        }
        if (raw.isEmpty()) return null;

        if (raw.contains(" ")) raw = raw.substring(0, raw.indexOf(' '));

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("M/d/yy"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE
        );

        for (DateTimeFormatter fmt : formatters) {
            try { return LocalDate.parse(raw, fmt); }
            catch (DateTimeParseException ignored) { }
        }

        throw new RuntimeException(
                "Unrecognised date '" + raw + "'. Use the calendar picker or type dd-MM-yyyy.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private CellStyle buildListHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setBold(true); s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private CellStyle buildHintStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setItalic(true);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); s.setFont(f);
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

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }
}