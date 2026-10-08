package com.vegayan.airtelmanagement.activity.service;

import com.vegayan.airtelmanagement.activity.dto.ActivityPhaseViewDTO;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelParseResponseDto;
import com.vegayan.airtelmanagement.activity.dto.PlanActivityExcelPhaseDto;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
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

/**
 * Bulk upload for the static template at public/templates/Plan_Activity_Upload_Template.xlsx
 * (React). Sheet "Upload": row 1 banner, row 2 header, data from row 3. One Activity = a block
 * of 6 rows sharing an Activity Ref (col A), one row per phase. Plan / Activity columns B-K
 * are read from the block's CRQ Review row only. Each phase row is one sp_insert_plan_activity call.
 */
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

    private static final String UPLOAD_SHEET_NAME = "Upload";
    private static final int DATA_ROW_START = 2; // 0-based row index (Excel row 3)

    // Upload sheet columns (0-based)
    private static final int COL_REF = 0, COL_VERTICAL = 1, COL_FUNCTION = 2, COL_DOMAIN = 3, COL_SUB_DOMAIN = 4,
            COL_NETWORK_DOMAIN = 5, COL_LAYER = 6, COL_PLAN_TYPE = 7, COL_VENDOR = 8, COL_IMPACT = 9,
            COL_ACTIVITY_NAME = 10, COL_PHASE = 11, COL_SHIFT = 12, COL_LEVEL = 13, COL_TIME = 14,
            COL_TEAM = 15, COL_DAYS_MARGIN = 16, COL_RESERVATION_MARGIN = 17, COL_ROLLBACK_TIME = 18;

    private static final String CRQ_REVIEW = "CRQ Review";
    private static final String EXECUTION = "Execution";
    // Must match the CASE in sp_insert_plan_activity; also the insert order.
    private static final List<String> PHASES = List.of(
            CRQ_REVIEW, "Impact Analysis", "Scheduling", "MOP Creation", "MOP Validation", EXECUTION);

    private static final String TEAM_PATH_SEPARATOR = ">";

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────────

    public List<PlanActivityExcelRowDto> parseExcel(MultipartFile file) throws Exception {
        Map<String, PlanActivityExcelRowDto> byRef = new LinkedHashMap<>();
        DataFormatter fmt = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(UPLOAD_SHEET_NAME);
            if (sheet == null) {
                throw new RuntimeException(
                        "Sheet '" + UPLOAD_SHEET_NAME + "' not found. Please use the provided template.");
            }

            for (int i = DATA_ROW_START; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(fmt, row)) continue;

                int rowNumber = i + 1;
                String ref = text(fmt, row, COL_REF);
                // A filled row without a ref still becomes its own block so it is reported, not dropped.
                String key = ref != null ? ref : "(row " + rowNumber + ")";

                PlanActivityExcelRowDto activity = byRef.computeIfAbsent(key, k -> {
                    PlanActivityExcelRowDto a = new PlanActivityExcelRowDto();
                    a.setRowNumber(rowNumber);
                    a.setActivityRef(k);
                    return a;
                });

                PlanActivityExcelPhaseDto phase = new PlanActivityExcelPhaseDto();
                phase.setRowNumber(rowNumber);
                phase.setPhase(text(fmt, row, COL_PHASE));
                phase.setShift(text(fmt, row, COL_SHIFT));
                phase.setMinimumLevelRequirement(text(fmt, row, COL_LEVEL));
                phase.setRequiredTimeMinutes(intVal(fmt, row, COL_TIME));
                phase.setTeamPath(text(fmt, row, COL_TEAM));
                phase.setDaysMargin(intVal(fmt, row, COL_DAYS_MARGIN));
                phase.setReservationMargin(intVal(fmt, row, COL_RESERVATION_MARGIN));
                phase.setRollbackTime(intVal(fmt, row, COL_ROLLBACK_TIME));
                activity.getPhases().add(phase);

                if (CRQ_REVIEW.equalsIgnoreCase(safe(phase.getPhase()))) {
                    activity.setVerticalName(text(fmt, row, COL_VERTICAL));
                    activity.setFunctionName(text(fmt, row, COL_FUNCTION));
                    activity.setChmDomainName(text(fmt, row, COL_DOMAIN));
                    activity.setChmSubDomainName(text(fmt, row, COL_SUB_DOMAIN));
                    activity.setNetworkDomain(text(fmt, row, COL_NETWORK_DOMAIN));
                    activity.setLayer(text(fmt, row, COL_LAYER));
                    activity.setPlanType(text(fmt, row, COL_PLAN_TYPE));
                    activity.setVendorOem(text(fmt, row, COL_VENDOR));
                    activity.setChangeImpact(text(fmt, row, COL_IMPACT));
                    activity.setActivityName(text(fmt, row, COL_ACTIVITY_NAME));
                }
            }
        }

        // Unused template blocks only carry the pre-filled Activity Ref + Phase — skip them.
        List<PlanActivityExcelRowDto> list = new ArrayList<>();
        for (PlanActivityExcelRowDto a : byRef.values()) {
            if (!isBlockEmpty(a)) list.add(a);
        }
        return list;
    }

    private boolean isBlockEmpty(PlanActivityExcelRowDto a) {
        boolean planEmpty = isBlank(a.getVerticalName()) && isBlank(a.getFunctionName())
                && isBlank(a.getChmDomainName()) && isBlank(a.getChmSubDomainName())
                && isBlank(a.getNetworkDomain()) && isBlank(a.getLayer()) && isBlank(a.getPlanType())
                && isBlank(a.getVendorOem()) && isBlank(a.getChangeImpact()) && isBlank(a.getActivityName());
        if (!planEmpty) return false;
        for (PlanActivityExcelPhaseDto p : a.getPhases()) {
            if (!isBlank(p.getShift()) || !isBlank(p.getMinimumLevelRequirement())
                    || p.getRequiredTimeMinutes() != null || !isBlank(p.getTeamPath())
                    || p.getDaysMargin() != null || p.getReservationMargin() != null || p.getRollbackTime() != null) {
                return false;
            }
        }
        return true;
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

    /** Only data columns A-S count — column T is a helper formula. */
    private boolean isRowEmpty(DataFormatter fmt, Row row) {
        for (int c = COL_REF; c <= COL_ROLLBACK_TIME; c++) {
            if (text(fmt, row, c) != null) return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────────

    public PlanActivityExcelParseResponseDto parseAndValidate(Long actorUserId, MultipartFile file) throws Exception {
        List<PlanActivityExcelRowDto> rows = parseExcel(file);
        List<PlanActivityValidationErrorDto> errors = validateRows(actorUserId, rows);

        Set<String> invalidRefs = new HashSet<>();
        for (PlanActivityValidationErrorDto e : errors) invalidRefs.add(e.getActivityRef());

        return new PlanActivityExcelParseResponseDto(
                rows.size(), rows.size() - invalidRefs.size(), invalidRefs.size(), rows, errors);
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

        // sp_insert_plan_activity resolves the Assigned Team by Sub Domain NAME only
        // (org-wide, LIMIT 1), so a name that appears under more than one Domain can't
        // be mapped safely — count occurrences to reject those instead of guessing.
        Map<String, Integer> subDomainNameCount = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SubDomainDto sd : hierarchy.getSubDomains()) subDomainNameCount.merge(sd.getName(), 1, Integer::sum);

        Set<String> teamPaths = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        teamPaths.addAll(buildTeamPaths(hierarchy));

        Map<String, Integer> comboActivityFirstRow = new HashMap<>();
        Map<String, Integer> comboPlanIdCache = new HashMap<>();
        Map<Integer, List<ActivityPhaseViewDTO.ActivityEntry>> planActivitiesCache = new HashMap<>();

        for (PlanActivityExcelRowDto row : rows) {
            String ref = row.getActivityRef();
            PlanActivityExcelPhaseDto crqRow = findPhase(row, CRQ_REVIEW);
            int rn = crqRow != null ? crqRow.getRowNumber() : row.getRowNumber();

            VerticalDto vertical = null;
            if (isBlank(row.getVerticalName())) {
                errors.add(err(rn, ref, "Vertical", row.getVerticalName(), "Required (on the CRQ Review row)"));
            } else {
                vertical = verticalByName.get(row.getVerticalName().trim());
                if (vertical == null) {
                    errors.add(err(rn, ref, "Vertical", row.getVerticalName(), "Invalid Vertical — not found"));
                }
            }

            TeamFunctionDto function = null;
            if (isBlank(row.getFunctionName())) {
                errors.add(err(rn, ref, "Team Function", row.getFunctionName(), "Required (on the CRQ Review row)"));
            } else if (vertical != null) {
                function = functionsByVertical.getOrDefault(vertical.getId(), Map.of())
                        .get(row.getFunctionName().trim());
                if (function == null) {
                    errors.add(err(rn, ref, "Team Function", row.getFunctionName(),
                            "Invalid Hierarchy Mapping — not found under Vertical '" + row.getVerticalName().trim() + "'"));
                }
            }

            DomainDto domain = null;
            if (isBlank(row.getChmDomainName())) {
                errors.add(err(rn, ref, "CHM Domain", row.getChmDomainName(), "Required (on the CRQ Review row)"));
            } else if (function != null) {
                domain = domainsByFunction.getOrDefault(function.getId(), Map.of())
                        .get(row.getChmDomainName().trim());
                if (domain == null) {
                    errors.add(err(rn, ref, "CHM Domain", row.getChmDomainName(),
                            "Invalid Hierarchy Mapping — not found under Team Function '" + row.getFunctionName().trim() + "'"));
                }
            }

            SubDomainDto subDomain = null;
            if (isBlank(row.getChmSubDomainName())) {
                errors.add(err(rn, ref, "CHM Sub Domain", row.getChmSubDomainName(), "Required (on the CRQ Review row)"));
            } else if (domain != null) {
                subDomain = subDomainsByDomain.getOrDefault(domain.getId(), Map.of())
                        .get(row.getChmSubDomainName().trim());
                if (subDomain == null) {
                    errors.add(err(rn, ref, "CHM Sub Domain", row.getChmSubDomainName(),
                            "Invalid Hierarchy Mapping — not found under CHM Domain '" + row.getChmDomainName().trim() + "'"));
                }
            }

            requireText(errors, rn, ref, "Network Domain", row.getNetworkDomain());
            requireText(errors, rn, ref, "Layer", row.getLayer());
            requireText(errors, rn, ref, "Plan Type", row.getPlanType());
            requireText(errors, rn, ref, "Vendor / OEM", row.getVendorOem());
            requireText(errors, rn, ref, "Impact", row.getChangeImpact());

            if (isBlank(row.getActivityName())) {
                errors.add(err(rn, ref, "Activity Name", row.getActivityName(), "Required (on the CRQ Review row)"));
            } else if (row.getActivityName().trim().length() > 30) {
                errors.add(err(rn, ref, "Activity Name", row.getActivityName(), "Must be 30 characters or fewer"));
            }

            validatePhases(errors, row, subDomainNameCount, teamPaths);

            if (domain != null && subDomain != null && !isBlank(row.getActivityName())) {
                checkDuplicateActivity(errors, actorUserId, rn, row, domain, subDomain,
                        comboActivityFirstRow, comboPlanIdCache, planActivitiesCache);
            }
        }

        return errors;
    }

    private void validatePhases(List<PlanActivityValidationErrorDto> errors, PlanActivityExcelRowDto row,
                                Map<String, Integer> subDomainNameCount, Set<String> teamPaths) {
        String ref = row.getActivityRef();
        Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (PlanActivityExcelPhaseDto p : row.getPhases()) {
            int rn = p.getRowNumber();
            String phase = safe(p.getPhase());

            if (phase.isEmpty()) {
                errors.add(err(rn, ref, "Phase", p.getPhase(), "Required"));
                continue;
            }
            if (PHASES.stream().noneMatch(phase::equalsIgnoreCase)) {
                errors.add(err(rn, ref, "Phase", p.getPhase(), "Invalid Phase — must be one of " + PHASES));
                continue;
            }
            if (!seen.add(phase)) {
                errors.add(err(rn, ref, "Phase", p.getPhase(), "Duplicate Phase within Activity Ref " + ref));
            }

            if (p.getRequiredTimeMinutes() == null) {
                errors.add(err(rn, ref, phase + " Time (Min)", "", "Required or must be a valid non-negative number"));
            } else if (p.getRequiredTimeMinutes() < 0) {
                errors.add(err(rn, ref, phase + " Time (Min)", str(p.getRequiredTimeMinutes()), "Must be a non-negative number"));
            }

            if (EXECUTION.equalsIgnoreCase(phase)) {
                nonNegative(errors, rn, ref, phase + " Days Margin", p.getDaysMargin());
                nonNegative(errors, rn, ref, phase + " Reservation Margin", p.getReservationMargin());
                nonNegative(errors, rn, ref, phase + " Rollback Time", p.getRollbackTime());
            }

            String teamName = resolveTeamName(row, p);
            String teamValue = isBlank(p.getTeamPath()) ? row.getChmSubDomainName() : p.getTeamPath();
            if (isBlank(teamName)) {
                errors.add(err(rn, ref, phase + " Assigned Team", teamValue, "Required"));
            } else if (p.getTeamPath() != null && p.getTeamPath().contains(TEAM_PATH_SEPARATOR)
                    && !teamPaths.contains(normalizePath(p.getTeamPath()))) {
                errors.add(err(rn, ref, phase + " Assigned Team", teamValue, "Invalid Team — path not found"));
            } else {
                int count = subDomainNameCount.getOrDefault(teamName, 0);
                if (count == 0) {
                    errors.add(err(rn, ref, phase + " Assigned Team", teamValue, "Invalid Team — not found"));
                } else if (count > 1) {
                    errors.add(err(rn, ref, phase + " Assigned Team", teamValue,
                            "Ambiguous Team — Sub Domain '" + teamName + "' exists under more than one Domain"));
                }
            }
        }

        for (String expected : PHASES) {
            if (!seen.contains(expected)) {
                errors.add(err(row.getRowNumber(), ref, "Phase", expected, "Missing phase row for Activity Ref " + ref));
            }
        }
    }

    /** The procedure takes a Sub Domain name: last segment of the Team Path, or the
     *  activity's own Sub Domain when the Team column is blank. */
    private String resolveTeamName(PlanActivityExcelRowDto row, PlanActivityExcelPhaseDto p) {
        if (isBlank(p.getTeamPath())) return safe(row.getChmSubDomainName());
        String[] parts = p.getTeamPath().split(TEAM_PATH_SEPARATOR);
        return parts[parts.length - 1].trim();
    }

    private List<String> buildTeamPaths(OrgHierarchyResponse hierarchy) {
        Map<Long, VerticalDto> verticals = new HashMap<>();
        for (VerticalDto v : hierarchy.getVerticals()) verticals.put(v.getId(), v);
        Map<Long, TeamFunctionDto> functions = new HashMap<>();
        for (TeamFunctionDto f : hierarchy.getTeamFunction()) functions.put(f.getId(), f);
        Map<Long, DomainDto> domains = new HashMap<>();
        for (DomainDto d : hierarchy.getDomains()) domains.put(d.getId(), d);

        List<String> paths = new ArrayList<>();
        for (SubDomainDto sd : hierarchy.getSubDomains()) {
            DomainDto d = domains.get(sd.getDomainId());
            TeamFunctionDto f = d == null ? null : functions.get(d.getFunctionId());
            VerticalDto v = f == null ? null : verticals.get(f.getVerticalId());
            if (v == null) continue;
            paths.add(normalizePath(String.join(TEAM_PATH_SEPARATOR,
                    v.getName(), f.getName(), d.getName(), sd.getName())));
        }
        return paths;
    }

    private String normalizePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split(TEAM_PATH_SEPARATOR)) {
            if (!sb.isEmpty()) sb.append(" > ");
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private PlanActivityExcelPhaseDto findPhase(PlanActivityExcelRowDto row, String phase) {
        for (PlanActivityExcelPhaseDto p : row.getPhases()) {
            if (phase.equalsIgnoreCase(safe(p.getPhase()))) return p;
        }
        return null;
    }

    private void checkDuplicateActivity(List<PlanActivityValidationErrorDto> errors, Long actorUserId, int rn,
                                         PlanActivityExcelRowDto row, DomainDto domain, SubDomainDto subDomain,
                                         Map<String, Integer> comboActivityFirstRow,
                                         Map<String, Integer> comboPlanIdCache,
                                         Map<Integer, List<ActivityPhaseViewDTO.ActivityEntry>> planActivitiesCache) {

        String ref = row.getActivityRef();
        String comboKey = String.join("|",
                String.valueOf(domain.getId()), String.valueOf(subDomain.getId()),
                safe(row.getLayer()), safe(row.getPlanType()),
                safe(row.getVendorOem()), safe(row.getChangeImpact())).toLowerCase();

        String activityKey = comboKey + "::" + row.getActivityName().trim().toLowerCase();
        Integer priorRow = comboActivityFirstRow.get(activityKey);

        if (priorRow != null) {
            errors.add(err(rn, ref, "Activity Name", row.getActivityName(),
                    "Duplicate Activity — same name already used for this Plan on row " + priorRow));
        } else {
            comboActivityFirstRow.put(activityKey, rn);
        }

        // Same Plan match as sp_insert_plan_activity's "EXISTING PLAN" lookup
        // (chm_domain/chm_sub_domain/layer/plan_type/vendor_oem/change_impact).
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

        if (planId < 0) {
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
            errors.add(err(rn, ref, "Activity Name", row.getActivityName(),
                    "Duplicate Activity — an activity with this name already exists for this Plan"));
        }
    }

    private void nonNegative(List<PlanActivityValidationErrorDto> errors, int rn, String ref, String column, Integer value) {
        if (value != null && value < 0) errors.add(err(rn, ref, column, str(value), "Must be a non-negative number"));
    }

    private void requireText(List<PlanActivityValidationErrorDto> errors, int rn, String ref, String column, String value) {
        if (isBlank(value)) errors.add(err(rn, ref, column, value, "Required (on the CRQ Review row)"));
    }

    private PlanActivityValidationErrorDto err(int rn, String ref, String column, String value, String error) {
        return new PlanActivityValidationErrorDto(rn, ref, column, value, error);
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

    // Not @Transactional: sp_insert_plan_activity runs its own START TRANSACTION / COMMIT
    // per phase, so each phase is committed as soon as its call returns.
    public PlanActivityExcelUploadSummaryDto insertBatch(Long actorUserId, List<PlanActivityExcelRowDto> rows) {
        long start = System.currentTimeMillis();
        List<PlanActivityExcelRowResultDto> results = new ArrayList<>();

        if (rows == null) rows = List.of();

        List<PlanActivityValidationErrorDto> errors = validateRows(actorUserId, rows);
        Map<String, String> firstErrorByRef = new LinkedHashMap<>();
        for (PlanActivityValidationErrorDto e : errors) {
            firstErrorByRef.putIfAbsent(e.getActivityRef(),
                    "Row " + e.getRowNumber() + " — " + e.getColumn() + ": " + e.getError());
        }

        String sql = "CALL sp_insert_plan_activity(" + "?,".repeat(18) + "?)";

        int success = 0;
        int failed = 0;

        for (PlanActivityExcelRowDto row : rows) {
            if (firstErrorByRef.containsKey(row.getActivityRef())) {
                failed++;
                results.add(new PlanActivityExcelRowResultDto(
                        row.getRowNumber(), row.getActivityRef(), row.getActivityName(), "FAILED",
                        firstErrorByRef.get(row.getActivityRef()), null, null));
                continue;
            }

            PlanActivityInsertResultDto last = null;
            String failure = null;
            int saved = 0;

            for (String phaseName : PHASES) {
                PlanActivityExcelPhaseDto phase = findPhase(row, phaseName);
                assert phase != null;
                phase.setPhase(phaseName); // canonical spelling for the procedure's CASE
                try {
                    Object[] params = PlanActivityExcelMapper.toSqlParams(
                            actorUserId, row, phase, resolveTeamName(row, phase));
                    LOGGER.info("Row {} -> {}", phase.getRowNumber(),
                            CommonService.formatProcedureCall("sp_insert_plan_activity", params));

                    List<PlanActivityInsertResultDto> resultRows = databaseUtils.executeProcedureGetDataWithError(
                            jdbcTemplateTwo, sql, PlanActivityInsertResultDto.class, params);
                    if (!resultRows.isEmpty()) last = resultRows.get(0);
                    saved++;
                } catch (Exception ex) {
                    LOGGER.error("Row {} ({}) failed", phase.getRowNumber(), phaseName, ex);
                    failure = "Row " + phase.getRowNumber() + " — " + phaseName + ": " + ex.getMessage()
                            + (saved > 0 ? " (" + saved + " earlier phase(s) were already saved)" : "");
                    break;
                }
            }

            if (failure == null) success++;
            else failed++;
            results.add(new PlanActivityExcelRowResultDto(
                    row.getRowNumber(), row.getActivityRef(), row.getActivityName(),
                    failure == null ? "SUCCESS" : "FAILED",
                    failure == null ? "Inserted " + PHASES.size() + " phases" : failure,
                    last != null ? last.getPlanId() : null,
                    last != null ? last.getActivityId() : null));
        }

        long elapsed = System.currentTimeMillis() - start;
        return new PlanActivityExcelUploadSummaryDto(rows.size(), success, failed, elapsed, results);
    }
}
