package com.vegayan.airtelmanagement.slotVisibility.service;

import com.vegayan.airtelmanagement.slotVisibility.dto.CheckActivityFilterDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.ShowAvailabilityDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.TotalTeamCountDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CheckActivityService {

    private final JdbcTemplate jdbcTemplateTwo;

    public CheckActivityService(JdbcTemplate jdbcTemplateTwo) {
        this.jdbcTemplateTwo = jdbcTemplateTwo;
    }

    public CheckActivityFilterDto getCheckActivityFilter() {

        String domainSql = """
        SELECT DISTINCT domain
        FROM V_CRQ_PLAN_SLOT_SUMMARY
        WHERE domain IS NOT NULL
        ORDER BY domain
        """;

        String layerSql = """
        SELECT DISTINCT layer
        FROM V_CRQ_PLAN_SLOT_SUMMARY
        WHERE layer IS NOT NULL
        ORDER BY layer
        """;

        String planTypeSql = """
        SELECT DISTINCT plan_type
        FROM V_CRQ_PLAN_SLOT_SUMMARY
        WHERE plan_type IS NOT NULL
        ORDER BY plan_type
        """;

        String changeImpactSql = """
        SELECT DISTINCT change_impact
        FROM V_CRQ_PLAN_SLOT_SUMMARY
        WHERE change_impact IS NOT NULL
        ORDER BY change_impact
        """;

        String vendorOemSql = """
        SELECT DISTINCT vendor_oem
        FROM V_CRQ_PLAN_SLOT_SUMMARY
        WHERE vendor_oem IS NOT NULL
        ORDER BY vendor_oem
        """;

        List<String> domains = jdbcTemplateTwo.query(
                domainSql,
                (rs, rowNum) -> rs.getString("domain")
        );

        List<String> layers = jdbcTemplateTwo.query(
                layerSql,
                (rs, rowNum) -> rs.getString("layer")
        );

        List<String> planTypes = jdbcTemplateTwo.query(
                planTypeSql,
                (rs, rowNum) -> rs.getString("plan_type")
        );

        List<String> changeImpacts = jdbcTemplateTwo.query(
                changeImpactSql,
                (rs, rowNum) -> rs.getString("change_impact")
        );

        List<String> vendorOems = jdbcTemplateTwo.query(
                vendorOemSql,
                (rs, rowNum) -> rs.getString("vendor_oem")
        );

        CheckActivityFilterDto dto = new CheckActivityFilterDto();

        dto.setDomain(domains);
        dto.setLayer(layers);
        dto.setPlan_type(planTypes);
        dto.setChange_impact(changeImpacts);
        dto.setVendor_oem(vendorOems);

        return dto;
    }

    public List<ShowAvailabilityDto> showAvaliability(
            String domain,
            String layer,
            String planType,
            String changeImpact,
            String vendorOem,
            String fromDate,
            String toDate) {

        String sql = """
        SELECT
            shift_date,
            shift_name,
            work_date,
            engineers_rostered,
            engineers_eligible,
            reserved_cnt,
            confirmed_cnt,
            activities_that_fit,
            slot_status
        FROM V_CRQ_PLAN_SLOT_SUMMARY
        WHERE domain = ?
          AND layer = ?
          AND plan_type = ?
          AND change_impact = ?
          AND vendor_oem LIKE ?
          AND shift_date BETWEEN ? AND ?
        ORDER BY shift_date, shift_name
        """;

        return jdbcTemplateTwo.query(
                sql,
                (rs, rowNum) -> {

                    ShowAvailabilityDto dto = new ShowAvailabilityDto();

                    dto.setShiftDate(
                            rs.getString("shift_date")
                    );

                    dto.setShiftName(
                            rs.getString("shift_name")
                    );

                    dto.setWorkWindow(
                            rs.getString("work_date")
                    );

                    dto.setRostered(
                            rs.getInt("engineers_rostered")
                    );

                    dto.setEligible(
                            rs.getInt("engineers_eligible")
                    );

                    dto.setReserved(
                            rs.getInt("reserved_cnt")
                    );

                    dto.setConfirmed(
                            rs.getInt("confirmed_cnt")
                    );

                    dto.setStillFits(
                            rs.getInt("activities_that_fit")
                    );

                    dto.setStatus(
                            rs.getString("slot_status")
                    );

                    return dto;
                },
                domain,
                layer,
                planType,
                changeImpact,
                vendorOem + "%",
                fromDate,
                toDate
        );
    }



}
