package com.physiolink.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

/**
 * The terminal filter in the gateway chain: proxies each request to the correct
 * downstream service based on a configured path-prefix → base-URL mapping, then
 * streams the downstream response (status + headers + body) back to the caller.
 * <p>
 * The incoming request may already have been wrapped by {@link JwtAuthFilter}
 * to carry injected {@code X-User-Id} / {@code X-User-Role} headers — this filter
 * copies <em>all</em> request headers (including those overlaid ones) transparently.
 *
 * <p>Route resolution order: the first prefix in {@code gateway.routes} that matches
 * the request path wins. List more-specific prefixes before less-specific ones in
 * {@code application.yml}.
 */
@Component
public class ReverseProxyFilter extends OncePerRequestFilter {

    /**
     * Hop-by-hop headers that must not be forwarded between HTTP/1.1 connections.
     * See RFC 7230 §6.1.
     */
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding",
            "te", "trailer", "upgrade",
            "proxy-authorization", "proxy-authenticate"
    );

    private final RestTemplate restTemplate;
    private final GatewayRouteProperties routeProperties;

    public ReverseProxyFilter(RestTemplate restTemplate, GatewayRouteProperties routeProperties) {
        this.restTemplate    = restTemplate;
        this.routeProperties = routeProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path        = request.getServletPath();
        String queryString = request.getQueryString();

        // ── 1. Resolve downstream target ──────────────────────────────────────
        String targetBase = resolveTarget(path);
        if (targetBase == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "No gateway route configured for: " + path);
            return;
        }

        String targetUrl = targetBase + path
                + (queryString != null ? "?" + queryString : "");

        // ── 2. Copy request headers (including injected X-User-* headers) ─────
        HttpHeaders forwardHeaders = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if (isHopByHop(name)) continue;
                Collections.list(request.getHeaders(name))
                           .forEach(value -> forwardHeaders.add(name, value));
            }
        }

        // ── 3. Read request body ──────────────────────────────────────────────
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        HttpEntity<byte[]> entity = new HttpEntity<>(body.length > 0 ? body : null, forwardHeaders);

        // ── 4. Proxy to downstream service ────────────────────────────────────
        try {
            ResponseEntity<byte[]> downstream = restTemplate.exchange(
                    URI.create(targetUrl),
                    HttpMethod.valueOf(request.getMethod()),
                    entity,
                    byte[].class);

            writeResponse(response, downstream);

        } catch (HttpStatusCodeException ex) {
            // Downstream returned a 4xx/5xx — relay it faithfully
            response.setStatus(ex.getStatusCode().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            byte[] errorBody = ex.getResponseBodyAsByteArray();
            if (errorBody.length > 0) {
                response.getOutputStream().write(errorBody);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveTarget(String path) {
        return routeProperties.getRoutes().stream()
                .filter(r -> path.startsWith(r.getPrefix()))
                .map(GatewayRouteProperties.Route::getTarget)
                .findFirst()
                .orElse(null);
    }

    private void writeResponse(HttpServletResponse response,
                                ResponseEntity<byte[]> downstream) throws IOException {
        response.setStatus(downstream.getStatusCode().value());

        downstream.getHeaders().forEach((name, values) -> {
            if (!isHopByHop(name)) {
                values.forEach(v -> response.addHeader(name, v));
            }
        });

        byte[] body = downstream.getBody();
        if (body != null && body.length > 0) {
            response.getOutputStream().write(body);
        }
    }

    private boolean isHopByHop(String headerName) {
        return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase());
    }
}
