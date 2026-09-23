package com.vegayan.airtelmanagement.auth.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.service.AuditLogService;
import com.vegayan.airtelmanagement.auth.dto.LoginResponseDto;
import com.vegayan.airtelmanagement.auth.service.AuthService;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.security.jwt.JwtUtil;
import com.vegayan.airtelmanagement.common.service.BaseService;
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

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public AuthController(
            AuthService authService,
            JwtUtil jwtUtil,
            UserService userService,
            AuditLogService auditLogService
    ) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/v1/signin")
    public ResponseEntity<?> signIn(@RequestBody UserCredentialsDto userCredentialsDto, HttpServletResponse response) {
        System.out.println("Received olmId: " + userCredentialsDto.getOlmId());
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

                String cookieHeader = String.format(
                        "jwt=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax",
                        jwtToken, 60 * 60 // 1 hour
                );
                response.setHeader("Set-Cookie", cookieHeader);

                LoginResponseDto responseBody = new LoginResponseDto(
                        "Success",
                        "Login Successfully",
                        jwtToken,
                        loginResponse.getUserId()
                );

                // Audited explicitly rather than with @Auditable: at this point
                // the SecurityContext is still empty (the filter had no token to
                // authenticate), so the aspect could not resolve an actor. The id
                // used here is the one AuthService just proved the credentials
                // belong to - not anything taken from the request body.
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

    // Ends the caller's own session and nothing else. This route is no longer
    // permitAll: JwtAuthenticationFilter runs on it, so by the time we get here
    // the bearer token has been proved genuine and live, and the token_id we
    // read out of it is provably the caller's own session.
    //
    // The olmId in the request body is now ignored entirely. It used to be the
    // invalidation key on an unauthenticated route, which meant a bare
    // {"olmId": "..."} POST from anyone on the network ended that person's
    // session - and they saw it as "Invalid session" in the middle of their
    // work. Frontend callers still send it; accepting and discarding it keeps
    // older clients working.
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
                // Same token, same claim set: the subject is the user id this
                // session was issued to. Read here, before the session is torn
                // down, so the LOGOUT row can name the right person.
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

    // Backs the "log out my other device" button the login screen offers after
    // a 403 "Already Logged". Unauthenticated by necessity - the whole point is
    // that the caller can't get a token while the old session holds the slot -
    // but it verifies the account's password before ending anything, so it
    // proves ownership just as strongly as signin does.
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

    private void clearJwtCookie(HttpServletResponse response) {
        Cookie jwtCookie = new Cookie("jwt", "");
        jwtCookie.setPath("/");
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(false);
        jwtCookie.setMaxAge(0);
        response.addCookie(jwtCookie);
    }

    // Same order of preference JwtAuthenticationFilter uses: the Authorization
    // header first, then the jwt cookie. The old version read the cookie only,
    // which quietly stopped finding a token an hour into every session - the
    // cookie's Max-Age is 1 hour while the JWT itself lives for days - and that
    // is precisely when logout fell back to invalidating by olmId instead.
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("jwt".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }



}
