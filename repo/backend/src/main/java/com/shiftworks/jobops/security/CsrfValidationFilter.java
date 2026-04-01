package com.shiftworks.jobops.security;

import com.shiftworks.jobops.entity.UserSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class CsrfValidationFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> CSRF_EXCLUSIONS = Set.of("/api/auth/login", "/api/auth/register");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        UserSession session = (UserSession) request.getAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE);
        if (session == null || SecurityContextHolder.getContext().getAuthentication() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String headerToken = request.getHeader("X-XSRF-TOKEN");
        if (headerToken == null || !headerToken.equals(session.getCsrfToken())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":403,\"message\":\"CSRF validation failed\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return CSRF_EXCLUSIONS.contains(path);
    }
}
