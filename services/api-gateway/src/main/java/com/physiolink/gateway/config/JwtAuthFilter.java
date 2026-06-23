package com.physiolink.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Reactive Gateway filter that:
 *  1. Extracts the Bearer token from the Authorization header
 *  2. Validates it using JwtUtil
 *  3. Injects X-User-Id and X-User-Role headers for downstream services
 *  4. Returns 401 if invalid / missing
 */
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorised(exchange);
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.validateAndGetClaims(token);
                String userId = claims.getSubject();
                String role   = claims.get("role", String.class);

                // Mutate request — inject user context headers for downstream
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.header("X-User-Id",   userId)
                                       .header("X-User-Role", role))
                        .build();
                return chain.filter(mutated);

            } catch (JwtException | IllegalArgumentException ex) {
                return unauthorised(exchange);
            }
        };
    }

    private Mono<Void> unauthorised(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // No configuration properties needed
    }
}
