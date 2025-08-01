package com.example.DataPreparationApp.Config;

import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Repository.UserRepository;
import com.example.DataPreparationApp.Services.AuthorityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AuthenticationConfig {

    private final UserRepository userRepository;
    private final AuthorityService authorityService;

    @Bean
    @Transactional
    public UserDetailsService userDetailsService() {
        return username -> {
            User user = userRepository.findByUsernameWithProfiles(username)
                    .orElseThrow(() -> {
                        return new UsernameNotFoundException("User not found: " + username);
                    });
            
            // Inject the AuthorityService into the User instance
            user.setAuthorityService(authorityService);
            
            // Pre-load authorities if needed
            try {
                user.setAuthorities(authorityService.getAuthorities(user));
                log.debug("Preloaded {} authorities for user {}", 
                        user.getAuthorities().size(), user.getUsername());
            } catch (Exception e) {
                log.error("Error loading authorities for user {}: {}", 
                        user.getUsername(), e.getMessage());
            }
            
            return user;
        };
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        // Set this to true to more clearly show which exception occurred during authentication
        authProvider.setHideUserNotFoundExceptions(true);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength factor of 12
    }
} 