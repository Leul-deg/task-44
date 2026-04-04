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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
        verify(auditService).log(user.getId(), "USER_LOGIN", "USER", user.getId(), null, null);
    }

    @Test
    void loginMarksPasswordExpiredAtDay90Boundary() {
        User user = buildUser();
        user.setPasswordChangedAt(java.time.Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS).minusSeconds(5));
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(true);
        UserSession session = new UserSession();
        session.setId("ssn");
        session.setCsrfToken("csrf");
        when(sessionService.createSession(eq(user), anyString(), anyString())).thenReturn(session);

        LoginRequest request = new LoginRequest(user.getUsername(), "secret", null, null);
        var result = authService.login(request, httpRequest(), true);

        assertTrue(result.isSuccess());
        assertTrue(result.response().passwordExpired());
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
        verify(auditService).log(user.getId(), "USER_LOGIN_FAILED", "USER", user.getId(), null, null);
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
    void loginMissingUserUsesValidDummyBcryptHash() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenAnswer(invocation -> {
            String rawPassword = invocation.getArgument(0);
            String encodedPassword = invocation.getArgument(1);
            assertDoesNotThrow(() -> new BCryptPasswordEncoder().matches(rawPassword, encodedPassword));
            return false;
        });

        LoginRequest request = new LoginRequest("ghost", "secret", null, null);
        var result = authService.login(request, httpRequest(), true);

        assertFalse(result.isSuccess());
        assertFalse(result.failure().captchaRequired());
        verify(passwordEncoder).matches(eq("secret"), anyString());
    }

    @Test
    void registerSuccess() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordPolicyService.validate(anyString())).thenReturn(List.of());
        when(passwordEncoder.encode("StrongPassword123!")).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        RegisterRequest request = new RegisterRequest("newuser", "new@example.com", "StrongPassword123!");
        var response = authService.register(request, 99L);
        assertEquals("newuser", response.username());
        verify(userRepository).save(any());
        verify(auditService).log(eq(99L), eq("USER_CREATED"), eq("USER"), eq(2L), isNull(), isNotNull());
    }

    @Test
    void registerDuplicateUsername() {
        User existing = buildUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        RegisterRequest request = new RegisterRequest("alice", "new@example.com", "StrongPassword123!");
        var ex = assertThrows(BusinessException.class, () -> authService.register(request, 99L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }
}
