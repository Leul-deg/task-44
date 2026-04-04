package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.AdminCategoryRequest;
import com.shiftworks.jobops.dto.AdminCategoryResponse;
import com.shiftworks.jobops.dto.AdminLocationRequest;
import com.shiftworks.jobops.dto.AdminLocationResponse;
import com.shiftworks.jobops.entity.Category;
import com.shiftworks.jobops.entity.Location;
import com.shiftworks.jobops.enums.JobStatus;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.JobPostingRepository;
import com.shiftworks.jobops.repository.LocationRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDictionaryService {

    private static final List<JobStatus> ACTIVE_STATUSES = List.of(
        JobStatus.DRAFT,
        JobStatus.PENDING_REVIEW,
        JobStatus.APPROVED,
        JobStatus.PUBLISHED
    );

    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
            .map(category -> new AdminCategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                jobPostingRepository.countByCategory_IdAndStatusIn(category.getId(), ACTIVE_STATUSES),
                category.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    public AdminCategoryResponse createCategory(AdminCategoryRequest request, AuthenticatedUser actor) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "category: Name already exists");
        }
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        category.setActive(true);
        category.setCreatedAt(Instant.now());
        category.setUpdatedAt(Instant.now());
        categoryRepository.save(category);
        auditService.log(actor.id(), "CATEGORY_CREATED", "CATEGORY", category.getId(), null,
            Map.of("name", category.getName(), "description", category.getDescription() != null ? category.getDescription() : ""));
        return new AdminCategoryResponse(category.getId(), category.getName(), category.getDescription(), true, 0, category.getCreatedAt());
    }

    @Transactional
    public AdminCategoryResponse updateCategory(Long id, AdminCategoryRequest request, AuthenticatedUser actor) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "category: Not found"));
        Map<String, Object> before = Map.of(
            "name", category.getName(),
            "description", category.getDescription() != null ? category.getDescription() : "");
        category.setName(request.name());
        category.setDescription(request.description());
        category.setUpdatedAt(Instant.now());
        categoryRepository.save(category);
        auditService.log(actor.id(), "CATEGORY_UPDATED", "CATEGORY", id, before,
            Map.of("name", category.getName(), "description", category.getDescription() != null ? category.getDescription() : ""));
        long activeCount = jobPostingRepository.countByCategory_IdAndStatusIn(id, ACTIVE_STATUSES);
        return new AdminCategoryResponse(category.getId(), category.getName(), category.getDescription(), category.isActive(), activeCount, category.getCreatedAt());
    }

    @Transactional
    public void deactivateCategory(Long id, AuthenticatedUser actor) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "category: Not found"));
        long activeCount = jobPostingRepository.countByCategory_IdAndStatusIn(id, ACTIVE_STATUSES);
        if (activeCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cannot deactivate — " + activeCount + " active postings use this category.");
        }
        category.setActive(false);
        category.setUpdatedAt(Instant.now());
        categoryRepository.save(category);
        auditService.log(actor.id(), "CATEGORY_DEACTIVATED", "CATEGORY", id,
            Map.of("name", category.getName(), "active", true),
            Map.of("name", category.getName(), "active", false));
    }

    @Transactional(readOnly = true)
    public List<AdminLocationResponse> listLocations(String state) {
        return locationRepository.findAll().stream()
            .filter(location -> state == null || state.isBlank() || location.getState().equalsIgnoreCase(state))
            .map(location -> new AdminLocationResponse(location.getId(), location.getState(), location.getCity(), location.isActive(), location.getCreatedAt()))
            .toList();
    }

    @Transactional
    public AdminLocationResponse createLocation(AdminLocationRequest request, AuthenticatedUser actor) {
        if (locationRepository.existsByStateIgnoreCaseAndCityIgnoreCase(request.state(), request.city())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "location: Already exists");
        }
        Location location = new Location();
        location.setState(request.state());
        location.setCity(request.city());
        location.setActive(true);
        location.setCreatedAt(Instant.now());
        location.setUpdatedAt(Instant.now());
        locationRepository.save(location);
        auditService.log(actor.id(), "LOCATION_CREATED", "LOCATION", location.getId(), null,
            Map.of("state", location.getState(), "city", location.getCity()));
        return new AdminLocationResponse(location.getId(), location.getState(), location.getCity(), true, location.getCreatedAt());
    }

    @Transactional
    public AdminLocationResponse updateLocation(Long id, AdminLocationRequest request, AuthenticatedUser actor) {
        Location location = locationRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "location: Not found"));
        Map<String, Object> before = Map.of("state", location.getState(), "city", location.getCity());
        location.setState(request.state());
        location.setCity(request.city());
        location.setUpdatedAt(Instant.now());
        locationRepository.save(location);
        auditService.log(actor.id(), "LOCATION_UPDATED", "LOCATION", id, before,
            Map.of("state", location.getState(), "city", location.getCity()));
        return new AdminLocationResponse(location.getId(), location.getState(), location.getCity(), location.isActive(), location.getCreatedAt());
    }

    @Transactional
    public void deactivateLocation(Long id, AuthenticatedUser actor) {
        Location location = locationRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "location: Not found"));
        long activeCount = jobPostingRepository.countByLocation_IdAndStatusIn(id, ACTIVE_STATUSES);
        if (activeCount > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cannot deactivate — " + activeCount + " active postings use this location.");
        }
        location.setActive(false);
        location.setUpdatedAt(Instant.now());
        locationRepository.save(location);
        auditService.log(actor.id(), "LOCATION_DEACTIVATED", "LOCATION", id,
            Map.of("state", location.getState(), "city", location.getCity(), "active", true),
            Map.of("state", location.getState(), "city", location.getCity(), "active", false));
    }
}
