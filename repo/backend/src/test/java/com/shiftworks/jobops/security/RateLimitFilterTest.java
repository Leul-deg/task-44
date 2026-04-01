package com.shiftworks.jobops.security;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.service.SessionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private SessionService sessionService;
    @Mock private FilterChain filterChain;
    private AppProperties appProperties;
    private RateLimitFilter filter;

    @BeforeEach
    void setup() {
        appProperties = new AppProperties();
        AppProperties.RateLimit rl = new AppProperties.RateLimit();
        rl.setPerMinute(3);
        rl.setAuthPerMinute(2);
        appProperties.setRateLimit(rl);
        filter = new RateLimitFilter(sessionService, appProperties);
    }

    @Test
    void allowsRequestsWithinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void blocksAuthRequestsOverIpLimit() throws Exception {
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.2");
            filter.doFilterInternal(req, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/api/auth/login");
        blocked.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(blocked, response, filterChain);

        assertEquals(429, response.getStatus());
    }
}
