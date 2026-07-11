package com.physiolink.gateway.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Central gateway configuration bean:
 * <ul>
 *   <li>Provides a {@link RestTemplate} for downstream HTTP proxying.</li>
 *   <li>Configures a {@link CorsFilter} to handle cross-origin requests
 *       (equivalent to {@code spring.cloud.gateway.globalcors} in the old config).</li>
 *   <li>Sets explicit ordering for the three gateway filters so they execute in
 *       the correct sequence regardless of Spring Boot's auto-registration order.</li>
 * </ul>
 *
 * <h3>Filter execution order</h3>
 * <pre>
 *   CorsFilter (order -1)     — handle OPTIONS preflight first
 *   RateLimitFilter (order 1) — reject over-limit traffic early
 *   JwtAuthFilter   (order 2) — validate JWT and inject X-User-* headers
 *   ReverseProxyFilter (order 3) — proxy to downstream and write response
 * </pre>
 */
@Configuration
public class GatewayConfig {

    // ── RestTemplate ──────────────────────────────────────────────────────────

    /**
     * Shared {@link RestTemplate} used by {@link ReverseProxyFilter} to call
     * downstream services. Connection and read timeouts are set conservatively;
     * adjust for your environment.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 s to establish TCP connection
        factory.setReadTimeout(30_000);     // 30 s to receive full response
        return new RestTemplate(factory);
    }

    // ── CORS ──────────────────────────────────────────────────────────────────

    /**
     * Global CORS policy — mirrors the old {@code spring.cloud.gateway.globalcors}
     * config. Allows all origins and the standard REST methods.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); //blocks the passing of cookies or other credentials through headers
        config.setMaxAge(3600L);//to generate a preflight response

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);//cors is applied to every endpoint
        
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(-1);
        return bean;
    }

    // ── Filter ordering ───────────────────────────────────────────────────────
    // Spring Boot auto-registers @Component filters at Integer.MAX_VALUE (last).
    // FilterRegistrationBean lets us control order explicitly.

    //setOrder is to set the order of the filter
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitRegistration(RateLimitFilter filter) {
        var bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthRegistration(JwtAuthFilter filter) {
        var bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(2);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<ReverseProxyFilter> reverseProxyRegistration(ReverseProxyFilter filter) {
        var bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(3);
        return bean;
    }
}
