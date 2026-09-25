package com.vegayan.airtelmanagement.rosterview.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.rosterview.dto.CurrentShiftCountDto;
import com.vegayan.airtelmanagement.rosterview.dto.DailyRosterDto;
import com.vegayan.airtelmanagement.rosterview.dto.MonthlyRosterResponseDto;
import com.vegayan.airtelmanagement.rosterview.dto.RosterImportRequestDto;
import com.vegayan.airtelmanagement.rosterview.dto.RosterImportResponseDto;
import com.vegayan.airtelmanagement.rosterview.dto.RosterRowDto;
import com.vegayan.airtelmanagement.rosterview.dto.ShiftDropDownsDto;
import com.vegayan.airtelmanagement.rosterview.dto.UserRosterDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonthlyRosterViewService extends BaseService {

    @Autowired
    private ObjectMapper objectMapper;

    public MonthlyRosterResponseDto getRosterMonthlyAndWeekly(
            Long domainId,
            Long subDomainId,
            LocalDate startDate,
            LocalDate endDate
    ) {

        String sql =
                "CALL sp_get_roster_page_monthlyAndweekly(?,?,?,?)";

        LOGGER.info(
                "call sp_get_roster_page_monthlyAndweekly('{}','{}','{}','{}');",
                domainId,
                subDomainId,
                startDate,
                endDate
        );

        List<RosterRowDto> rows =
                databaseUtils.executeProcedureGetDataWithError(
                        jdbcTemplateTwo,
                        sql,
                        RosterRowDto.class,
                        domainId,
                        subDomainId,
                        startDate,
                        endDate
                );

        return buildResponseMonthlyAndWeekly(
                rows,
                startDate,
                endDate
        );
    }


    public MonthlyRosterResponseDto getActorRoster(
            String userId,
            LocalDate startDate,
            LocalDate endDate
    ) {

        String sql =
                "CALL sp_get_actor_roster_page_monthlyAndweekly(?,?,?)";

        LOGGER.info(
                "call sp_get_actor_roster_page_monthlyAndweekly('{}','{}','{}');",
                userId,
                startDate,
                endDate
        );

        List<RosterRowDto> rows =
                databaseUtils.executeProcedureGetDataWithError(
                        jdbcTemplateTwo,
                        sql,
                        RosterRowDto.class,
                        userId,
                        startDate,
                        endDate
                );

        return buildResponseMonthlyAndWeekly(
                rows,
                startDate,
                endDate
        );
    }


    private MonthlyRosterResponseDto buildResponseMonthlyAndWeekly(
            List<RosterRowDto> rows,
            LocalDate startDate,
            LocalDate endDate
    ) {

        Map<Long, UserRosterDto> userMap =
                new LinkedHashMap<>();

        for (RosterRowDto row : rows) {

            Long userId = row.getUserId();
            LocalDate date = row.getShiftDate();

            UserRosterDto user =
                    userMap.computeIfAbsent(
                            userId,
                            id -> {

                                UserRosterDto dto =
                                        new UserRosterDto();

                                dto.setUserId(id);
                                dto.setOlmid(row.getOlmid());

                                // Make sure UserRosterDto contains this field
                                dto.setEmployeeName(
                                        row.getEmployeeName()
                                );

                                dto.setJobLevel(
                                        row.getJobLevel()
                                );

                                dto.setRoster(
                                        new LinkedHashMap<>()
                                );

                                return dto;
                            }
                    );

            if (user.getRoster().containsKey(date)) {

                LOGGER.warn(
                        "Duplicate roster entry for user {} on {}",
                        userId,
                        date
                );

                continue;
            }

            DailyRosterDto daily = new DailyRosterDto();

            daily.setShiftDisplay(
                    row.getDisplayShift()
            );

            daily.setWorkMode(
                    row.getWorkMode()
            );

            daily.setAssignActCount(
                    row.getAssignActCount() == null
                            ? 0
                            : row.getAssignActCount()
            );

            daily.setAvailableMins(
                    row.getAvailableMins() == null
                            ? 0
                            : row.getAvailableMins()
            );

            user.getRoster().put(
                    date,
                    daily
            );
        }

        MonthlyRosterResponseDto response =
                new MonthlyRosterResponseDto();

        response.setSuccess(true);
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setTotalUsers(userMap.size());
        response.setData(
                new ArrayList<>(userMap.values())
        );

        return response;
    }

    public List<CurrentShiftCountDto> getCurrentShiftCount(
            String domainId,
            String subDomainId
    ) {

        String sql =
                "CALL sp_get_current_shift_count(?, ?)";

        LOGGER.info(
                "call sp_get_current_shift_count('{}','{}');",
                domainId,
                subDomainId
        );

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                CurrentShiftCountDto.class,
                domainId,
                subDomainId
        );
    }

    public ApiResponse changeShift(
            Long actorUserId,
            Long affectedUserId,
            Integer newAssignActivity,
            Integer newAvailableMinutes,
            LocalDate shiftDate,
            Integer newShiftId,
            String reason
    ) {

        String sql =
                "CALL sp_change_shift(?,?,?,?,?,?,?)";

        LOGGER.info(
                "CALL sp_change_shift({}, {}, {}, {}, '{}', {}, '{}')",
                actorUserId,
                affectedUserId,
                newAssignActivity,
                newAvailableMinutes,
                shiftDate,
                newShiftId,
                reason
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                affectedUserId,
                newAssignActivity,
                newAvailableMinutes,
                Date.valueOf(shiftDate),
                newShiftId,
                reason
        );
    }

    /**
     * Save the roster uploaded from Excel. Calls sp_import_roster_shift once
     * per employee (it updates existing days and inserts missing ones).
     * One employee failing doesn't stop the others; the failures are
     * returned so the UI can show them.
     */
    public RosterImportResponseDto importRosterShifts(List<RosterImportRequestDto> employees) {

        String sql = "CALL sp_import_roster_shift(?, ?)";

        LOGGER.info("Roster import request received: {} employee(s)", employees.size());

        int saved = 0;
        List<String> errors = new ArrayList<>();

        for (RosterImportRequestDto employee : employees) {

            if (employee.shifts() == null || employee.shifts().isEmpty()) {
                continue;
            }

            try {
                List<Map<String, Object>> shifts = new ArrayList<>();
                for (RosterImportRequestDto.ShiftEntry shift : employee.shifts()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("shift_date", shift.shiftDate().toString());
                    row.put("shift_id", shift.shiftId());
                    shifts.add(row);
                }
                String rosterJson = objectMapper.writeValueAsString(shifts);

                LOGGER.info(
                        "call sp_import_roster_shift('{}','{}');",
                        employee.olmId(),
                        rosterJson
                );

                databaseUtils.executeProcedureForMessageV1(
                        jdbcTemplateTwo,
                        sql,
                        employee.olmId(),
                        rosterJson
                );
                saved++;

            } catch (DataAccessException e) {
                LOGGER.error("Roster import failed for {}", employee.olmId(), e);
                errors.add(employee.olmId() + ": " + e.getMostSpecificCause().getMessage());
            } catch (Exception e) {
                LOGGER.error("Roster import failed for {}", employee.olmId(), e);
                errors.add(employee.olmId() + ": " + e.getMessage());
            }
        }

        return new RosterImportResponseDto(saved, errors.size(), errors);
    }

    public List<ShiftDropDownsDto> shiftDropDowns() {

        String sql =
                "CALL sp_get_shift_drop_downs()";

        LOGGER.info(
                "call sp_get_shift_drop_downs();"
        );

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                ShiftDropDownsDto.class
        );
    }


    public ApiResponse shiftSwapByManager(
            String actorUserId,
            Long affectedUserId1,
            LocalDate shiftDate1,
            Long affectedUserId2,
            LocalDate shiftDate2,
            String shiftSwapReason
    ) {

        String sql =
                "CALL sp_shift_swap_by_manager(?,?,?,?,?,?)";

        LOGGER.info(
                "call sp_shift_swap_by_manager('{}','{}','{}','{}','{}','{}');",
                actorUserId,
                affectedUserId1,
                shiftDate1,
                affectedUserId2,
                shiftDate2,
                shiftSwapReason
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                affectedUserId1,
                shiftDate1,
                affectedUserId2,
                shiftDate2,
                shiftSwapReason
        );
    }

    public ApiResponse shiftSwapReqByTeamMember(
            String actorUserId,
            LocalDate shiftDate1,
            Long recipientUserId,
            LocalDate shiftDate2,
            String shiftSwapReason
    ) {

        String sql =
                "CALL sp_shift_swap_req_by_team_member(?,?,?,?,?)";

        LOGGER.info(
                "call sp_shift_swap_req_by_team_member('{}','{}','{}','{}','{}');",
                actorUserId,
                shiftDate1,
                recipientUserId,
                shiftDate2,
                shiftSwapReason
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                shiftDate1,
                recipientUserId,
                shiftDate2,
                shiftSwapReason
        );
    }
}