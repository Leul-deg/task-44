package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.ClaimRequest;
import com.shiftworks.jobops.dto.ClaimResponse;
import com.shiftworks.jobops.dto.ClaimUpdateRequest;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.entity.Claim;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.ClaimStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.ClaimRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
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
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> listClaims(AuthenticatedUser user, String statusValue, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Claim> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (user.role() == UserRole.EMPLOYER) {
                predicate = cb.and(predicate, cb.equal(root.get("claimant").get("id"), user.id()));
            }
            if (statusValue != null && !statusValue.isBlank()) {
                ClaimStatus status = ClaimStatus.valueOf(statusValue.toUpperCase(Locale.ROOT));
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            return predicate;
        };
        Page<Claim> result = claimRepository.findAll(spec, pageable);
        List<ClaimResponse> items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size, result.getTotalPages());
    }

    @Transactional
    public ClaimResponse createClaim(AuthenticatedUser user, ClaimRequest request) {
        log.info("Claim created for jobId={} by userId={}", request.jobPostingId(), user.id());
        if (user.role() != UserRole.EMPLOYER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "claim: Employers only");
        }
        JobPosting jobPosting = jobPostingRepository.findById(request.jobPostingId())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "jobPosting: Not found"));
        User claimant = userRepository.findById(user.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));
        Claim claim = new Claim();
        claim.setJobPosting(jobPosting);
        claim.setClaimant(claimant);
        claim.setStatus(ClaimStatus.OPEN);
        claim.setDescription(request.description());
        claim.setCreatedAt(Instant.now());
        claim.setUpdatedAt(Instant.now());
        claimRepository.save(claim);
        auditService.log(user.id(), "CLAIM_CREATED", "CLAIM", claim.getId(), null, claim);
        return toResponse(claim);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaim(AuthenticatedUser user, Long id) {
        Claim claim = claimRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "claim: Not found"));
        if (user.role() == UserRole.ADMIN || claim.getClaimant().getId().equals(user.id())) {
            return toResponse(claim);
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "claim: Access denied");
    }

    @Transactional
    public ClaimResponse updateClaim(AuthenticatedUser user, Long id, ClaimUpdateRequest request) {
        if (user.role() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "claim: Admin only");
        }
        Claim claim = claimRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "claim: Not found"));
        if (request.status() != null) {
            ensureTransition(claim.getStatus(), request.status());
            claim.setStatus(request.status());
        }
        if (request.resolution() != null) {
            claim.setResolution(request.resolution());
        }
        if (request.assignedTo() != null) {
            User assignee = userRepository.findById(request.assignedTo())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "assignedTo: User not found"));
            claim.setAssignedTo(assignee);
        }
        claim.setUpdatedAt(Instant.now());
        claimRepository.save(claim);
        auditService.log(user.id(), "CLAIM_UPDATED", "CLAIM", id, null, claim);
        return toResponse(claim);
    }

    private void ensureTransition(ClaimStatus current, ClaimStatus next) {
        if (current == next) {
            return;
        }
        switch (current) {
            case OPEN -> {
                if (next != ClaimStatus.IN_PROGRESS) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "status: OPEN can only move to IN_PROGRESS");
                }
            }
            case IN_PROGRESS -> {
                if (next != ClaimStatus.RESOLVED) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "status: IN_PROGRESS can only move to RESOLVED");
                }
            }
            case RESOLVED -> {
                if (next != ClaimStatus.CLOSED) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "status: RESOLVED can only move to CLOSED");
                }
            }
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "status: Cannot transition from " + current);
        }
    }

    private ClaimResponse toResponse(Claim claim) {
        return new ClaimResponse(
            claim.getId(),
            claim.getJobPosting().getId(),
            claim.getJobPosting().getTitle(),
            claim.getClaimant().getId(),
            claim.getClaimant().getUsername(),
            claim.getStatus(),
            claim.getDescription(),
            claim.getResolution(),
            claim.getAssignedTo() != null ? claim.getAssignedTo().getId() : null,
            claim.getAssignedTo() != null ? claim.getAssignedTo().getUsername() : null,
            claim.getCreatedAt(),
            claim.getUpdatedAt()
        );
    }
}
