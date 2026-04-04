package com.shiftworks.jobops.security;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.service.SessionService;
import com.shiftworks.jobops.util.CookieUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_REQUEST_ATTRIBUTE = "SHIFTWORKS_ACTIVE_SESSION";

    private final SessionService sessionService;
    private final AppProperties appProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            UserSession session = (UserSession) request.getAttribute(SESSION_REQUEST_ATTRIBUTE);
            if (session == null) {
                session = CookieUtils.getCookie(request, SessionService.SESSION_COOKIE)
                    .flatMap(sessionService::findValidSession)
                    .orElse(null);
            }
            if (session != null) {
                if (isExpired(session)) {
                    sessionService.invalidateSession(session);
                    expireCookies(response);
                } else {
                    sessionService.touchSession(session);
                    request.setAttribute(SESSION_REQUEST_ATTRIBUTE, session);
                    establishSecurityContext(request, session);
                    if (session.getUser().getPasswordChangedAt() != null) {
                        long daysSinceChange = java.time.Duration.between(session.getUser().getPasswordChangedAt(), java.time.Instant.now()).toDays();
                        if (daysSinceChange >= appProperties.getSecurity().getPassword().getRotationDays()) {
                            String requestPath = request.getRequestURI();
                            if (!requestPath.equals("/api/auth/change-password") && !requestPath.equals("/api/auth/me") && !requestPath.equals("/api/auth/logout")) {
                                response.setStatus(403);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"code\":403,\"message\":\"Password expired. Please change your password.\",\"passwordExpired\":true}");
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Session authentication error — denying request", ex);
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":401,\"message\":\"Authentication service unavailable\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isExpired(UserSession session) {
        if (!session.isValid() || session.getAbsoluteExpiry().isBefore(Instant.now())) {
            return true;
        }
        Duration idleLimit = Duration.ofMinutes(appProperties.getSession().getIdleTimeoutMinutes());
        return session.getLastActiveAt().plus(idleLimit).isBefore(Instant.now());
    }

    private void establishSecurityContext(HttpServletRequest request, UserSession session) {
        UserRole role = session.getUser().getRole();
        AuthenticatedUser principal = new AuthenticatedUser(session.getUser().getId(), session.getUser().getUsername(), role);
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    private void expireCookies(HttpServletResponse response) {
        ResponseCookie sessionCookie = ResponseCookie.from(SessionService.SESSION_COOKIE, "")
            .path("/")
            .maxAge(0)
            .httpOnly(true)
            .sameSite("Strict")
            .build();
        ResponseCookie csrfCookie = ResponseCookie.from(SessionService.CSRF_COOKIE, "")
            .path("/")
            .maxAge(0)
            .httpOnly(false)
            .sameSite("Strict")
            .build();
        response.addHeader("Set-Cookie", sessionCookie.toString());
        response.addHeader("Set-Cookie", csrfCookie.toString());
    }
}
