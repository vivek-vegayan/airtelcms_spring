package com.vegayan.airtelmanagement.common.security.session;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retires session rows whose JWT has expired.
 *
 * <p>{@code AUTH_JWT_TOKENS} rows were previously cleared only by an explicit
 * logout or by the same user's next successful login. Closing the browser
 * without logging out left the row flagged valid indefinitely, and AuthService's
 * "Already Logged" guard counts exactly those rows - so an abandoned session
 * locked its owner out of logging in until somebody force-terminated it.
 *
 * <p>Flipping {@code valid} rather than deleting keeps the row available for
 * audit; the second sweep deletes only rows that have been dead long enough to
 * have no forensic value, which is also what stops the table growing forever.
 */
@Component
public class SessionCleanupJob extends BaseService {

    private static final int RETAIN_DEAD_SESSIONS_DAYS = 30;

    // Hourly rather than nightly: the window between a session's JWT expiring
    // and its row being retired is a window in which the owner cannot log back
    // in, so it should be short.
    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 60 * 1000)
    public void retireExpiredSessions() {
        try {
            int retired = jdbcTemplateOne.update(
                    "UPDATE AUTH_JWT_TOKENS SET valid = false WHERE valid = true AND expires_at <= NOW()");

            int purged = jdbcTemplateOne.update(
                    "DELETE FROM AUTH_JWT_TOKENS WHERE valid = false AND expires_at < NOW() - INTERVAL ? DAY",
                    RETAIN_DEAD_SESSIONS_DAYS);

            if (retired > 0 || purged > 0) {
                LOGGER.info("Session cleanup: retired {} expired session(s), purged {} row(s) older than {} days.",
                        retired, purged, RETAIN_DEAD_SESSIONS_DAYS);
            }
        } catch (DataAccessException e) {
            // Never propagate: this runs on the scheduler thread, and a failed
            // sweep is harmless - the same predicate is enforced live by
            // TokenValidationService and the login guard, so an un-retired row
            // is untidy, not dangerous. The next run retries.
            LOGGER.error("Session cleanup sweep failed; will retry on the next run.", e);
        }
    }
}
