package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.LoginRequest;
import com.shiftworks.jobops.dto.RegisterRequest;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.enums.UserStatus;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private SessionService sessionService;
    @Mock private AuditService auditService;
    @Spy private AppProperties appProperties = buildProps();
    @InjectMocks private AuthService authService;

    private static AppProperties buildProps() {
        AppProperties props = new AppProperties();
        AppProperties.Security sec = new AppProperties.Security();
        sec.setCaptchaAfterFailures(3);
        sec.setLockAfterFailures(5);
        sec.setLockDurationMinutes(15);
        AppProperties.Password pwd = new AppProperties.Password();
        pwd.setMinLength(12);
        pwd.setRotationDays(90);
        sec.setPassword(pwd);
        props.setSecurity(sec);
        return props;
    }

    private HttpServletRequest httpRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("agent");
        return request;
    }

    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPasswordHash("encoded");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.EMPLOYER);
        return user;
    }

    @Test
    void loginSuccess() {
        User user = buildUser();
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(true);
        UserSession session = new UserSession();
        session.setId("ssn");
        session.setCsrfToken("csrf");
        when(sessionService.createSession(eq(user), anyString(), anyString())).thenReturn(session);

        LoginRequest request = new LoginRequest(user.getUsername(), "secret", null, null);
        var result = authService.login(request, httpRequest(), true);
        assertTrue(result.isSuccess());
    }

    @Test
    void loginInvalidPasswordIncrementsAttempts() {
        User user = buildUser();
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(false);

        LoginRequest request = new LoginRequest(user.getUsername(), "secret", null, null);
        var result = authService.login(request, httpRequest(), true);
        assertFalse(result.isSuccess());
        assertEquals(1, user.getFailedLoginAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void loginLocksAfter5Failures() {
        User user = buildUser();
        user.setFailedLoginAttempts(4);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(false);

        LoginRequest request = new LoginRequest(user.getUsername(), "secret", null, null);
        authService.login(request, httpRequest(), true);
        assertEquals(UserStatus.LOCKED, user.getStatus());
    }

    @Test
    void loginCaptchaRequiredAfter3Failures() {
        User user = buildUser();
        user.setFailedLoginAttempts(3);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        LoginRequest request = new LoginRequest(user.getUsername(), "secret", null, null);
        var result = authService.login(request, httpRequest(), false);
        assertFalse(result.isSuccess());
        assertTrue(result.failure().captchaRequired());
    }

    @Test
    void registerSuccess() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordPolicyService.validate(anyString())).thenReturn(List.of());
        when(passwordEncoder.encode("StrongPassword123!")).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest("newuser", "new@example.com", "StrongPassword123!");
        var response = authService.register(request);
        assertEquals("newuser", response.username());
        verify(userRepository).save(any());
    }

    @Test
    void registerDuplicateUsername() {
        User existing = buildUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        RegisterRequest request = new RegisterRequest("alice", "new@example.com", "StrongPassword123!");
        var ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }
}
