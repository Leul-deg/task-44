package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.TicketUpdateRequest;
import com.shiftworks.jobops.entity.Ticket;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.TicketPriority;
import com.shiftworks.jobops.enums.TicketStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.repository.TicketRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceAuditTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @InjectMocks private TicketService ticketService;

    private AuthenticatedUser admin;

    @BeforeEach
    void setup() {
        admin = new AuthenticatedUser(1L, "admin", UserRole.ADMIN);
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Ticket buildTicket(Long id) {
        User reporter = new User();
        reporter.setId(5L);
        reporter.setUsername("reporter");

        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setReporter(reporter);
        ticket.setSubject("Test ticket");
        ticket.setDescription("Description");
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM);
        return ticket;
    }

    @Test
    void updateTicketAuditsWithBeforeAndAfter() {
        Ticket ticket = buildTicket(42L);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));

        TicketUpdateRequest request = new TicketUpdateRequest(TicketStatus.CLOSED, TicketPriority.HIGH, "Resolved", null);
        ticketService.updateTicket(42L, request, admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("TICKET_UPDATED"),
            eq("TICKET"),
            eq(42L),
            isNotNull(),
            isNotNull()
        );
    }
}
