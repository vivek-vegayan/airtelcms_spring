package com.vegayan.airtelmanagement.usermanagement.controller;


import com.vegayan.airtelmanagement.user.dto.UserCredentialsDto;
import com.vegayan.airtelmanagement.usermanagement.dto.LoginTokenResponseDto;
import com.vegayan.airtelmanagement.usermanagement.dto.UserLogDetailsDto;
import com.vegayan.airtelmanagement.usermanagement.service.UserLogDetailsService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.List;

@RestController
@RequestMapping("/usermanagement")
@AllArgsConstructor
public class UserLogDetailsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserLogDetailsController.class);

    private final UserLogDetailsService userLogDetailsService;

    @GetMapping("/logindetails")
    public List<UserLogDetailsDto> getUsersLoginDetails(
            @RequestParam Date startDate,
            @RequestParam Date endDate,
            @RequestParam("subDomainId") Long subDomainId,
            @PageableDefault(size = 10) Pageable pageable) {
        return userLogDetailsService.getUsersLoginDetails(startDate,endDate,subDomainId,pageable);
    }

    @PostMapping("/v1/getaccesstoken")
    public ResponseEntity<LoginTokenResponseDto> signIn(
            @RequestBody UserCredentialsDto userCredentialsDto) {

        // Never log the password — anyone with access to the logs could read it.
        LOGGER.info("Access token requested for username: {}", userCredentialsDto.getUsername());

        LoginTokenResponseDto response =
                userLogDetailsService.getAccessToken(
                        userCredentialsDto.getUsername(),
                        userCredentialsDto.getPassword()
                );

        if ("Success".equalsIgnoreCase(response.getStatus())) {
            LOGGER.info("Access token issued for username: {}", userCredentialsDto.getUsername());
            return ResponseEntity.ok(response);
        }

        LOGGER.warn("Access token request rejected for username: {}", userCredentialsDto.getUsername());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

}
