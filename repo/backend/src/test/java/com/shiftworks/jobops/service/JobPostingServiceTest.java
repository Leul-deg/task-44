package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.JobPostingRequest;
import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.JobPosting;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.enums.PayType;
import com.shiftworks.jobops.enums.SettlementType;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.JobPostingHistoryRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.LocationRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobPostingServiceTest {

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private JobPostingHistoryRepository jobPostingHistoryRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private UserRepository userRepository;
    @Mock private StepUpVerificationService stepUpVerificationService;
    @Mock private JobHistoryService jobHistoryService;
    @Mock private AuditService auditService;
    @Spy private AppProperties appProperties = buildProps();
    @InjectMocks private JobPostingService jobPostingService;

    private Category category;
    private Location location;
    private User employer;
    private AuthenticatedUser authUser;

    @BeforeEach
    void setup() {
        employer = new User();
        employer.setId(10L);
        employer.setRole(UserRole.EMPLOYER);
        authUser = new AuthenticatedUser(employer.getId(), "employer", UserRole.EMPLOYER);
        when(userRepository.findById(employer.getId())).thenReturn(Optional.of(employer));

        category = new Category();
        category.setId(1L);
        category.setActive(true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        location = new Location();
        location.setId(1L);
        location.setActive(true);
        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));

        when(jobPostingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static AppProperties buildProps() {
        AppProperties props = new AppProperties();
        AppProperties.JobValidation validation = new AppProperties.JobValidation();
        AppProperties.ValidityDays validity = new AppProperties.ValidityDays();
        validity.setDefaultDays(30);
        validity.setMax(90);
        AppProperties.Range hourly = new AppProperties.Range();
        hourly.setMin(12);
        hourly.setMax(75);
        AppProperties.Range flatPay = new AppProperties.Range();
        flatPay.setMin(12);
        flatPay.setMax(5000);
        AppProperties.Range headcount = new AppProperties.Range();
        headcount.setMin(1);
        headcount.setMax(500);
        AppProperties.Range weeklyHours = new AppProperties.Range();
        weeklyHours.setMin(1);
        weeklyHours.setMax(80);
        validation.setValidityDays(validity);
        validation.setHourlyPay(hourly);
        validation.setFlatPay(flatPay);
        validation.setHeadcount(headcount);
        validation.setWeeklyHours(weeklyHours);
        props.setJobValidation(validation);
        return props;
    }

    private JobPostingRequest baseRequest() {
        return new JobPostingRequest(
            "Valid Title",
            "This description is longer than twenty characters.",
            category.getId(),
            location.getId(),
            PayType.HOURLY,
            SettlementType.WEEKLY,
            BigDecimal.valueOf(20),
            5,
            BigDecimal.valueOf(40),
            "555-123-4567",
            List.of("tag1"),
            LocalDate.now(),
            LocalDate.now().plusDays(30)
        );
    }

    private JobPosting buildJob(JobStatus status) {
        JobPosting job = new JobPosting();
        job.setId(2L);
        job.setEmployer(employer);
        job.setCategory(category);
        job.setLocation(location);
        job.setStatus(status);
        job.setTitle("Valid Job Title");
        job.setDescription("This is a valid job description longer than twenty characters");
        job.setPayType(PayType.HOURLY);
        job.setSettlementType(SettlementType.WEEKLY);
        job.setPayAmount(BigDecimal.valueOf(20));
        job.setHeadcount(5);
        job.setWeeklyHours(BigDecimal.valueOf(40));
        job.setContactPhone("555-123-4567");
        job.setValidityStart(java.time.LocalDate.now());
        job.setValidityEnd(java.time.LocalDate.now().plusDays(30));
        return job;
    }

    @Test
    void createDraftSuccess() {
        var response = jobPostingService.createJob(baseRequest(), authUser);
        assertEquals(JobStatus.DRAFT, response.status());
    }

    @Test
    void rejectHourlyPayBelow12() {
        JobPostingRequest request = new JobPostingRequest(
            "Valid Title",
            "This description is longer than twenty characters.",
            category.getId(),
            location.getId(),
            PayType.HOURLY,
            SettlementType.WEEKLY,
            BigDecimal.valueOf(11),
            5,
            BigDecimal.valueOf(40),
            "555-123-4567",
            List.of("tag1"),
            LocalDate.now(),
            LocalDate.now().plusDays(30)
        );
        var ex = assertThrows(BusinessException.class, () -> jobPostingService.createJob(request, authUser));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("payAmount"));
    }

    @Test
    void rejectHourlyPayAbove75() {
        JobPostingRequest request = new JobPostingRequest(
            "Valid Title",
            "This description is longer than twenty characters.",
            category.getId(),
            location.getId(),
            PayType.HOURLY,
            SettlementType.WEEKLY,
            BigDecimal.valueOf(76),
            5,
            BigDecimal.valueOf(40),
            "555-123-4567",
            List.of("tag1"),
            LocalDate.now(),
            LocalDate.now().plusDays(30)
        );
        assertThrows(BusinessException.class, () -> jobPostingService.createJob(request, authUser));
    }

    @Test
    void rejectHeadcountZero() {
        JobPostingRequest request = new JobPostingRequest(
            "Valid Title",
            "This description is longer than twenty characters.",
            category.getId(),
            location.getId(),
            PayType.HOURLY,
            SettlementType.WEEKLY,
            BigDecimal.valueOf(20),
            0,
            BigDecimal.valueOf(40),
            "555-123-4567",
            List.of("tag1"),
            LocalDate.now(),
            LocalDate.now().plusDays(30)
        );
        assertThrows(BusinessException.class, () -> jobPostingService.createJob(request, authUser));
    }

    @Test
    void rejectHeadcount501() {
        JobPostingRequest request = new JobPostingRequest(
            "Valid Title",
            "This description is longer than twenty characters.",
            category.getId(),
            location.getId(),
            PayType.HOURLY,
            SettlementType.WEEKLY,
            BigDecimal.valueOf(20),
            501,
            BigDecimal.valueOf(40),
            "555-123-4567",
            List.of("tag1"),
            LocalDate.now(),
            LocalDate.now().plusDays(30)
        );
        assertThrows(BusinessException.class, () -> jobPostingService.createJob(request, authUser));
    }

    @Test
    void rejectWeeklyHoursAbove80() {
        JobPostingRequest request = new JobPostingRequest(
            "Valid Title",
            "This description is longer than twenty characters.",
            category.getId(),
            location.getId(),
            PayType.HOURLY,
            SettlementType.WEEKLY,
            BigDecimal.valueOf(20),
            5,
            BigDecimal.valueOf(81),
            "555-123-4567",
            List.of("tag1"),
            LocalDate.now(),
            LocalDate.now().plusDays(30)
        );
        assertThrows(BusinessException.class, () -> jobPostingService.createJob(request, authUser));
    }

    @Test
    void rejectMoreThan10Tags() {
        var tags = IntStream.rangeClosed(1, 11).mapToObj(i -> "tag" + i).toList();
        JobPostingRequest request = new JobPostingRequest(
            "Valid Title",
            "This description is longer than twenty characters.",
            category.getId(),
            location.getId(),
            PayType.HOURLY,
            SettlementType.WEEKLY,
            BigDecimal.valueOf(20),
            5,
            BigDecimal.valueOf(40),
            "555-123-4567",
            tags,
            LocalDate.now(),
            LocalDate.now().plusDays(30)
        );
        var ex = assertThrows(BusinessException.class, () -> jobPostingService.createJob(request, authUser));
        assertTrue(ex.getMessage().contains("Maximum of 10 tags"));
    }

    @Test
    void submitTransitionsToPendingReview() {
        JobPosting job = buildJob(JobStatus.DRAFT);
        when(jobPostingRepository.findByIdAndEmployer_Id(job.getId(), authUser.id())).thenReturn(Optional.of(job));
        jobPostingService.submitForReview(job.getId(), authUser);
        assertEquals(JobStatus.PENDING_REVIEW, job.getStatus());
    }

    @Test
    void editOnlyInDraft() {
        JobPosting job = buildJob(JobStatus.PENDING_REVIEW);
        when(jobPostingRepository.findByIdAndEmployer_Id(job.getId(), authUser.id())).thenReturn(Optional.of(job));
        var ex = assertThrows(BusinessException.class, () -> jobPostingService.updateJob(job.getId(), baseRequest(), authUser));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only draft or rejected"));
    }
}
