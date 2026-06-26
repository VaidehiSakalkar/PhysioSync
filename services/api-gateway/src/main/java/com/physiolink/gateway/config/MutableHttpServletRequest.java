package com.physiolink.gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link HttpServletRequestWrapper} that allows callers to inject additional
 * headers into the request without modifying the original immutable request object.
 * <p>
 * Used by {@link JwtAuthFilter} to overlay {@code X-User-Id} and {@code X-User-Role}
 * after a successful JWT validation, so that downstream services receive them as
 * ordinary HTTP headers.
 */
public class MutableHttpServletRequest extends HttpServletRequestWrapper {

    /** Extra headers to overlay on top of the original request headers. */
    private final Map<String, String> extraHeaders = new HashMap<>();

    public MutableHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    /**
     * Add (or overwrite) a header value. Names are stored lower-case so lookups
     * are case-insensitive, matching the HTTP/1.1 spec.
     */
    public void addHeader(String name, String value) {
        extraHeaders.put(name.toLowerCase(), value);
    }

    // ── Override header accessors to include injected headers ─────────────────

    @Override
    public String getHeader(String name) {
        String extra = extraHeaders.get(name.toLowerCase());
        return extra != null ? extra : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String extra = extraHeaders.get(name.toLowerCase());
        if (extra != null) {
            return Collections.enumeration(List.of(extra));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
        // Add injected names that weren't already present
        extraHeaders.keySet().forEach(h -> {
            if (!names.contains(h)) names.add(h);
        });
        return Collections.enumeration(names);
    }
}
