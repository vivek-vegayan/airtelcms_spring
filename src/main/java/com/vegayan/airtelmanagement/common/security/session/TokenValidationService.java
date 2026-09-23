package com.vegayan.airtelmanagement.common.security.session;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TokenValidationService extends BaseService {

    public boolean isTokenValid(Long userId, String tokenId) {
        if (tokenId == null) {
            return false;
        }

        // token_id is a fresh UUID minted on every login (the previous row
        // for the user is deleted first in AuthService.saveTokenForUser), so
        // matching on it alone already accounts for logout (valid flips to
        // false) and re-login elsewhere (row replaced with a new token_id).
        //
        // expires_at is checked too so the DB's idea of a live session can't
        // outlive the JWT's own exp claim. It is belt-and-braces for a request
        // that got this far (an expired JWT already fails earlier, in
        // JwtAuthenticationFilter's extractClaims), but it is the same
        // predicate AuthService's "Already Logged" guard and the nightly purge
        // use, and all three must agree on what "live" means.
        String query = "SELECT COUNT(*) FROM AUTH_JWT_TOKENS "
                + "WHERE token_id = ? AND valid = true AND expires_at > NOW()";

        try {
            Integer count = jdbcTemplateOne.queryForObject(query, Integer.class, tokenId);
            return Optional.ofNullable(count).orElse(0) > 0;
        } catch (DataAccessException e) {
            LOGGER.error("Error validating token in DB for tokenId={}, userId={}", tokenId, userId, e);
            return false;
        }
    }
}
