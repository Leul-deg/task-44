package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.AdminCategoryRequest;
import com.shiftworks.jobops.dto.AdminLocationRequest;
import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.LocationRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDictionaryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private AuditService auditService;
    @InjectMocks private AdminDictionaryService adminDictionaryService;

    private AuthenticatedUser admin;

    @BeforeEach
    void setup() {
        admin = new AuthenticatedUser(1L, "admin", UserRole.ADMIN);
        when(categoryRepository.save(any())).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            if (c.getId() == null) c.setId(10L);
            return c;
        });
        when(locationRepository.save(any())).thenAnswer(inv -> {
            Location l = inv.getArgument(0);
            if (l.getId() == null) l.setId(20L);
            return l;
        });
    }

    // ----- category audit tests -----

    @Test
    void createCategoryAuditsWithActor() {
        when(categoryRepository.existsByNameIgnoreCase("Engineering")).thenReturn(false);

        adminDictionaryService.createCategory(new AdminCategoryRequest("Engineering", "Tech jobs"), admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("CATEGORY_CREATED"),
            eq("CATEGORY"),
            any(),
            isNull(),
            isNotNull()
        );
    }

    @Test
    void updateCategoryAuditsBeforeAndAfter() {
        Category existing = new Category();
        existing.setId(10L);
        existing.setName("OldName");
        existing.setDescription("Old desc");
        existing.setActive(true);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.countByCategory_IdAndStatusIn(eq(10L), any())).thenReturn(0L);

        adminDictionaryService.updateCategory(10L, new AdminCategoryRequest("NewName", "New desc"), admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("CATEGORY_UPDATED"),
            eq("CATEGORY"),
            eq(10L),
            isNotNull(),
            isNotNull()
        );
    }

    @Test
    void deactivateCategoryAuditsWithActor() {
        Category existing = new Category();
        existing.setId(10L);
        existing.setName("Engineering");
        existing.setActive(true);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.countByCategory_IdAndStatusIn(eq(10L), any())).thenReturn(0L);

        adminDictionaryService.deactivateCategory(10L, admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("CATEGORY_DEACTIVATED"),
            eq("CATEGORY"),
            eq(10L),
            isNotNull(),
            isNotNull()
        );
    }

    // ----- location audit tests -----

    @Test
    void createLocationAuditsWithActor() {
        when(locationRepository.existsByStateIgnoreCaseAndCityIgnoreCase("TX", "Austin")).thenReturn(false);

        adminDictionaryService.createLocation(new AdminLocationRequest("TX", "Austin"), admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("LOCATION_CREATED"),
            eq("LOCATION"),
            any(),
            isNull(),
            isNotNull()
        );
    }

    @Test
    void updateLocationAuditsBeforeAndAfter() {
        Location existing = new Location();
        existing.setId(20L);
        existing.setState("TX");
        existing.setCity("Austin");
        existing.setActive(true);
        when(locationRepository.findById(20L)).thenReturn(Optional.of(existing));

        adminDictionaryService.updateLocation(20L, new AdminLocationRequest("TX", "Houston"), admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("LOCATION_UPDATED"),
            eq("LOCATION"),
            eq(20L),
            isNotNull(),
            isNotNull()
        );
    }

    @Test
    void deactivateLocationAuditsWithActor() {
        Location existing = new Location();
        existing.setId(20L);
        existing.setState("TX");
        existing.setCity("Austin");
        existing.setActive(true);
        when(locationRepository.findById(20L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.countByLocation_IdAndStatusIn(eq(20L), any())).thenReturn(0L);

        adminDictionaryService.deactivateLocation(20L, admin);

        verify(auditService).log(
            eq(admin.id()),
            eq("LOCATION_DEACTIVATED"),
            eq("LOCATION"),
            eq(20L),
            isNotNull(),
            isNotNull()
        );
    }
}
