package com.vegayan.airtelmanagement.common.security.config;

import org.springframework.util.AntPathMatcher;

/**
 * The single list of routes that are reachable without a login.
 *
 * <p>Two separate mechanisms need to agree on this list, and they used to be
 * maintained by hand in two files that had already drifted apart:
 *
 * <ul>
 *   <li>{@code SecurityConfig}'s {@code permitAll()} matchers, which control the
 *       authorization check at the end of the filter chain.
 *   <li>{@code JwtAuthenticationFilter.shouldNotFilter}, which controls whether
 *       the JWT is inspected at all. {@code permitAll()} does <em>not</em> skip
 *       that filter, so a route listed only in {@code SecurityConfig} still
 *       rejects a caller who happens to be carrying a stale token - which is
 *       how {@code /api/python/runScript} and {@code /CHMDataAdapter/CreateCrq}
 *       ended up answering 401 "Invalid session" despite being public.
 * </ul>
 *
 * <p>Patterns are Ant-style so the same strings drive both call sites.
 */
public final class PublicEndpoints {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public static final String[] PATTERNS = {
            "/test/**",
            "/auth/v1/signin",
            // Password-verified takeover of one's own stranded session. It has
            // to be reachable without a token (the caller can't get one while
            // the old session holds the slot) but it re-checks the account
            // password before ending anything - see AuthController.
            "/auth/v1/session/terminate",
            "/actuator/**",
            "/users/signup",
            "/api/python/runScript",
            "/CHMDataAdapter/CreateCrq",
            "/usermanagement/v1/getaccesstoken"
    };

    private PublicEndpoints() {
    }

    public static boolean matches(String uri) {
        for (String pattern : PATTERNS) {
            if (MATCHER.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }
}
