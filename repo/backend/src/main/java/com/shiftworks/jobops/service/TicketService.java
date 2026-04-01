package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.TicketRequest;
import com.shiftworks.jobops.dto.TicketResponse;
import com.shiftworks.jobops.dto.TicketUpdateRequest;
import com.shiftworks.jobops.entity.Ticket;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.TicketPriority;
import com.shiftworks.jobops.enums.TicketStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.TicketRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> listTickets(AuthenticatedUser user, String statusValue, String priorityValue, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Ticket> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (user.role() != UserRole.ADMIN) {
                predicate = cb.and(predicate, cb.equal(root.get("reporter").get("id"), user.id()));
            }
            if (statusValue != null && !statusValue.isBlank()) {
                TicketStatus status = TicketStatus.valueOf(statusValue.toUpperCase(Locale.ROOT));
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (priorityValue != null && !priorityValue.isBlank()) {
                TicketPriority priority = TicketPriority.valueOf(priorityValue.toUpperCase(Locale.ROOT));
                predicate = cb.and(predicate, cb.equal(root.get("priority"), priority));
            }
            return predicate;
        };
        Page<Ticket> result = ticketRepository.findAll(spec, pageable);
        List<TicketResponse> items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size, result.getTotalPages());
    }

    @Transactional
    public TicketResponse createTicket(AuthenticatedUser user, TicketRequest request) {
        log.info("Ticket created priority={} by userId={}", request.priority(), user.id());
        User reporter = userRepository.findById(user.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        Ticket ticket = new Ticket();
        ticket.setReporter(reporter);
        ticket.setSubject(request.subject());
        ticket.setDescription(request.description());
        ticket.setPriority(request.priority());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());
        ticketRepository.save(ticket);
        auditService.log(user.id(), "TICKET_CREATED", "TICKET", ticket.getId(), null, ticket);
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(AuthenticatedUser user, Long id) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ticket: Not found"));
        if (user.role() == UserRole.ADMIN || ticket.getReporter().getId().equals(user.id())) {
            return toResponse(ticket);
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "ticket: Access denied");
    }

    @Transactional
    public TicketResponse updateTicket(Long id, TicketUpdateRequest request) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ticket: Not found"));
        if (request.status() != null) {
            ticket.setStatus(request.status());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.resolution() != null) {
            ticket.setResolution(request.resolution());
        }
        if (request.assignedTo() != null) {
            User assignee = userRepository.findById(request.assignedTo())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "assignedTo: User not found"));
            ticket.setAssignedTo(assignee);
        }
        ticket.setUpdatedAt(Instant.now());
        ticketRepository.save(ticket);
        return toResponse(ticket);
    }

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
            ticket.getId(),
            ticket.getSubject(),
            ticket.getDescription(),
            ticket.getStatus(),
            ticket.getPriority(),
            ticket.getReporter().getId(),
            ticket.getReporter().getUsername(),
            ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null,
            ticket.getAssignedTo() != null ? ticket.getAssignedTo().getUsername() : null,
            ticket.getResolution(),
            ticket.getCreatedAt(),
            ticket.getUpdatedAt()
        );
    }
}
