package com.vegayan.airtelmanagement.slotVisibility.service;

import com.vegayan.airtelmanagement.slotVisibility.dto.AllPlansDTO;
import com.vegayan.airtelmanagement.slotVisibility.dto.ShowAvailabilityDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AllPlansService {

    private final JdbcTemplate jdbcTemplateTwo;

    public AllPlansService(JdbcTemplate jdbcTemplateTwo) {
        this.jdbcTemplateTwo = jdbcTemplateTwo;
    }


    public List<AllPlansDTO> getAllPlans(
            String teamName
    ) {

        String sql = """
        SELECT
             shift_date,
             activities_that_fit ,
              slot_status ,
              shift_name ,
               required_min ,
               plan_type,
               domain
        FROM V_CRQ_PLAN_SLOT_SUMMARY
        WHERE team_id = ?
        """;

        return jdbcTemplateTwo.query(
                sql,
                (rs, rowNum) -> {

                    AllPlansDTO dto = new AllPlansDTO();

                    dto.setShift_date(
                            rs.getString("shift_date")
                    );

                    dto.setActivities_that_fit(
                            rs.getInt("activities_that_fit")
                    );

                    dto.setPlan_type(
                            rs.getString("plan_type")
                    );

                    dto.setSlot_status(
                            rs.getString("slot_status")
                    );

                    dto.setShift_name(
                            rs.getString("shift_name")
                    );

                    dto.setRequired_min(
                            rs.getInt("required_min")
                    );

                    dto.setDomain(
                            rs.getString("domain")
                    );

                    return dto;
                },
                teamName
        );
    }
}
