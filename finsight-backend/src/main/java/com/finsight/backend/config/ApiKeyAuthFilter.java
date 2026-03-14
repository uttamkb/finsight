package com.finsight.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter that enforces API key authentication on all endpoints.
 * Clients must include the header: X-API-Key: <secret>
 *
 * Exempt endpoints (no key required):
 *  - GET /api/health  (uptime checks)
 *  - OPTIONS requests (CORS pre-flight)
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${app.api.secret-key}")
    private String secretKey;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String path   = request.getRequestURI();

        // Allow CORS pre-flight, health endpoint, and Swagger/OpenAPI docs
        if ("OPTIONS".equalsIgnoreCase(method) || 
            path.startsWith("/api/v1/health") || 
            path.startsWith("/v3/api-docs") || 
            path.startsWith("/swagger-ui")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || !providedKey.equals(secretKey)) {
            log.warn("Unauthorized access attempt to {} {} from {}", method, path, request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Missing or invalid X-API-Key header.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
