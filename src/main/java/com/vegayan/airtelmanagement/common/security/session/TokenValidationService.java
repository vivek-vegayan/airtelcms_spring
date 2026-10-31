package com.vegayan.airtelmanagement.common.security.session;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TokenValidationService extends BaseService {

    @Value("${jwt.token.expirationTime}")
    private long tokenTtlMillis;

    @Value("${session.idle.timeout.minutes:30}")
    private long idleTimeoutMinutes;

    public boolean isTokenValid(Long userId, String tokenId) {
        if (tokenId == null) {
            return false;
        }

        // token_id is a fresh UUID minted on every login (the previous row
        // for the user is deleted first in AuthService.saveTokenForUser), so
        // matching on it alone already accounts for logout (valid flips to
        // false) and re-login elsewhere (row replaced with a new token_id).
        //
        // expires_at is the session's idle deadline (see touchSession). It is
        // the same predicate AuthService's "Already Logged" guard and the
        // hourly purge use, and all three must agree on what "live" means.
        String query = "SELECT COUNT(*) FROM AUTH_JWT_TOKENS "
                + "WHERE token_id = ? AND valid = true AND expires_at > NOW()";

        try {
            Integer count = jdbcTemplateOne.queryForObject(query, Integer.class, tokenId);
            boolean valid = Optional.ofNullable(count).orElse(0) > 0;
            if (valid) {
                touchSession(tokenId);
            }
            return valid;
        } catch (DataAccessException e) {
            LOGGER.error("Error validating token in DB for tokenId={}, userId={}", tokenId, userId, e);
            return false;
        }
    }

    // Slides the idle deadline to one idle window from now, capped at the JWT's
    // own exp so the row can't outlive the token. The last WHERE clause skips
    // the write unless the deadline is more than a minute stale, so a burst of
    // requests costs one UPDATE per minute rather than one per request.
    private void touchSession(String tokenId) {
        try {
            jdbcTemplateOne.update(
                    "UPDATE AUTH_JWT_TOKENS "
                            + "SET expires_at = LEAST(NOW() + INTERVAL ? MINUTE, issued_at + INTERVAL ? SECOND) "
                            + "WHERE token_id = ? AND valid = true "
                            + "AND expires_at < NOW() + INTERVAL ? MINUTE",
                    idleTimeoutMinutes, tokenTtlMillis / 1000, tokenId, idleTimeoutMinutes - 1);
        } catch (DataAccessException e) {
            // A missed slide only shortens this session by at most a minute;
            // never fail the request over it.
            LOGGER.warn("Could not extend idle deadline for tokenId={}", tokenId, e);
        }
    }
}
