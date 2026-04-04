package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.dto.CaptchaResponse;
import com.shiftworks.jobops.dto.ChangePasswordRequest;
import com.shiftworks.jobops.dto.LoginRequest;
import com.shiftworks.jobops.dto.LoginResponse;
import com.shiftworks.jobops.dto.RegisterRequest;
import com.shiftworks.jobops.dto.UserResponse;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.AuthService;
import com.shiftworks.jobops.service.CaptchaService;
import com.shiftworks.jobops.service.SessionService;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final AppProperties appProperties;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        boolean captchaPassed = request.captchaId() != null && captchaService.validate(request.captchaId(), request.captchaAnswer());
        var result = authService.login(request, httpRequest, captchaPassed);
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "code", HttpStatus.UNAUTHORIZED.value(),
                "message", AuthService.LOGIN_FAILURE,
                "captchaRequired", result.failure().captchaRequired()
            ));
        }
        LoginResponse response = result.response();
        addSessionCookies(httpResponse, result.sessionToken(), response.csrfToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.logout(user.id());
        clearSessionCookies(response);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(Authentication authentication, @Valid @RequestBody RegisterRequest request) {
        AuthenticatedUser actor = (AuthenticatedUser) authentication.getPrincipal();
        UserResponse user = authService.register(request, actor.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @GetMapping("/captcha")
    public ResponseEntity<CaptchaResponse> captcha() {
        var captcha = captchaService.generateCaptcha();
        return ResponseEntity.ok(new CaptchaResponse(captcha.captchaId(), "data:image/png;base64," + captcha.imageBase64()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request, HttpServletResponse response) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.changePassword(user.id(), request);
        clearSessionCookies(response);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.me(user.id()));
    }

    private void addSessionCookies(HttpServletResponse response, String sessionToken, String csrfToken) {
        Duration sessionDuration = Duration.ofHours(appProperties.getSession().getAbsoluteTimeoutHours());
        ResponseCookie sessionCookie = ResponseCookie.from(SessionService.SESSION_COOKIE, sessionToken)
            .path("/")
            .httpOnly(true)
            .sameSite("Strict")
            .secure("true".equals(System.getenv("COOKIE_SECURE")))
            .maxAge(sessionDuration)
            .build();
        ResponseCookie csrfCookie = ResponseCookie.from(SessionService.CSRF_COOKIE, csrfToken)
            .path("/")
            .httpOnly(false)
            .sameSite("Strict")
            .secure("true".equals(System.getenv("COOKIE_SECURE")))
            .maxAge(sessionDuration)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    private void clearSessionCookies(HttpServletResponse response) {
        ResponseCookie sessionCookie = ResponseCookie.from(SessionService.SESSION_COOKIE, "")
            .path("/")
            .httpOnly(true)
            .sameSite("Strict")
            .maxAge(0)
            .build();
        ResponseCookie csrfCookie = ResponseCookie.from(SessionService.CSRF_COOKIE, "")
            .path("/")
            .httpOnly(false)
            .sameSite("Strict")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }
}
