package com.example.DataPreparationApp.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Filter that implements rate limiting for login attempts
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();
    
    // Maximum number of login attempts per minute
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Only apply rate limiting to login endpoint
        if (request.getMethod().equals("POST") && request.getRequestURI().endsWith("/api/auth/login")) {
            String clientIp = getClientIp(request);
            RequestCounter counter = requestCounts.computeIfAbsent(clientIp, k -> new RequestCounter());
            
            if (counter.isMaximumExceeded()) {
                log.warn("Rate limit exceeded for IP: {}", clientIp);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("{\"message\": \"Too many login attempts. Please try again later.\"}");
                response.setContentType("application/json");
                return;
            }
            
            counter.increment();
        }
        
        filterChain.doFilter(request, response);
    }
    /**
     * Extract the client IP address from the request
     */
    private String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }
    
    /**
     * Counter class to track requests and reset after a time window
     */
    private static class RequestCounter {
        private int count;
        private long lastResetTime;
        
        public RequestCounter() {
            this.count = 0;
            this.lastResetTime = System.currentTimeMillis();
        }
        
        public synchronized void increment() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastResetTime >= TimeUnit.MINUTES.toMillis(1)) {
                // Reset if more than a minute has passed
                count = 1;
                lastResetTime = currentTime;
            } else {
                count++;
            }
        }
        
        public synchronized boolean isMaximumExceeded() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastResetTime >= TimeUnit.MINUTES.toMillis(1)) {
                // Reset if more than a minute has passed
                count = 0;
                lastResetTime = currentTime;
                return false;
            }
            
            return count >= MAX_REQUESTS_PER_MINUTE;
        }
    }
} 