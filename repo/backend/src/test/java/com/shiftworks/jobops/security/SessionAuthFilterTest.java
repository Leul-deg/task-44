package com.shiftworks.jobops.security;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionAuthFilterTest {

    @Mock private SessionService sessionService;
    @Mock private FilterChain filterChain;
    private AppProperties appProperties;
    private SessionAuthFilter filter;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        appProperties = new AppProperties();
        AppProperties.Session sessionProps = new AppProperties.Session();
        sessionProps.setIdleTimeoutMinutes(30);
        sessionProps.setAbsoluteTimeoutHours(12);
        appProperties.setSession(sessionProps);
        filter = new SessionAuthFilter(sessionService, appProperties);
    }

    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(UserRole.EMPLOYER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        return user;
    }

    private UserSession buildSession(User user, Instant lastActive, Instant absoluteExpiry) {
        UserSession session = new UserSession();
        session.setId("session-123");
        session.setCsrfToken("csrf-123");
        session.setUser(user);
        session.setValid(true);
        session.setLastActiveAt(lastActive);
        session.setAbsoluteExpiry(absoluteExpiry);
        return session;
    }

    @Test
    void validSessionEstablishesSecurityContext() throws Exception {
        User user = buildUser();
        UserSession session = buildSession(user, Instant.now(), Instant.now().plus(12, ChronoUnit.HOURS));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jobs");
        request.setCookies(new Cookie(SessionService.SESSION_COOKIE, "session-123"));
        when(sessionService.findValidSession("session-123")).thenReturn(Optional.of(session));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(sessionService).touchSession(session);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void idleTimedOutSessionIsInvalidated() throws Exception {
        User user = buildUser();
        UserSession session = buildSession(user,
                Instant.now().minus(45, ChronoUnit.MINUTES),
                Instant.now().plus(12, ChronoUnit.HOURS));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jobs");
        request.setCookies(new Cookie(SessionService.SESSION_COOKIE, "session-123"));
        when(sessionService.findValidSession("session-123")).thenReturn(Optional.of(session));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(sessionService).invalidateSession(session);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void absoluteTimedOutSessionIsInvalidated() throws Exception {
        User user = buildUser();
        UserSession session = buildSession(user,
                Instant.now(),
                Instant.now().minus(1, ChronoUnit.HOURS));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jobs");
        request.setCookies(new Cookie(SessionService.SESSION_COOKIE, "session-123"));
        when(sessionService.findValidSession("session-123")).thenReturn(Optional.of(session));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(sessionService).invalidateSession(session);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void expiredPasswordReturns403() throws Exception {
        User user = buildUser();
        user.setPasswordChangedAt(Instant.now().minus(100, ChronoUnit.DAYS));
        UserSession session = buildSession(user, Instant.now(), Instant.now().plus(12, ChronoUnit.HOURS));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jobs");
        request.setCookies(new Cookie(SessionService.SESSION_COOKIE, "session-123"));
        when(sessionService.findValidSession("session-123")).thenReturn(Optional.of(session));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("passwordExpired"));
    }

    @Test
    void changePasswordEndpointAllowedWhenPasswordExpired() throws Exception {
        User user = buildUser();
        user.setPasswordChangedAt(Instant.now().minus(100, ChronoUnit.DAYS));
        UserSession session = buildSession(user, Instant.now(), Instant.now().plus(12, ChronoUnit.HOURS));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/change-password");
        request.setCookies(new Cookie(SessionService.SESSION_COOKIE, "session-123"));
        when(sessionService.findValidSession("session-123")).thenReturn(Optional.of(session));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noSessionCookiePassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
