-- Session hardening for AUTH_JWT_TOKENS.
--
-- Background: a row in this table is the server side of a login session, and
-- TokenValidationService answers "is this JWT still a live session?" purely by
-- looking for token_id with valid = true. The table had no notion of expiry, so
-- a row only ever stopped being "valid" when the user explicitly logged out or
-- logged in again. Closing the browser tab left the row valid = true forever,
-- long after the 7-day JWT it belonged to had expired.
--
-- Two things fell out of that:
--   1. AuthService's "Already Logged" guard counts valid rows for the username,
--      so an abandoned session locked the user out of logging in permanently.
--   2. The only escape was the anonymous force-logout button, which invalidated
--      every session for that username - kicking whoever was genuinely working
--      under that account off with "Invalid session".
--
-- Giving the row its own expiry makes an abandoned session self-heal at exactly
-- the moment its JWT dies, which is what both of those code paths assumed all
-- along.

ALTER TABLE AUTH_JWT_TOKENS
    ADD COLUMN expires_at DATETIME NULL AFTER issued_at;

-- Backfill: every existing row was minted while jwt.token.expirationTime was
-- 604800000 ms (7 days), so that is the honest expiry for the JWTs already out
-- there. New rows compute this from the configured TTL in AuthService instead,
-- so the two can never drift apart again.
UPDATE AUTH_JWT_TOKENS
SET expires_at = issued_at + INTERVAL 7 DAY
WHERE expires_at IS NULL;

ALTER TABLE AUTH_JWT_TOKENS
    MODIFY COLUMN expires_at DATETIME NOT NULL;

-- TokenValidationService filters on (token_id, valid, expires_at) and the
-- nightly purge sweeps on (valid, expires_at); token_id already has uk_token_id.
ALTER TABLE AUTH_JWT_TOKENS
    ADD KEY idx_valid_expires (valid, expires_at);

-- Retire the sessions that are already zombies: still flagged valid, but their
-- JWT expired days or weeks ago. These are what have been producing spurious
-- "Already Logged" refusals.
UPDATE AUTH_JWT_TOKENS
SET valid = 0
WHERE valid = 1
  AND expires_at <= NOW();
