package com.shiftworks.jobops.security;

import com.shiftworks.jobops.entity.UserSession;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CsrfValidationFilterTest {

    private CsrfValidationFilter filter;
    @Mock private FilterChain filterChain;

    @BeforeEach
    void setup() {
        filter = new CsrfValidationFilter();
        SecurityContextHolder.clearContext();
    }

    private UserSession buildSession(String csrfToken) {
        UserSession session = new UserSession();
        session.setCsrfToken(csrfToken);
        return session;
    }

    @Test
    void getRequestPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void loginPostExcludedFromCsrf() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void postWithValidCsrfPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/jobs");
        request.addHeader("X-XSRF-TOKEN", "valid-token");
        UserSession session = buildSession("valid-token");
        request.setAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE, session);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void postWithMissingCsrfReturns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/jobs");
        UserSession session = buildSession("valid-token");
        request.setAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE, session);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void postWithWrongCsrfReturns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/jobs");
        request.addHeader("X-XSRF-TOKEN", "wrong-token");
        UserSession session = buildSession("valid-token");
        request.setAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE, session);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void postWithNoSessionPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    // register is an admin-only state-changing endpoint — CSRF must be enforced

    @Test
    void registerRequiresValidCsrfWhenAuthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.addHeader("X-XSRF-TOKEN", "valid-token");
        UserSession session = buildSession("valid-token");
        request.setAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE, session);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void registerWithMissingCsrfReturns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        UserSession session = buildSession("valid-token");
        request.setAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE, session);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void registerWithWrongCsrfReturns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.addHeader("X-XSRF-TOKEN", "tampered-token");
        UserSession session = buildSession("valid-token");
        request.setAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE, session);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }
}
