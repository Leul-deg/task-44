package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.entity.Appeal;
import com.shiftworks.jobops.entity.Claim;
import com.shiftworks.jobops.entity.FileAttachment;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.Ticket;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.AttachmentEntityType;
import com.shiftworks.jobops.enums.AppealStatus;
import com.shiftworks.jobops.enums.ClaimStatus;
import com.shiftworks.jobops.enums.FileStatus;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.TicketPriority;
import com.shiftworks.jobops.enums.TicketStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.dto.ClaimRequest;
import com.shiftworks.jobops.repository.AppealRepository;
import com.shiftworks.jobops.repository.ClaimRepository;
import com.shiftworks.jobops.repository.FileAttachmentRepository;
import com.shiftworks.jobops.repository.JobPostingHistoryRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.LocationRepository;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.ReviewActionRepository;
import com.shiftworks.jobops.repository.TicketRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Verifies that object-level authorization prevents cross-user access to
 * resources that belong to another user (employer A vs employer B, etc.).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObjectLevelAuthorizationTest {

    // ---------- shared users ----------

    private User userA;   // owner of all resources
    private User userB;   // the attacker

    private AuthenticatedUser authA;
    private AuthenticatedUser authB;

    @BeforeEach
    void buildUsers() {
        userA = new User();
        userA.setId(1L);
        userA.setUsername("employerA");
        userA.setRole(UserRole.EMPLOYER);

        userB = new User();
        userB.setId(2L);
        userB.setUsername("employerB");
        userB.setRole(UserRole.EMPLOYER);

        authA = new AuthenticatedUser(1L, "employerA", UserRole.EMPLOYER);
        authB = new AuthenticatedUser(2L, "employerB", UserRole.EMPLOYER);
    }

    // =========================================================
    // 1. JobPostingService — detail and history
    // =========================================================

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private JobPostingHistoryRepository jobPostingHistoryRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserRepository userRepository;
    @Mock private StepUpVerificationService stepUpVerificationService;
    @Mock private JobHistoryService jobHistoryService;
    @Mock private AuditService auditService;
    @Spy  private AppProperties appProperties = new AppProperties();
    @InjectMocks private JobPostingService jobPostingService;

    private JobPosting jobOwnedByA() {
        JobPosting job = new JobPosting();
        job.setId(10L);
        job.setStatus(JobStatus.DRAFT);
        job.setEmployer(userA);
        return job;
    }

    @Test
    void employerBCannotGetDetailOfEmployerAJob() {
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobOwnedByA()));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> jobPostingService.getJob(10L, authB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void employerBCannotGetHistoryOfEmployerAJob() {
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobOwnedByA()));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> jobPostingService.history(10L, authB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // =========================================================
    // 2. AppealService — detail
    // =========================================================

    @Mock private AppealRepository appealRepository;
    @Mock private ReviewActionRepository reviewActionRepository;
    @InjectMocks private AppealService appealService;

    private Appeal appealOwnedByA() {
        JobPosting job = jobOwnedByA();
        Appeal appeal = new Appeal();
        appeal.setId(20L);
        appeal.setJobPosting(job);
        appeal.setEmployer(userA);
        appeal.setStatus(AppealStatus.PENDING);
        return appeal;
    }

    @Test
    void employerBCannotGetDetailOfEmployerAAppeal() {
        when(appealRepository.findById(20L)).thenReturn(Optional.of(appealOwnedByA()));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> appealService.getAppeal(20L, authB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // =========================================================
    // 3. ClaimService — detail
    // =========================================================

    @Mock private ClaimRepository claimRepository;
    @InjectMocks private ClaimService claimService;

    private Claim claimOwnedByA() {
        JobPosting job = jobOwnedByA();
        Claim claim = new Claim();
        claim.setId(30L);
        claim.setJobPosting(job);
        claim.setClaimant(userA);
        claim.setStatus(ClaimStatus.OPEN);
        claim.setDescription("Wage not paid");
        return claim;
    }

    @Test
    void employerBCannotGetDetailOfEmployerAClaim() {
        when(claimRepository.findById(30L)).thenReturn(Optional.of(claimOwnedByA()));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> claimService.getClaim(authB, 30L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void reviewerCannotListClaims() {
        AuthenticatedUser reviewer = new AuthenticatedUser(3L, "reviewer", UserRole.REVIEWER);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> claimService.listClaims(reviewer, null, 0, 10));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void invalidClaimStatusReturnsBadRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> claimService.listClaims(authA, "NOT_A_STATUS", 0, 10));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void employerCannotCreateClaimForAnotherEmployersJob() {
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobOwnedByA()));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> claimService.createClaim(authB, new ClaimRequest(10L, "Cross-tenant access attempt")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // =========================================================
    // 4. TicketService — detail
    // =========================================================

    @Mock private TicketRepository ticketRepository;
    @InjectMocks private TicketService ticketService;

    private Ticket ticketOwnedByA() {
        Ticket ticket = new Ticket();
        ticket.setId(40L);
        ticket.setReporter(userA);
        ticket.setSubject("Help");
        ticket.setDescription("Some issue");
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM);
        return ticket;
    }

    @Test
    void userBCannotGetDetailOfUserATicket() {
        when(ticketRepository.findById(40L)).thenReturn(Optional.of(ticketOwnedByA()));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> ticketService.getTicket(authB, 40L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void invalidTicketFiltersReturnBadRequest() {
        BusinessException badStatus = assertThrows(BusinessException.class,
            () -> ticketService.listTickets(authA, "UNKNOWN_STATUS", null, 0, 10));
        assertEquals(HttpStatus.BAD_REQUEST, badStatus.getStatus());

        BusinessException badPriority = assertThrows(BusinessException.class,
            () -> ticketService.listTickets(authA, null, "UNKNOWN_PRIORITY", 0, 10));
        assertEquals(HttpStatus.BAD_REQUEST, badPriority.getStatus());
    }

    // =========================================================
    // 5. FileService — download by non-owner non-admin
    // =========================================================

    @Mock private FileAttachmentRepository fileAttachmentRepository;
    @Mock private FileStorageService fileStorageService;
    @InjectMocks private FileService fileService;

    /** Attachment uploaded by userA, attached to a CLAIM owned by userA. */
    private FileAttachment attachmentOwnedByA() {
        FileAttachment fa = new FileAttachment();
        fa.setId(50L);
        fa.setEntityType(AttachmentEntityType.CLAIM);
        fa.setEntityId(30L);
        fa.setOriginalFilename("doc.pdf");
        fa.setStoredPath("CLAIM/30/doc.pdf");
        fa.setFileType("pdf");
        fa.setFileSize(1024L);
        fa.setStatus(FileStatus.ACTIVE);
        fa.setUploadedBy(userA);
        return fa;
    }

    @Test
    void employerBCannotDownloadFileUploadedByEmployerA() {
        when(fileAttachmentRepository.findById(50L)).thenReturn(Optional.of(attachmentOwnedByA()));
        // The underlying claim belongs to userA — so userB is not entity owner either
        Claim claimA = claimOwnedByA();
        when(claimRepository.findById(30L)).thenReturn(Optional.of(claimA));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> fileService.download(50L, false, authB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }
}
