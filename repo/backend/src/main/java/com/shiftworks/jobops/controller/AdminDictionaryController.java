package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.AdminCategoryRequest;
import com.shiftworks.jobops.dto.AdminCategoryResponse;
import com.shiftworks.jobops.dto.AdminLocationRequest;
import com.shiftworks.jobops.dto.AdminLocationResponse;
import com.shiftworks.jobops.service.AdminDictionaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDictionaryController {

    private final AdminDictionaryService adminDictionaryService;

    @GetMapping("/categories")
    public List<AdminCategoryResponse> categories() {
        return adminDictionaryService.listCategories();
    }

    @PostMapping("/categories")
    public AdminCategoryResponse createCategory(@Valid @RequestBody AdminCategoryRequest request) {
        return adminDictionaryService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public AdminCategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody AdminCategoryRequest request) {
        return adminDictionaryService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable Long id) {
        adminDictionaryService.deactivateCategory(id);
    }

    @GetMapping("/locations")
    public List<AdminLocationResponse> locations(@RequestParam(value = "state", required = false) String state) {
        return adminDictionaryService.listLocations(state);
    }

    @PostMapping("/locations")
    public AdminLocationResponse createLocation(@Valid @RequestBody AdminLocationRequest request) {
        return adminDictionaryService.createLocation(request);
    }

    @PutMapping("/locations/{id}")
    public AdminLocationResponse updateLocation(@PathVariable Long id, @Valid @RequestBody AdminLocationRequest request) {
        return adminDictionaryService.updateLocation(id, request);
    }

    @DeleteMapping("/locations/{id}")
    public void deleteLocation(@PathVariable Long id) {
        adminDictionaryService.deactivateLocation(id);
    }
}
