package com.vegayan.airtelmanagement.globalsettings.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.globalsettings.dto.LocationDto;
import com.vegayan.airtelmanagement.globalsettings.dto.NetworkFreezeDto;
import com.vegayan.airtelmanagement.globalsettings.service.NetworkFreezSettingService;
import com.vegayan.airtelmanagement.me.dto.HolidayDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/networkfreez")
public class NetworkFreezSettingController {

    private final NetworkFreezSettingService networkFreezSettingService;

    public NetworkFreezSettingController(
            NetworkFreezSettingService networkFreezSettingService) {
        this.networkFreezSettingService = networkFreezSettingService;
    }

    // =====================================================
    // HOLIDAY APIs
    // =====================================================

    @GetMapping("/holiday/view")
    public List<HolidayDto> getHolidayDetails() {
        return networkFreezSettingService.getHolidayDetails();
    }

    @GetMapping("/holiday/location/dropdown")
    public List<LocationDto> getHolidayLocationDropdown() {
        return networkFreezSettingService.getHolidayLocationDropdown();
    }

    @PostMapping("/holiday/insert")
    public ResponseEntity<ApiResponse> insertHolidayDetail(
            Authentication authentication,
            @RequestParam String location,
            @RequestParam LocalDate holidayDate,
            @RequestParam String holidayOccasion) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                networkFreezSettingService.insertHolidayDetail(
                        actorUserId,
                        location,
                        holidayDate,
                        holidayOccasion
                );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/holiday/update")
    public ResponseEntity<ApiResponse> updateHolidayDetail(
            Authentication authentication,
            @RequestParam Integer holidayId,
            @RequestParam String location,
            @RequestParam LocalDate holidayDate,
            @RequestParam String holidayOccasion) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                networkFreezSettingService.updateHolidayDetail(
                        actorUserId,
                        holidayId,
                        location,
                        holidayDate,
                        holidayOccasion
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/holiday/delete/{holidayId}")
    public ApiResponse deleteHolidayById(
            Authentication authentication,
            @PathVariable Integer holidayId) {

        Long actorUserId =
                Long.parseLong(authentication.getName());

        return networkFreezSettingService.deleteHolidayById(
                actorUserId,
                holidayId
        );
    }

    // =====================================================
    // NETWORK FREEZE APIs
    // =====================================================

    @GetMapping("/networkfreezedays/view")
    public List<NetworkFreezeDto> getNetworkFreezeDetails() {
        return networkFreezSettingService.getNetworkFreezeDetails();
    }

    @PostMapping("/networkfreeze/insert")
    public ResponseEntity<ApiResponse> insertNetworkFreeze(
            Authentication authentication,
            @RequestParam String freezeName,
            @RequestParam String startDateTime,
            @RequestParam String endDateTime,
            @RequestParam String remarks) {

        Long actorUserId =
                Long.valueOf(authentication.getName());

        ApiResponse response =
                networkFreezSettingService.insertNetworkFreeze(
                        actorUserId,
                        freezeName,
                        startDateTime,
                        endDateTime,
                        remarks
                );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/networkfreeze/update")
    public ResponseEntity<ApiResponse> updateNetworkFreeze(
            Authentication authentication,
            @RequestParam Integer freezeId,
            @RequestParam String freezeName,
            @RequestParam String startDateTime,
            @RequestParam String endDateTime,
            @RequestParam String remarks) {

        Long actorUserId =
                Long.valueOf(authentication.getName());

        ApiResponse response =
                networkFreezSettingService.updateNetworkFreeze(
                        actorUserId,
                        freezeId,
                        freezeName,
                        startDateTime,
                        endDateTime,
                        remarks
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/networkfreeze/delete/{freezeId}")
    public ApiResponse deleteNetworkFreeze(
            Authentication authentication,
            @PathVariable Integer freezeId) {

        Long actorUserId =
                Long.parseLong(authentication.getName());

        return networkFreezSettingService.deleteNetworkFreeze(
                actorUserId,
                freezeId
        );
    }
}