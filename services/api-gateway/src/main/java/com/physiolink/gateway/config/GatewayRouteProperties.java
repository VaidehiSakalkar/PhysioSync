package com.physiolink.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the {@code gateway.routes} list from {@code application.yml}.
 * <p>
 * Each route entry maps a URL path prefix to a downstream base URL, for example:
 * <pre>
 * gateway:
 *   routes:
 *     - prefix: /api/patients
 *       target: http://patient-service:8081
 * </pre>
 * Routes are evaluated in declaration order — list more-specific prefixes first.
 */
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayRouteProperties {

    private List<Route> routes = new ArrayList<>();

    public List<Route> getRoutes()              { return routes; }
    public void setRoutes(List<Route> routes)   { this.routes = routes; }

    public static class Route {

        /** URL prefix to match against the incoming request path. */
        private String prefix;

        /** Base URL of the downstream service (scheme + host + port, no trailing slash). */
        private String target;

        public String getPrefix()            { return prefix; }
        public String getTarget()            { return target; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public void setTarget(String target) { this.target = target; }
    }
}
