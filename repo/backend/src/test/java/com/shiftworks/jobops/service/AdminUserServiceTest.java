package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.AdminUserCreateRequest;
import com.shiftworks.jobops.dto.AdminUserRoleChangeRequest;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private SessionService sessionService;
    @Mock private StepUpVerificationService stepUpVerificationService;
    @Mock private PasswordPolicyService passwordPolicyService;
    @InjectMocks private AdminUserService adminUserService;

    private AuthenticatedUser adminUser;

    @BeforeEach
    void setup() {
        adminUser = new AuthenticatedUser(1L, "admin", UserRole.ADMIN);
    }

    private User buildUser(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void cannotChangeOwnRole() {
        User self = buildUser(1L, UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));
        AdminUserRoleChangeRequest request = new AdminUserRoleChangeRequest(UserRole.EMPLOYER, "password");

        var ex = assertThrows(BusinessException.class,
            () -> adminUserService.changeRole(1L, request, adminUser));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void unlockLockedUser() {
        User locked = buildUser(2L, UserRole.EMPLOYER);
        locked.setStatus(UserStatus.LOCKED);
        locked.setFailedLoginAttempts(5);
        when(userRepository.findById(2L)).thenReturn(Optional.of(locked));

        adminUserService.unlock(2L, adminUser);

        assertEquals(UserStatus.ACTIVE, locked.getStatus());
        assertEquals(0, locked.getFailedLoginAttempts());
        verify(userRepository).save(locked);
    }

    // ----- audit tests -----

    @Test
    void createRecordsActorInAudit() {
        AdminUserCreateRequest request = new AdminUserCreateRequest("newuser", "new@example.com", "Password1!", UserRole.EMPLOYER);
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordPolicyService.validate(any())).thenReturn(List.of());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });

        adminUserService.create(request, adminUser);

        verify(auditService).log(
            eq(adminUser.id()),
            eq("USER_CREATED"),
            eq("USER"),
            any(),
            isNull(),
            isNotNull()
        );
    }

    @Test
    void unlockRecordsAuditWithActor() {
        User locked = buildUser(2L, UserRole.EMPLOYER);
        locked.setStatus(UserStatus.LOCKED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(locked));

        adminUserService.unlock(2L, adminUser);

        verify(auditService).log(
            eq(adminUser.id()),
            eq("USER_UNLOCKED"),
            eq("USER"),
            eq(2L),
            isNotNull(),
            isNotNull()
        );
    }

    @Test
    void resetPasswordRecordsAuditWithActor() {
        User user = buildUser(2L, UserRole.EMPLOYER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(passwordPolicyService.isValid(any())).thenReturn(true);

        adminUserService.resetPassword(2L, adminUser);

        verify(auditService).log(
            eq(adminUser.id()),
            eq("USER_PASSWORD_RESET"),
            eq("USER"),
            eq(2L),
            isNull(),
            isNotNull()
        );
    }

    @Test
    void invalidRoleFilterReturnsBadRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> adminUserService.listUsers("NOT_A_ROLE", null, null, 0, 10));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void invalidStatusFilterReturnsBadRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> adminUserService.listUsers(null, "NOT_A_STATUS", null, 0, 10));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}
