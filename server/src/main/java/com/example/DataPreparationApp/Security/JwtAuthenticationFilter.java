package com.example.DataPreparationApp.Security;

import com.example.DataPreparationApp.Services.JwtService;
import com.example.DataPreparationApp.Services.AuthorityService;
import com.example.DataPreparationApp.Model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;

import java.io.IOException;
import java.util.Collection;

import org.hibernate.Hibernate;

/**
 * Filter that intercepts each request to validate JWT tokens
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AuthorityService authorityService;
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            final String authHeader = request.getHeader("Authorization");
            final String jwt;
            final String username;

            // If no JWT token is present, continue the filter chain
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extract the JWT token (remove "Bearer " prefix)
            jwt = authHeader.substring(7);
            
            // Extract username from the token
            username = jwtService.extractUsername(jwt);
            
            // If username exists and there's no authentication yet
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Load the user details - with minimal data needed for authentication
                UserDetails userDetails = null;
                try {
                    userDetails = userDetailsService.loadUserByUsername(username);
                } catch (Exception e) {
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Load authorities using the AuthorityService only if not already loaded
                if (userDetails instanceof User) {
                    User user = (User) userDetails;
                    Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
                    
                    // Only load authorities if they haven't been loaded yet or are empty
                    if (authorities == null || authorities.isEmpty()) {
                        try {
                            // Ensure AuthorityService is injected
                            if (user.getAuthorityService() == null) {
                                user.setAuthorityService(authorityService);
                            }
                            
                            // Load and set authorities
                            authorities = authorityService.getAuthorities(user);
                            user.setAuthorities(authorities);
                        } catch (Exception e) {
                            log.error("Error loading authorities for user {}: {}", user.getUsername(), e.getMessage());
                        }
                    } else {
                        log.debug("Using pre-loaded authorities for user {}", user.getUsername());
                    }
                }
                
                // Validate the token against the user details
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    try {
                        // Create an authentication token
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        
                        // Set details from the request
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        
                        // Update the security context with the new authentication
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        
                        logger.debug("User {} successfully authenticated", username);
                    } catch (Exception e) {
                        logger.error("Error during authentication: {}", e.getMessage(), e);
                    }
                } else {
                    logger.warn("Invalid JWT token for user: {}", username);
                }
            }
        } catch (Exception e) {
            logger.error("Authentication error: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Authentication error: " + e.getMessage());
        }

        // Continue the filter chain
        filterChain.doFilter(request, response);
    }
} 