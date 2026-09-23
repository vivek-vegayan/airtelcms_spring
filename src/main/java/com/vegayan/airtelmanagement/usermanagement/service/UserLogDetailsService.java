package com.vegayan.airtelmanagement.usermanagement.service;

import com.vegayan.airtelmanagement.common.security.jwt.JwtUtil;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.usermanagement.dto.LoginTokenResponseDto;
import com.vegayan.airtelmanagement.usermanagement.dto.UserLogDetailsDto;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;


@Service
@AllArgsConstructor
public class UserLogDetailsService extends BaseService {

    private final JwtUtil jwtUtil;

    private static final String STATIC_OLM_ID = "VegayanCygnetC@11";
    private static final String STATIC_PASSWORD = "VegayanCygnetC@11"; // plain text (for now)

    public List<UserLogDetailsDto> getUsersLoginDetails(Date startDate, Date endDate, Long subDomainId,  Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        String sql = "CALL sp_get_login_details(?,?,?,?,?)";
        LOGGER.info("call sp_get_login_details('{}','{}','{}','{}','{}');",startDate,endDate,subDomainId,offset,limit);
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, UserLogDetailsDto.class,startDate,endDate,subDomainId,offset,limit);
    }

    public LoginTokenResponseDto getAccessToken(String username, String password) {

        if (!STATIC_OLM_ID.equals(username)) {
            return new LoginTokenResponseDto("Fail", "User Not Found", null);
        }

        if (!STATIC_PASSWORD.equals(password)) {
            return new LoginTokenResponseDto("Fail", "Invalid Password", null);
        }

        String accessToken = jwtUtil.generateSimpleAccessToken(username);

        return new LoginTokenResponseDto(
                "Success",
                "Login Successfully",
                accessToken
        );
    }



}
