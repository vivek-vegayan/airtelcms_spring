package com.vegayan.airtelmanagement.user.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.user.dto.*;
import com.vegayan.airtelmanagement.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


import java.util.List;


@RestController
@RequestMapping("/users")
public class UserController extends BaseService {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> signUp(@RequestBody UserRecord userRecord) {
        LOGGER.info("Received olmId: " + userRecord.olmId());  // Log the olmId
        ApiResponse response = userService.createUser(userRecord);
        if ("success".equals(response.status())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/v1/getloggeduserdetails")
    public List<LoggedUserDto> getLoggedUserDetailsV1(Authentication authentication) {
        String userId = authentication.getName(); // this is JWT subject
        return userService.getLoggedUserDetailsV1(userId);
    }

    @GetMapping("/v2/getloggeduserdetails")
    public LoggedUserResponseDto getLoggedUserDetailsV2(Authentication authentication) {
        String userId = authentication.getName(); // this is JWT subject
        return userService.getLoggedUserDetailsV2(userId);
    }


    @GetMapping("/getOrgHierarchyByUser")
    public ResponseEntity<OrgHierarchyApiResponse<OrgHierarchyResponse>> getOrgHierarchyByUser(
            @RequestParam("userId") Long userId,
            @RequestParam("roleName") String roleName) {

        OrgHierarchyResponse data =
                userService.getOrgHierarchyByUser(String.valueOf(userId), roleName);

        return ResponseEntity.ok(
                OrgHierarchyApiResponse.<OrgHierarchyResponse>builder()
                                       .status("success")
                                       .data(data)
                                       .build()
        );
    }


    @GetMapping("/V1/getOrgHierarchyByUser")
    public ResponseEntity<OrgHierarchyApiResponse<OrgHierarchyResponse>>
    getOrgHierarchyByUserV1(Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        OrgHierarchyResponse data =
                userService.getOrgHierarchyByUserV1(userId);

        return ResponseEntity.ok(
                OrgHierarchyApiResponse.<OrgHierarchyResponse>builder()
                        .status("success")
                        .data(data)
                        .build()
        );
    }

    @GetMapping("/v3/getemployeesbysubdomain")
    public PageResponseDto<EmployeeDto> getEmployeesBySubDomainIdV3(
            Authentication authentication,
            @RequestParam(required = false) Long domainId,
            @RequestParam("subDomainId") Long subDomainId,
            @RequestParam String employeeStatus,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return userService.getEmployeesBySubDomainIdV3(actorUserId, domainId, subDomainId, employeeStatus, pageable);
    }






}
