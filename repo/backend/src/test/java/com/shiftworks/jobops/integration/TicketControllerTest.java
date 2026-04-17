package com.shiftworks.jobops.integration;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.config.SecurityConfig;
import com.shiftworks.jobops.controller.TicketController;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.TicketResponse;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.TicketPriority;
import com.shiftworks.jobops.enums.TicketStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import com.shiftworks.jobops.service.SessionService;
import com.shiftworks.jobops.service.TicketService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, SessionAuthFilter.class, CsrfValidationFilter.class,
         RateLimitFilter.class, SecurityHeadersFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class TicketControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private TicketService ticketService;
    @MockBean private SessionService sessionService;

    private UserSession buildSession(UserRole role) {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangedAt(Instant.now());
        UserSession session = new UserSession();
        session.setId("test-session-token");
        session.setUser(user);
        session.setValid(true);
        session.setLastActiveAt(Instant.now());
        session.setAbsoluteExpiry(Instant.now().plusSeconds(43200));
        session.setCsrfToken("test-csrf");
        return session;
    }

    private TicketResponse sampleTicket() {
        return new TicketResponse(10L, "Build broken", "Description here",
                TicketStatus.OPEN, TicketPriority.HIGH, 1L, "testuser", null, null, null,
                Instant.now(), Instant.now());
    }

    @Test
    void listTicketsAsEmployer_returns200WithPage() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));
        when(ticketService.listTickets(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(sampleTicket()), 1L, 0, 10, 1));

        mockMvc.perform(get("/api/tickets")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void createTicketAsEmployer_returns200WithId() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));
        when(ticketService.createTicket(any(), any())).thenReturn(sampleTicket());

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Build broken\",\"description\":\"Details here\",\"priority\":\"HIGH\"}")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)));
    }

    @Test
    void getTicketDetailAsEmployer_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));
        when(ticketService.getTicket(any(), eq(10L))).thenReturn(sampleTicket());

        mockMvc.perform(get("/api/tickets/10")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject", is("Build broken")));
    }

    @Test
    void updateTicketAsAdmin_returns200() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.ADMIN)));
        TicketResponse resolved = new TicketResponse(10L, "Build broken", "Description here",
                TicketStatus.RESOLVED, TicketPriority.HIGH, 1L, "testuser", null, null, "Fixed",
                Instant.now(), Instant.now());
        when(ticketService.updateTicket(eq(10L), any(), any())).thenReturn(resolved);

        mockMvc.perform(put("/api/tickets/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"priority\":\"HIGH\",\"resolution\":\"Fixed\"}")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")));
    }

    @Test
    void updateTicketDeniedForEmployer_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.of(buildSession(UserRole.EMPLOYER)));

        mockMvc.perform(put("/api/tickets/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"priority\":\"HIGH\"}")
                        .cookie(new Cookie(SessionService.SESSION_COOKIE, "test-session-token"))
                        .header("X-XSRF-TOKEN", "test-csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTicketsUnauthenticated_returns403() throws Exception {
        when(sessionService.findValidSession(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isForbidden());
    }
}
