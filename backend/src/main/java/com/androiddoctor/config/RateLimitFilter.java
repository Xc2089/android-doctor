package com.androiddoctor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final AppProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || "GET".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = request.getRemoteAddr();
        Window window = windows.compute(key, (ignored, old) ->
                old == null || old.minute != minute ? new Window(minute) : old);
        if (window.count.incrementAndGet() > properties.rateLimitPerMinute()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute - 2);
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();
        private Window(long minute) { this.minute = minute; }
    }
}

