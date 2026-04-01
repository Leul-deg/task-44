package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.CategoryResponse;
import com.shiftworks.jobops.dto.LocationResponse;
import com.shiftworks.jobops.repository.CategoryRepository;
import com.shiftworks.jobops.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DictionaryController {

    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(category -> new CategoryResponse(category.getId(), category.getName(), category.getDescription()))
            .toList();
    }

    @GetMapping("/locations")
    public List<LocationResponse> locations(@RequestParam(value = "state", required = false) String state) {
        if (state != null && !state.isBlank()) {
            return locationRepository.findByActiveTrueAndStateOrderByCityAsc(state).stream()
                .map(location -> new LocationResponse(location.getId(), location.getState(), location.getCity()))
                .toList();
        }
        return locationRepository.findByActiveTrueOrderByStateAscCityAsc().stream()
            .map(location -> new LocationResponse(location.getId(), location.getState(), location.getCity()))
            .toList();
    }

    @GetMapping("/locations/states")
    public List<String> states() {
        return locationRepository.findActiveStates();
    }
}
