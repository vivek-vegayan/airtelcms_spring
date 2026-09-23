package com.vegayan.airtelmanagement.common.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.security.config.PublicEndpoints;
import com.vegayan.airtelmanagement.common.security.session.TokenValidationService;
import com.vegayan.airtelmanagement.common.security.permission.UserPermissionService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.NonNull;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;

import java.io.IOException;
import java.util.List;


@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserPermissionService permissionService;
    private final TokenValidationService tokenValidationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                      UserPermissionService permissionService,
                                      TokenValidationService tokenValidationService) {
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
        this.tokenValidationService = tokenValidationService;
    }

    // Spring Security's authorizeHttpRequests permitAll only skips the
    // FilterSecurityInterceptor authorization check at the end of the chain -
    // it does NOT stop this filter (added earlier via addFilterBefore) from
    // running. Without this, a stale/expired/revoked token still present on the
    // client (leftover localStorage token or httpOnly jwt cookie) gets
    // validated on every request including login itself, so a bad old token
    // blocks a brand new signin attempt with "Invalid session" before the
    // signin controller ever runs.
    //
    // This shares one pattern list with SecurityConfig (see PublicEndpoints)
    // because the two hand-maintained copies had drifted: /api/python/runScript
    // and /CHMDataAdapter/CreateCrq were permitAll but still filtered here.
    //
    // /auth/v1/logout is intentionally absent from that list. It ends a session,
    // so it has to be able to prove which one - see AuthController.logout.
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return PublicEndpoints.matches(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

//        LOGGER.info("=== JWT FILTER HIT for URI: {} ===", request.getRequestURI());

        String token = extractToken(request);
//        LOGGER.info("Extracted Token = {}", token);

        if (token == null) {
//            LOGGER.warn("No JWT token found in request");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.extractClaims(token);
            String subject = claims.getSubject();

            // Tokens from /usermanagement/v1/getaccesstoken (generateSimpleAccessToken)
            // carry the raw username as the subject instead of a numeric userId,
            // and have no DB-backed session (no tokenId/jti, no AUTH_JWT_TOKENS row).
            // They're a static/test credential, not a real login - only allow them
            // through to endpoints that don't require @PreAuthorize authorities.
            if (subject == null || !subject.matches("\\d+")) {
                if (!jwtUtil.validateSimpleToken(token, subject)) {
                    sendUnauthorized(response, "Invalid token");
                    return;
                }

                Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
                boolean requiresAuthorities = handler instanceof HandlerMethod handlerMethod
                        && AnnotationUtils.findAnnotation(handlerMethod.getMethod(), PreAuthorize.class) != null;

                if (requiresAuthorities) {
                    sendUnauthorized(response, "Invalid token");
                    return;
                }

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(subject, null, List.of()));
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = Long.valueOf(subject);
            String tokenId = claims.getId();

//            LOGGER.info("JWT Subject (OLM) = {}", userId);
//            LOGGER.info("JWT TokenId (jti) = {}", tokenId);

            boolean jwtValid = jwtUtil.validateToken(token,  userId);
            boolean dbValid = tokenValidationService.isTokenValid(userId, tokenId);

//            LOGGER.info("JWT Signature & Expiry Valid = {}", jwtValid);
//            LOGGER.info("DB Token Validation Result = {}", dbValid);

            if (!jwtValid || !dbValid) {
//                LOGGER.error("JWT or DB validation failed");
                sendUnauthorized(response, "Invalid session");
                return;
            }

            // 🔹 Determine if endpoint has @PreAuthorize
            boolean requiresAuthorities = false;
            Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
            if (handler instanceof HandlerMethod handlerMethod) {
                PreAuthorize preAuth = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), PreAuthorize.class);
                requiresAuthorities = preAuth != null;
            }

            List<SimpleGrantedAuthority> authorities = List.of();

            if (requiresAuthorities) {
                authorities = permissionService.getPermissionsByUserIdV1(userId)
                        .stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
//                LOGGER.info("Authorities Loaded = {}", authorities);
            } else {
//                LOGGER.info("No authorities required for this endpoint");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
//            LOGGER.info("Authentication set for {}", userId);

        } catch (ExpiredJwtException e) {
//            LOGGER.error("JWT expired", e);
            sendUnauthorized(response, "Token expired");
            return;
        } catch (Exception e) {
//            LOGGER.error("JWT parsing/validation error", e);
            sendUnauthorized(response, "Invalid token");
            return;
        }

        filterChain.doFilter(request, response);
    }


    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(new ApiResponse("Fail", msg)));
    }

}
