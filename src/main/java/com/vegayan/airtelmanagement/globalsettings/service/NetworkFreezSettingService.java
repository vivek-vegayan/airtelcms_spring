package com.vegayan.airtelmanagement.globalsettings.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.globalsettings.dto.LocationDto;
import com.vegayan.airtelmanagement.globalsettings.dto.NetworkFreezeDto;
import com.vegayan.airtelmanagement.me.dto.HolidayDto;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

@Service
public class NetworkFreezSettingService extends BaseService {

    public List<HolidayDto> getHolidayDetails() {

        String sql = "CALL sp_get_holiday_details()";

        LOGGER.info("call sp_get_holiday_details();");

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                HolidayDto.class
        );
    }

    public List<LocationDto> getHolidayLocationDropdown() {

        String sql = "CALL sp_get_holiday_location_dropdown()";

        LOGGER.info("call sp_get_holiday_location_dropdown();");

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                LocationDto.class
        );
    }

    public ApiResponse insertHolidayDetail(
            Long actorUserId,
            String location,
            LocalDate holidayDate,
            String holidayOccasion) {

        String sql = "CALL sp_insert_holiday_detail (?,?,?,?)";

        LOGGER.info(
                "call sp_insert_holiday_detail ('{}','{}','{}','{}');",
                actorUserId,
                location,
                holidayDate,
                holidayOccasion
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                location,
                holidayDate,
                holidayOccasion
        );
    }

    public ApiResponse updateHolidayDetail(
            Long actorUserId,
            Integer holidayId,
            String location,
            LocalDate holidayDate,
            String holidayOccasion) {

        String sql = "CALL sp_update_holiday_detail (?,?,?,?,?)";

        LOGGER.info(
                "call sp_update_holiday_detail ('{}','{}','{}','{}','{}');",
                actorUserId,
                holidayId,
                location,
                holidayDate,
                holidayOccasion
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                holidayId,
                location,
                holidayDate,
                holidayOccasion
        );
    }

    public ApiResponse deleteHolidayById(
            Long actorUserId,
            Integer holidayId) {

        String sql = "CALL sp_delete_holiday_by_id(?,?)";

        LOGGER.info(
                "call sp_delete_holiday_by_id('{}','{}');",
                actorUserId,
                holidayId
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                holidayId
        );
    }

    // =====================================================
    // NETWORK FREEZE VIEW
    // =====================================================

    public List<NetworkFreezeDto> getNetworkFreezeDetails() {

        String sql = "CALL sp_get_network_freeze()";

        LOGGER.info("call sp_get_network_freeze();");

        return jdbcTemplateTwo.query(sql, (rs, rowNum) -> {

            NetworkFreezeDto dto = new NetworkFreezeDto();

            dto.setFreezeId(rs.getInt("Freeze_ID"));
            dto.setFreezeName(rs.getString("Freeze_Name"));

            Timestamp startTs = rs.getTimestamp("Start_DateTime");
            if (startTs != null) {
                dto.setStartDateTime(startTs.toLocalDateTime());
            }

            Timestamp endTs = rs.getTimestamp("End_DateTime");
            if (endTs != null) {
                dto.setEndDateTime(endTs.toLocalDateTime());
            }

            return dto;
        });
    }

    // =====================================================
    // NETWORK FREEZE INSERT
    // =====================================================

    public ApiResponse insertNetworkFreeze(
            Long actorUserId,
            String freezeName,
            String startDateTime,
            String endDateTime,
            String remarks) {

        String sql = "CALL sp_insert_network_freeze(?,?,?,?,?)";

        LOGGER.info(
                "call sp_insert_network_freeze('{}','{}','{}','{}','{}');",
                actorUserId,
                freezeName,
                startDateTime,
                endDateTime,
                remarks
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                freezeName,
                startDateTime,
                endDateTime,
                remarks
        );
    }

    // =====================================================
    // NETWORK FREEZE UPDATE
    // =====================================================

    public ApiResponse updateNetworkFreeze(
            Long actorUserId,
            Integer freezeId,
            String freezeName,
            String startDateTime,
            String endDateTime,
            String remarks) {

        String sql = "CALL sp_update_network_freeze(?,?,?,?,?,?)";

        LOGGER.info(
                "call sp_update_network_freeze('{}','{}','{}','{}','{}','{}');",
                actorUserId,
                freezeId,
                freezeName,
                startDateTime,
                endDateTime,
                remarks
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                freezeId,
                freezeName,
                startDateTime,
                endDateTime,
                remarks
        );
    }

    // =====================================================
    // NETWORK FREEZE DELETE
    // =====================================================

    public ApiResponse deleteNetworkFreeze(
            Long actorUserId,
            Integer freezeId) {

        String sql = "CALL sp_delete_network_freeze(?,?)";

        LOGGER.info(
                "call sp_delete_network_freeze('{}','{}');",
                actorUserId,
                freezeId
        );

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                freezeId
        );
    }
}