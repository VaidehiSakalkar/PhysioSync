package com.physiolink.patient.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT issuance utility for the patient-service (the only service that signs tokens).
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expiryMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiry-ms:86400000}") long expiryMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs = expiryMs;
    }

    /**
     * Generate a signed JWT for the given user.
     *
     * @param userId user UUID (becomes the sub claim)
     * @param role   "PATIENT" or "PHYSIO"
     */
    public String generateToken(UUID userId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(signingKey)
                .compact();
    }
}
