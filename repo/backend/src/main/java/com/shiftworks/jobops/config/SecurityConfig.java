package com.shiftworks.jobops.config;

import com.shiftworks.jobops.security.CsrfValidationFilter;
import com.shiftworks.jobops.security.RateLimitFilter;
import com.shiftworks.jobops.security.SecurityHeadersFilter;
import com.shiftworks.jobops.security.SessionAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;
    private final SessionAuthFilter sessionAuthFilter;
    private final CsrfValidationFilter csrfValidationFilter;
    private final SecurityHeadersFilter securityHeadersFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/captcha").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                    "/api/categories", "/api/locations", "/api/locations/states").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll());
        http.addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(csrfValidationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
