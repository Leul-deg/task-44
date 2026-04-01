package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.ClaimRequest;
import com.shiftworks.jobops.dto.ClaimResponse;
import com.shiftworks.jobops.dto.ClaimUpdateRequest;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.ClaimService;
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
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @GetMapping
    public PageResponse<ClaimResponse> list(Authentication authentication,
                                            @RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "page", defaultValue = "0") int page,
                                            @RequestParam(value = "size", defaultValue = "10") int size) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return claimService.listClaims(user, status, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    public ClaimResponse create(Authentication authentication, @Valid @RequestBody ClaimRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return claimService.createClaim(user, request);
    }

    @GetMapping("/{id}")
    public ClaimResponse detail(Authentication authentication, @PathVariable Long id) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return claimService.getClaim(user, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClaimResponse update(@PathVariable Long id, @Valid @RequestBody ClaimUpdateRequest request, Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return claimService.updateClaim(user, id, request);
    }
}
