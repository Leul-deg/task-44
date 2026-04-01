package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.AdminUserCreateRequest;
import com.shiftworks.jobops.dto.AdminUserResponse;
import com.shiftworks.jobops.dto.AdminUserRoleChangeRequest;
import com.shiftworks.jobops.dto.AdminUserUpdateRequest;
import com.shiftworks.jobops.dto.PageResponse;
import com.shiftworks.jobops.dto.ResetPasswordResponse;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(value = "role", required = false) String role,
                                                @RequestParam(value = "status", required = false) String status,
                                                @RequestParam(value = "search", required = false) String search,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "size", defaultValue = "10") int size) {
        return adminUserService.listUsers(role, status, search, page, size);
    }

    @GetMapping("/{id}")
    public AdminUserResponse detail(@PathVariable Long id) {
        return adminUserService.getUser(id);
    }

    @PostMapping
    public AdminUserResponse create(@Valid @RequestBody AdminUserCreateRequest request) {
        return adminUserService.create(request);
    }

    @PutMapping("/{id}")
    public AdminUserResponse update(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request, Authentication authentication) {
        AuthenticatedUser actor = (AuthenticatedUser) authentication.getPrincipal();
        return adminUserService.update(id, request, actor);
    }

    @PutMapping("/{id}/role")
    public void changeRole(@PathVariable Long id, @Valid @RequestBody AdminUserRoleChangeRequest request, Authentication authentication) {
        AuthenticatedUser actor = (AuthenticatedUser) authentication.getPrincipal();
        adminUserService.changeRole(id, request, actor);
    }

    @PutMapping("/{id}/unlock")
    public void unlock(@PathVariable Long id) {
        adminUserService.unlock(id);
    }

    @PutMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable Long id) {
        return adminUserService.resetPassword(id);
    }
}
