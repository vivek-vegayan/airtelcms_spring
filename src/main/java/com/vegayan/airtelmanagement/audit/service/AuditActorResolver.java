package com.vegayan.airtelmanagement.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated user id to record as {@code Actor_User_ID}.
 *
 * <p>The value is taken from the SecurityContext that
 * {@code JwtAuthenticationFilter} populated after it verified the JWT's
 * signature, its expiry AND the matching live row in AUTH_JWT_TOKENS. It is
 * therefore the id the bearer proved they own.
 *
 * <p>Nothing here ever looks at a request parameter, header or body field. A
 * client that sends {@code actorUserId=1} alongside its own token changes
 * nothing: that value is not read, anywhere, on any path into the audit trail.
 *
 * <p>Two shapes of principal reach this class. A real login puts a
 * {@code Long} user id there; the static access-token route
 * ({@code /usermanagement/v1/getaccesstoken}) puts a username String, which
 * has no user id behind it at all and resolves to null - the resulting row is
 * still written, with a NULL actor, rather than being dropped.
 */
@Component
public class AuditActorResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditActorResolver.class);

    /** @return the authenticated user id, or null when there is none. */
    public Long currentActorUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof Long userId) {
                return userId;
            }

            // Fall back to the name, which for a real login is the same id in
            // String form. A non-numeric name is the static-token case above.
            String name = authentication.getName();
            if (name != null && name.matches("\\d+")) {
                return Long.valueOf(name);
            }

            return null;

        } catch (Exception ex) {
            // Resolving the actor must never be able to fail a request.
            LOGGER.debug("Could not resolve the audit actor from the SecurityContext", ex);
            return null;
        }
    }
}
