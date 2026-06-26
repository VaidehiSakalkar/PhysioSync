package com.physiolink.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that validates the JWT Bearer token on every protected request.
 * <p>
 * Public paths ({@code /api/auth/**}) are skipped via {@link #shouldNotFilter}.
 * On a valid token the filter wraps the request using {@link MutableHttpServletRequest}
 * to inject two headers that downstream services rely on:
 * <ul>
 *   <li>{@code X-User-Id}   — the user's UUID (JWT {@code sub} claim)</li>
 *   <li>{@code X-User-Role} — {@code PATIENT} or {@code PHYSIO}  (JWT {@code role} claim)</li>
 * </ul>
 * On a missing or invalid token it writes {@code 401 Unauthorized} and stops the chain.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PUBLIC_PATH_PREFIX = "/api/auth";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // ── Skip filter for public auth endpoints ─────────────────────────────────

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith(PUBLIC_PATH_PREFIX);
    }

    // ── Main filter logic ─────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            unauthorised(response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.validateAndGetClaims(token);
            String userId = claims.getSubject();
            String role   = claims.get("role", String.class);

            // Wrap the request to inject X-User-Id and X-User-Role for downstream services.
            // HttpServletRequest headers are immutable, so we use a wrapper that overlays them.
            MutableHttpServletRequest mutated = new MutableHttpServletRequest(request);
            mutated.addHeader("X-User-Id",   userId);
            mutated.addHeader("X-User-Role", role);

            filterChain.doFilter(mutated, response);

        } catch (JwtException | IllegalArgumentException ex) {
            unauthorised(response);
        }
    }

    private void unauthorised(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }
}
