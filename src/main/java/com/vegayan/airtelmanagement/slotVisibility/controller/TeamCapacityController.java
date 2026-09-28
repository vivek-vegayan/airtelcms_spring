package com.vegayan.airtelmanagement.slotVisibility.controller;

import com.vegayan.airtelmanagement.cabmanager.service.CabCrqService;
import com.vegayan.airtelmanagement.notification.dto.CabRejectReasonDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.EngineerCapacityDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.TeamCapacityCountDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.TotalTeamCountDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.getTotalTeamCountDto;
import com.vegayan.airtelmanagement.slotVisibility.service.TeamCapacityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/slotVisibility/teamCapacity")
public class TeamCapacityController {

    private final TeamCapacityService teamCapacityService;

    public TeamCapacityController(TeamCapacityService teamCapacityService) {
        this.teamCapacityService = teamCapacityService;
    }
    @GetMapping("/teamCapacityCount")
    public List<TeamCapacityCountDto> getTeamCapacityCount(
            @RequestParam String fromDate,
            @RequestParam String toDate,
            @RequestParam String teamName,
            @RequestParam String shiftName) {
        System.out.println(fromDate);
        System.out.println(toDate);
        System.out.println(teamName);
        System.out.println(shiftName);
        return teamCapacityService.getTeamCapacityCount(
                fromDate,
                toDate,
                teamName,
                shiftName
        );
    }

    @GetMapping("/selectedSlotCount")
    public List<EngineerCapacityDto> getEngineerCapacity(
            @RequestParam String   shiftDate,
            @RequestParam String teamName,
            @RequestParam String shiftName) {
        System.out.println(shiftDate);
        System.out.println(teamName);
        System.out.println(shiftName);
        return teamCapacityService.getEngineerCapacity(
                 shiftDate,
                teamName,
                shiftName
        );
    }

    @GetMapping("/totalTeamCount")
    public TotalTeamCountDto getTotalTeamCount(
            @RequestParam String fromDate,
            @RequestParam String toDate,
            @RequestParam String teamName,
            @RequestParam String shiftName) {

        System.out.println("fromDate = " + fromDate);
        System.out.println("toDate = " + toDate);
        System.out.println("teamName = " + teamName);
        System.out.println("shiftName = " + shiftName);

        return teamCapacityService.getTotalTeamCount(
                fromDate,
                toDate,
                teamName,
                shiftName
        );
    }









}
