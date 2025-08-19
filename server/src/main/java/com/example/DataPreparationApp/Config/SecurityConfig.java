package com.example.DataPreparationApp.Config;

import com.example.DataPreparationApp.Security.JwtAuthenticationFilter;
import com.example.DataPreparationApp.Security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final AuthenticationProvider authenticationProvider;
    
    private static final String[] PUBLIC_URLS = {
        "/api/auth/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-resources/**",
        "/webjars/**",
        "/files/**",
        "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_URLS).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Admin endpoints require ADMIN role
                .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                // Dataset endpoints with different permissions based on operation
                .requestMatchers(HttpMethod.GET, "/api/datasets/**").hasAnyAuthority("VIEW_DATA", "EDIT_DATA", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/datasets/**").hasAnyAuthority("UPLOAD_DATA", "EDIT_DATA", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/datasets/**").hasAnyAuthority("EDIT_DATA", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/datasets/**").hasAnyAuthority("DELETE_DATA", "ADMIN")
                // File upload endpoints
                .requestMatchers("/api/files/upload").hasAnyAuthority("UPLOAD_DATA", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/files/**").hasAnyAuthority("VIEW_DATA", "EDIT_DATA", "ADMIN")
                // Export endpoints
                .requestMatchers("/api/export/**").hasAnyAuthority("EXPORT_DATA", "ADMIN")
                // Data preparation endpoints
                .requestMatchers(HttpMethod.GET, "/api/preparations/**").hasAnyAuthority("EDIT_DATA", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/preparations/**").hasAnyAuthority("EDIT_DATA", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/preparations/**").hasAnyAuthority("EDIT_DATA", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/preparations/**").hasAnyAuthority("ADMIN", "DELETE_DATA")
                // Transformation endpoints
                .requestMatchers("/api/transformations/**").hasAnyAuthority("EDIT_DATA", "ADMIN")
                .requestMatchers("/api/preparations/*/transformations/**").hasAnyAuthority("EDIT_DATA", "ADMIN")
                .requestMatchers("/api/preparations/*/preview").hasAnyAuthority("EDIT_DATA", "ADMIN")
                // Validation endpoints
                .requestMatchers(HttpMethod.GET, "/api/validation/**").hasAnyAuthority("VIEW_DATA", "EDIT_DATA", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/validation/datasets/*/columns/*/validate").hasAnyAuthority("EDIT_DATA", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/validation/datasets/*/fix-duplicates").hasAnyAuthority("VIEW_DATA", "EDIT_DATA", "ADMIN")
                // Profiling endpoints - class-level annotation already specifies VIEW_DATA, EDIT_DATA, ADMIN
                .requestMatchers("/api/profiling/**").hasAnyAuthority("VIEW_DATA", "EXPORT_DATA", "EDIT_DATA", "ADMIN")
                // User endpoints - allow authenticated users to manage their own profile
                .requestMatchers("/api/user/**").authenticated()
                // Auth endpoints are already configured in PUBLIC_URLS
                // Test endpoints - keep their specific permissions
                .requestMatchers("/api/test/admin").hasAuthority("ADMIN")
                .requestMatchers("/api/test/view").hasAuthority("VIEW_DATA")
                .requestMatchers("/api/test/edit").hasAuthority("EDIT_DATA")
                .requestMatchers("/api/test/upload").hasAuthority("UPLOAD_DATA")
                .requestMatchers("/api/test/user").authenticated()
                // Default rule - must be authenticated
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
                .xssProtection(xss -> xss.disable())
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'self'; object-src 'none'; script-src 'self'"))
            );
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("https://localhost:*", "http://localhost:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
} 