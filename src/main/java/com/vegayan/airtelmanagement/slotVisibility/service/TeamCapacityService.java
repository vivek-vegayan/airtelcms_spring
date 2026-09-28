package com.vegayan.airtelmanagement.slotVisibility.service;

import com.vegayan.airtelmanagement.slotVisibility.dto.EngineerCapacityDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.TeamCapacityCountDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.TotalTeamCountDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamCapacityService {

    private final JdbcTemplate jdbcTemplateTwo;

    public TeamCapacityService(JdbcTemplate jdbcTemplateTwo) {
        this.jdbcTemplateTwo = jdbcTemplateTwo;
    }

    public List<TeamCapacityCountDto> getTeamCapacityCount(
            String fromDate,
            String toDate,
            String teamName,
            String shiftName) {

        String sql = """ 
            SELECT * FROM V_CRQ_TEAM_CAPACITY WHERE shift_date BETWEEN ? AND ? AND team_name = ? AND shift_name = ?
            """;

        return jdbcTemplateTwo.query(
                sql,
                (rs, rowNum) -> {
                    TeamCapacityCountDto dto = new TeamCapacityCountDto();

                    dto.setShiftDate(rs.getString("shift_date"));
                    dto.setTeamName(rs.getString("team_name"));
                    dto.setShiftName(rs.getString("shift_name"));
                    dto.setConfirmed_cnt(rs.getInt("confirmed_cnt"));
                    dto.setReserved_cnt(rs.getInt("reserved_cnt"));
                    dto.setFree_min(rs.getInt("free_min"));

                    return dto;
                },
                fromDate,
                toDate,
                teamName,
                shiftName
        );
    }


    public List<EngineerCapacityDto> getEngineerCapacity(
            String shiftDate,
            String teamName,
            String shiftName) {

        String sql = """
        SELECT *
        FROM V_CRQ_ENGINEER_CAPACITY
        WHERE shift_date = ?
          AND team_name = ?
          AND shift_name = ?
        """;

        return jdbcTemplateTwo.query(
                sql,
                (rs, rowNum) -> {
                    EngineerCapacityDto dto = new EngineerCapacityDto();

                    dto.setRosterId(rs.getLong("roster_id"));
                    dto.setShiftDate(rs.getString("shift_date"));
                    dto.setWorkDate(rs.getString("work_date"));
                    dto.setShiftId(rs.getLong("shift_id"));
                    dto.setShiftName(rs.getString("shift_name"));
                    dto.setDomainId(rs.getLong("domain_id"));
                    dto.setTeamId(rs.getLong("team_id"));
                    dto.setTeamName(rs.getString("team_name"));
                    dto.setUserId(rs.getLong("user_id"));
                    dto.setOlmid(rs.getString("olmid"));
                    dto.setEmployeeName(rs.getString("employee_name"));
                    dto.setJobLevel(rs.getString("job_level"));
                    dto.setVendorCapability(rs.getString("vendor_capability"));
                    dto.setWindowMin(rs.getInt("window_min"));
                    dto.setAvailableMin(rs.getInt("available_min"));
                    dto.setReservedMin(rs.getInt("reserved_min"));
                    dto.setReservedCnt(rs.getInt("reserved_cnt"));
                    dto.setConfirmedMin(rs.getInt("confirmed_min"));
                    dto.setConfirmedCnt(rs.getInt("confirmed_cnt"));
                    dto.setDayStatus(rs.getString("day_status"));
                    dto.setFreeMin(rs.getInt("free_min"));
                    dto.setUtilisationPct(rs.getBigDecimal("utilisation_pct"));

                    return dto;
                },
                shiftDate,
                teamName,
                shiftName
        );
    }

    public TotalTeamCountDto getTotalTeamCount(
            String fromDate,
            String toDate,
            String teamName,
            String shiftName) {

        String sql = """
        SELECT
            COALESCE(SUM(activities_that_fit), 0) AS activities_that_fit,
            COALESCE(SUM(reserved_cnt), 0) AS reserved_cnt,
            COALESCE(SUM(confirmed_cnt), 0) AS confirmed_cnt
        FROM V_CRQ_PLAN_SLOT_SUMMARY
        WHERE shift_date BETWEEN ? AND ?
          AND team_name = ?
          AND shift_name = ?
        """;

        return jdbcTemplateTwo.queryForObject(
                sql,
                (rs, rowNum) -> {
                    TotalTeamCountDto dto = new TotalTeamCountDto();

                    dto.setActivities_that_fit(
                            rs.getInt("activities_that_fit")
                    );

                    dto.setReserved_cnt(
                            rs.getInt("reserved_cnt")
                    );

                    dto.setConfirmed_cnt(
                            rs.getInt("confirmed_cnt")
                    );

                    return dto;
                },
                fromDate,
                toDate,
                teamName,
                shiftName
        );
    }
}
