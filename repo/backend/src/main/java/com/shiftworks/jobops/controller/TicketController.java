package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.TicketRequest;
import com.shiftworks.jobops.dto.TicketResponse;
import com.shiftworks.jobops.dto.TicketUpdateRequest;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public PageResponse<TicketResponse> list(Authentication authentication,
                                             @RequestParam(value = "status", required = false) String status,
                                             @RequestParam(value = "priority", required = false) String priority,
                                             @RequestParam(value = "page", defaultValue = "0") int page,
                                             @RequestParam(value = "size", defaultValue = "10") int size) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ticketService.listTickets(user, status, priority, page, size);
    }

    @PostMapping
    public TicketResponse create(Authentication authentication, @Valid @RequestBody TicketRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ticketService.createTicket(user, request);
    }

    @GetMapping("/{id}")
    public TicketResponse detail(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ticketService.getTicket(user, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse update(@PathVariable Long id, @Valid @RequestBody TicketUpdateRequest request) {
        return ticketService.updateTicket(id, request);
    }
}
