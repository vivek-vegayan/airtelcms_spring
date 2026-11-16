package com.vegayan.airtelmanagement.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.token.expirationTime}")
    private long expirationTime;

    @Value("${jwt.access.expirationTime:900000}")
    private long accessExpirationTime;

    @Value("${jwt.simpleToken.expirationTime:604800000}")
    private long simpleTokenExpirationTime;

    @Value("${jwt.secret}")
    private String secret;

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        log.debug("JWT secret length (decoded): {}", keyBytes.length);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Short-lived access token sent on every API call.
    public String generateToken(Long userId, String tokenId) {

        log.debug("Generating access JWT -> userId={}, tokenId={}", userId, tokenId);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setId(tokenId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpirationTime))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Long-lived refresh token (absolute session lifetime). Lives only in the
    // httpOnly refresh_token cookie and is accepted only by /auth/v1/refresh.
    public String generateRefreshToken(Long userId, String tokenId) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setId(tokenId)
                .claim("type", "refresh")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("type", String.class));
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationTime / 1000;
    }

    public long getRefreshExpirationSeconds() {
        return expirationTime / 1000;
    }

    public Claims extractClaims(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims;
    }

    public String extractTokenId(String token) {
        return extractClaims(token).getId();
    }

    public boolean validateToken(String token, Long userId) {
        Claims claims = extractClaims(token);
        boolean subjectMatch = String.valueOf(userId).equals(claims.getSubject());
        boolean notExpired   = claims.getExpiration().after(new Date());
        return subjectMatch && notExpired;
    }

    public String generateSimpleAccessToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("username", username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + simpleTokenExpirationTime))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateSimpleToken(String token, String username) {
        Claims claims = extractClaims(token);
        boolean subjectMatch = username.equals(claims.getSubject());
        boolean notExpired = claims.getExpiration().after(new Date());
        return subjectMatch && notExpired;
    }
}
