package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.controller.AuthController;
import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.AuthService;
import com.shiftworks.jobops.service.CaptchaService;
import com.shiftworks.jobops.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuthService authService;
    @MockBean private CaptchaService captchaService;
    @MockBean private SessionService sessionService;

    @Test
    void loginEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"test\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void registerEndpointRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new\",\"email\":\"a@b.com\",\"password\":\"StrongPass123!\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void responsesIncludeSecurityHeaders() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"test\"}"))
            .andExpect(header().exists("Content-Security-Policy"))
            .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
