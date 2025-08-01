package com.example.DataPreparationApp.Security;

import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Listener that handles successful and failed authentication attempts
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptListener implements ApplicationListener<AbstractAuthenticationEvent> {

    private final UserService userService;

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        if (event instanceof AuthenticationSuccessEvent) {
            handleSuccessfulLogin((AuthenticationSuccessEvent) event);
        } else if (event instanceof AuthenticationFailureBadCredentialsEvent) {
            handleFailedLogin((AuthenticationFailureBadCredentialsEvent) event);
        }
    }

    /**
     * Handle successful authentication
     */
    private void handleSuccessfulLogin(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof User) {
            User user = (User) principal;
            log.info("Successful login: {}", user.getUsername());
            userService.loginSuccess(user);
        }
    }

    /**
     * Handle failed authentication
     */
    private void handleFailedLogin(AuthenticationFailureBadCredentialsEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof String) {
            String username = (String) principal;
            log.warn("Failed login attempt for user: {}", username);
            userService.loginFailure(username);
        }
    }
} 