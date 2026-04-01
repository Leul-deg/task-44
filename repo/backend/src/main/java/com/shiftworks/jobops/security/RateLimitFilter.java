package com.shiftworks.jobops.security;

import com.shiftworks.jobops.config.AppProperties;
import com.shiftworks.jobops.entity.UserSession;
import com.shiftworks.jobops.service.SessionService;
import com.shiftworks.jobops.util.CookieUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final AppProperties appProperties;

    private final Map<String, BucketHolder> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketHolder> ipBuckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Long userId = resolveUserId(request);
        boolean isAuthEndpoint = request.getRequestURI().startsWith("/api/auth");
        if (userId != null) {
            if (!allowRequest(userBuckets, userId.toString(), appProperties.getRateLimit().getPerMinute())) {
                writeRateLimitResponse(response);
                return;
            }
        } else if (isAuthEndpoint) {
            String key = request.getRemoteAddr();
            if (!allowRequest(ipBuckets, key, appProperties.getRateLimit().getAuthPerMinute())) {
                writeRateLimitResponse(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean allowRequest(Map<String, BucketHolder> buckets, String key, int limit) {
        BucketHolder holder = buckets.computeIfAbsent(key, k -> new BucketHolder(new TokenBucket(limit, limit)));
        holder.touch();
        return holder.bucket.tryConsume();
    }

    private Long resolveUserId(HttpServletRequest request) {
        UserSession existing = (UserSession) request.getAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE);
        if (existing != null) {
            return existing.getUser().getId();
        }
        return CookieUtils.getCookie(request, SessionService.SESSION_COOKIE)
            .flatMap(sessionService::findValidSession)
            .map(session -> {
                request.setAttribute(SessionAuthFilter.SESSION_REQUEST_ATTRIBUTE, session);
                return session.getUser().getId();
            })
            .orElse(null);
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":429,\"message\":\"Rate limit exceeded\"}");
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanBuckets() {
        Instant cutoff = Instant.now().minusSeconds(600);
        userBuckets.entrySet().removeIf(entry -> entry.getValue().isStale(cutoff));
        ipBuckets.entrySet().removeIf(entry -> entry.getValue().isStale(cutoff));
    }

    private static final class BucketHolder {
        private final TokenBucket bucket;
        private volatile Instant lastTouched;

        private BucketHolder(TokenBucket bucket) {
            this.bucket = bucket;
            this.lastTouched = Instant.now();
        }

        private void touch() {
            this.lastTouched = Instant.now();
        }

        private boolean isStale(Instant cutoff) {
            return lastTouched.isBefore(cutoff);
        }
    }
}
