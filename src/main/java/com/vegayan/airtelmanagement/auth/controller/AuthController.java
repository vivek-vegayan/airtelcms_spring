package com.vegayan.airtelmanagement.auth.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.service.AuditLogService;
import com.vegayan.airtelmanagement.auth.dto.LoginResponseDto;
import com.vegayan.airtelmanagement.auth.service.AuthService;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.security.jwt.JwtUtil;
import com.vegayan.airtelmanagement.common.security.session.TokenValidationService;
import com.vegayan.airtelmanagement.common.service.BaseService;
import io.jsonwebtoken.Claims;
import com.vegayan.airtelmanagement.user.dto.UserCredentialsDto;
import com.vegayan.airtelmanagement.user.service.UserService;
import com.vegayan.airtelmanagement.usermanagement.dto.LoginTokenResponseDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class    AuthController extends BaseService {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final TokenValidationService tokenValidationService;

    public AuthController(
            AuthService authService,
            JwtUtil jwtUtil,
            UserService userService,
            AuditLogService auditLogService,
            TokenValidationService tokenValidationService
    ) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.tokenValidationService = tokenValidationService;
    }

    @PostMapping("/v1/signin")
    public ResponseEntity<?> signIn(@RequestBody UserCredentialsDto userCredentialsDto, HttpServletResponse response) {
        LOGGER.info("Received olmId: {}", userCredentialsDto.getOlmId());
        try {
            LoginResponseDto loginResponse = authService.findPasswordByOlmId(
                    userCredentialsDto.getOlmId(),
                    userCredentialsDto.getPassword()
            );

            if ("Success".equalsIgnoreCase(loginResponse.getStatus())) {
                String jwtToken = jwtUtil.generateToken(
                        loginResponse.getUserId(),
                        loginResponse.getTokenId()
                );
                String refreshToken = jwtUtil.generateRefreshToken(
                        loginResponse.getUserId(),
                        loginResponse.getTokenId()
                );

                setCookie(response, "jwt", jwtToken, jwtUtil.getAccessExpirationSeconds());
                setCookie(response, REFRESH_COOKIE, refreshToken, jwtUtil.getRefreshExpirationSeconds());

                LoginResponseDto responseBody = new LoginResponseDto(
                        "Success",
                        "Login Successfully",
                        jwtToken,
                        loginResponse.getUserId()
                );

                auditLogService.logActionAs(
                        loginResponse.getUserId(),
                        AuditModule.AUTHENTICATION,
                        AuditModule.SUB_SESSION,
                        AuditAction.LOGIN,
                        null,
                        "Signed in");

                return ResponseEntity.ok().body(responseBody);
            }

            return switch (loginResponse.getMessage()) {
                case "Already Logged" -> ResponseEntity.status(403)
                        .body(new LoginResponseDto("Fail", "Already Logged"));
                case "Invalid Password" -> ResponseEntity.status(401)
                        .body(new LoginResponseDto("Fail", "Invalid Password"));
                case "User Not Found" -> ResponseEntity.status(404)
                        .body(new LoginResponseDto("Fail", "User Not Found"));
                default -> ResponseEntity.status(500)
                        .body(new LoginResponseDto("Fail", "Unknown Error"));
            };

        } catch (DatabaseOperationException e) {
            return ResponseEntity.status(500)
                    .body(new LoginResponseDto("Fail", "Database error"));
        }
    }


    // Issues a fresh access token from the httpOnly refresh_token cookie. The
    // frontend calls it when an access token expires and, throttled, on user
    // activity — isTokenValid also slides the session's idle deadline, so this
    // is the activity heartbeat too. 401 means the session is over.
    @PostMapping("/v1/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, REFRESH_COOKIE);
        try {
            if (refreshToken != null) {
                Claims claims = jwtUtil.extractClaims(refreshToken);
                String subject = claims.getSubject();
                if (jwtUtil.isRefreshToken(claims) && subject != null && subject.matches("\\d+")) {
                    Long userId = Long.valueOf(subject);
                    if (tokenValidationService.isTokenValid(userId, claims.getId())) {
                        String accessToken = jwtUtil.generateToken(userId, claims.getId());
                        setCookie(response, "jwt", accessToken, jwtUtil.getAccessExpirationSeconds());
                        return ResponseEntity.ok(
                                new LoginResponseDto("Success", "Token refreshed", accessToken, userId));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Refresh token rejected", e);
        }
        clearJwtCookie(response);
        return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                .body(new ApiResponse("Fail", "Session expired"));
    }

    @PostMapping("/v1/logout")
    public ResponseEntity<?> logout(
            @RequestBody(required = false) Map<String, String> requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            String token = extractToken(request);
            String tokenId = null;
            Long actorUserId = null;
            if (token != null && !token.isBlank()) {
                try {
                    tokenId = jwtUtil.extractTokenId(token);
                } catch (Exception ex) {
                    LOGGER.error("Failed to extract tokenId from the request's JWT", ex);
                }

                try {
                    String subject = jwtUtil.extractClaims(token).getSubject();
                    if (subject != null && subject.matches("\\d+")) {
                        actorUserId = Long.valueOf(subject);
                    }
                } catch (Exception ex) {
                    LOGGER.debug("Could not read the user id from the request's JWT for the audit trail", ex);
                }
            }

            if (tokenId == null || tokenId.isBlank()) {
                return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                        .body(new ApiResponse("Fail", "Unauthorized: no session to log out."));
            }

            userService.logoutSession(tokenId);
            userService.updateLogoutAudit(tokenId);

            // After the session has actually been invalidated, so a LOGOUT row
            // only ever exists for a logout that happened.
            auditLogService.logActionAs(
                    actorUserId,
                    AuditModule.AUTHENTICATION,
                    AuditModule.SUB_SESSION,
                    AuditAction.LOGOUT,
                    null,
                    "Signed out");

            clearJwtCookie(response);

            return ResponseEntity.ok(new ApiResponse("Success", "User logged out successfully."));
        } catch (Exception e) {
            LOGGER.error(" Exception during logout", e);
            return ResponseEntity.status(500).body(new ApiResponse("Fail", "Error logging out user."));
        }
    }

    @PostMapping("/v1/session/terminate")
    public ResponseEntity<?> terminateOwnSessions(
            @RequestBody UserCredentialsDto credentials,
            HttpServletResponse response) {
        try {
            String olmId = credentials.getOlmId();
            String password = credentials.getPassword();

            if (olmId == null || olmId.isBlank() || password == null || password.isBlank()) {
                return ResponseEntity.status(400)
                        .body(new ApiResponse("Fail", "OLM ID and password are required."));
            }

            LoginResponseDto result = authService.terminateSessionsWithCredentials(olmId.trim(), password);

            if (!"Success".equalsIgnoreCase(result.getStatus())) {
                // Deliberately the same 401 and wording for a wrong password and
                // an unknown account, so this endpoint can't be used to probe
                // which OLM IDs exist.
                return ResponseEntity.status(401)
                        .body(new ApiResponse("Fail", "Invalid credentials."));
            }

            clearJwtCookie(response);
            return ResponseEntity.ok(new ApiResponse("Success", "Previous session terminated."));
        } catch (DatabaseOperationException e) {
            LOGGER.error("Database error while terminating sessions", e);
            return ResponseEntity.status(500).body(new ApiResponse("Fail", "Database error"));
        }
    }

    // Clears both the access and the refresh cookie.
    private void clearJwtCookie(HttpServletResponse response) {
        setCookie(response, "jwt", "", 0);
        setCookie(response, REFRESH_COOKIE, "", 0);
    }

    private void setCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        response.addHeader("Set-Cookie", String.format(
                "%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax", name, value, maxAgeSeconds));
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return readCookie(request, "jwt");
    }



}
