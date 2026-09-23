package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.schedular.dto.TaskConfigDto;
import com.vegayan.airtelmanagement.schedular.service.TaskConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/schedular")
public class TaskConfigController {

    private final TaskConfigService taskConfigService;

    public TaskConfigController(TaskConfigService taskConfigService) {
        this.taskConfigService = taskConfigService;
    }

    @GetMapping("/task-config")
    public List<TaskConfigDto> getTaskConfig(
            @RequestParam Integer domainId,
            @RequestParam Integer subDomainId

    ) {

        return taskConfigService.getTaskConfig(domainId,subDomainId);
    }


    @PatchMapping("/task-config/update")
    public ResponseEntity<ApiResponse> updateTaskConfig(
            Authentication authentication,
            @RequestParam Long affectedUserId,
            @RequestParam String colName,
            @RequestParam String newValue
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                taskConfigService.updateTaskConfig( actorUserId, affectedUserId,colName,newValue);

        return ResponseEntity.ok(response);
    }

}