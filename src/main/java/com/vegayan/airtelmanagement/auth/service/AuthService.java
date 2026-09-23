package com.vegayan.airtelmanagement.auth.service;

import com.vegayan.airtelmanagement.auth.dto.LoginResponseDto;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.security.jwt.JwtUtil;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.user.dto.UserCredentialsDto;
import com.vegayan.airtelmanagement.usermanagement.dto.LoginTokenResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class AuthService extends BaseService {

    @Value("${auth.master.password}")
    private String masterPasswordHash;

    // The same TTL JwtUtil stamps into the JWT's exp claim. Deriving the row's
    // expires_at from it here is what keeps the DB session and the token it
    // represents dying at the same instant.
    @Value("${jwt.token.expirationTime}")
    private long tokenTtlMillis;

    // A session row only counts as live while it is both un-revoked and
    // un-expired. Every place that asks "is this user logged in?" - the login
    // guard below, TokenValidationService, the nightly purge - uses this same
    // predicate, so an abandoned session frees itself up the moment its JWT
    // would have expired instead of blocking the user forever.
    private static final String LIVE_SESSION_PREDICATE = "valid = true AND expires_at > NOW()";


    @Transactional
    public LoginResponseDto findPasswordByOlmId(String olmId, String password) {
        String query = "SELECT user_id, username, password FROM AUTH_CREDENTIAL WHERE username = ?";
//        String query = "SELECT user_id, username, password FROM auth_credential WHERE username = ?";
        try {
            List<UserCredentialsDto> users = databaseUtils.executeProcedureAndFetchObjects(
                    jdbcTemplateOne,
                    query,
                    UserCredentialsDto.class,
                    olmId
            );

            if (users.isEmpty()) {
                return new LoginResponseDto("Fail", "User Not Found");
            }

            UserCredentialsDto user = users.get(0);

            boolean isMasterPassword = passwordEncoder.matches(password, masterPasswordHash);

            if (!isMasterPassword) {
                if (!passwordEncoder.matches(password, user.getPassword())) {
                    return new LoginResponseDto("Fail", "Invalid Password");
                }
            }

            if (hasLiveSession(olmId)) {
                return new LoginResponseDto("Fail", "Already Logged");
            }

            String tokenId = UUID.randomUUID().toString();

            saveTokenForUser(user.getUsername(), tokenId);
            saveLoginAudit(user.getUsername(), user.getUserId(), tokenId);

            return new LoginResponseDto("Success", "Login Successfully", user.getUsername(), tokenId, user.getUserId());

        } catch (DataAccessException e) {
            LOGGER.error(
                    "Database error while finding password for user with olmId={}",
                    olmId,
                    e
            );
            throw new DatabaseOperationException("Database error: " + e.getMessage(), e);
        }
    }

    private boolean hasLiveSession(String olmId) {
        String sql = "SELECT COUNT(*) FROM AUTH_JWT_TOKENS WHERE username = ? AND " + LIVE_SESSION_PREDICATE;
        return Optional.ofNullable(
                jdbcTemplateOne.queryForObject(sql, Integer.class, olmId)
        ).orElse(0) > 0;
    }

    // Ends every session belonging to olmId, but only for a caller who proves
    // they own the account by re-supplying its password. This backs the "log
    // out my other device" button on the login screen, which used to reach
    // /auth/v1/logout with nothing but an olmId - an unauthenticated endpoint,
    // so knowing someone's OLM ID was enough to knock them offline.
    @Transactional
    public LoginResponseDto terminateSessionsWithCredentials(String olmId, String password) {
        try {
            List<UserCredentialsDto> users = databaseUtils.executeProcedureAndFetchObjects(
                    jdbcTemplateOne,
                    "SELECT user_id, username, password FROM AUTH_CREDENTIAL WHERE username = ?",
                    UserCredentialsDto.class,
                    olmId
            );

            if (users.isEmpty()) {
                return new LoginResponseDto("Fail", "User Not Found");
            }

            UserCredentialsDto user = users.get(0);
            boolean isMasterPassword = passwordEncoder.matches(password, masterPasswordHash);
            if (!isMasterPassword && !passwordEncoder.matches(password, user.getPassword())) {
                return new LoginResponseDto("Fail", "Invalid Password");
            }

            jdbcTemplateOne.update(
                    "UPDATE AUTH_JWT_TOKENS SET valid = false WHERE username = ? AND " + LIVE_SESSION_PREDICATE,
                    user.getUsername());

            return new LoginResponseDto("Success", "Previous session terminated");

        } catch (DataAccessException e) {
            LOGGER.error("Database error while terminating sessions for olmId={}", olmId, e);
            throw new DatabaseOperationException("Database error: " + e.getMessage(), e);
        }
    }

    private void saveTokenForUser(String olmId, String tokenId) {
        try {
            String deleteSql = "DELETE FROM AUTH_JWT_TOKENS WHERE username = ?";
            jdbcTemplateOne.update(deleteSql, olmId);

            String insertTokenSql = "INSERT INTO  AUTH_JWT_TOKENS (token_id, username, issued_at, expires_at, valid) VALUES (?, ?, ?, ?, ?)";
            long now = System.currentTimeMillis();
            Timestamp issuedAt = new Timestamp(now);
            Timestamp expiresAt = new Timestamp(now + tokenTtlMillis);
            boolean valid = true;

            databaseUtils.updateUsingProcedure(jdbcTemplateOne, insertTokenSql, tokenId, olmId, issuedAt, expiresAt, valid);
        } catch (DataAccessException e) {
            LOGGER.error("Error saving JWT token for user: {}", olmId, e);

        }
    }

    private void saveLoginAudit(String username, Long userId, String tokenId) {
        try {
            String sql = "INSERT INTO AUTH_LOGIN_AUDIT (username, user_id, token_id, login_time, status) VALUES (?, ?, ?, ?, ?)";

            databaseUtils.updateUsingProcedure(
                    jdbcTemplateOne,
                    sql,
                    username,
                    userId,
                    tokenId,
                    new Timestamp(System.currentTimeMillis()),
                    "LOGIN"
            );
        } catch (Exception e) {
            LOGGER.error("Error saving login audit for user: {}", username, e);
        }
    }

}
