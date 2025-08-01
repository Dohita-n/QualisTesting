package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.DTO.AuthResponse;
import com.example.DataPreparationApp.DTO.LoginRequest;
import com.example.DataPreparationApp.DTO.RegisterRequest;
import com.example.DataPreparationApp.DTO.TokenRefreshRequest;
import com.example.DataPreparationApp.Model.RefreshToken;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Services.JwtService;
import com.example.DataPreparationApp.Services.RefreshTokenService;
import com.example.DataPreparationApp.Services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.LockedException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    
    /**
     * Login endpoint
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            
            // First check if user exists to provide better error messages
            try {
                userService.loadUserByUsername(loginRequest.getUsernameOrEmail());
            } catch (UsernameNotFoundException e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                            "userExists", false,
                            "message", "Authentication failed",
                            "error", "Invalid username/email or password"
                        ));
            }
            
            // Authenticate the user
            Authentication authentication;
            try {
                authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                loginRequest.getUsernameOrEmail(),
                                loginRequest.getPassword()
                        )
                );
            } catch (BadCredentialsException e) {
                log.warn("Bad credentials for user: {}", loginRequest.getUsernameOrEmail());
                userService.loginFailure(loginRequest.getUsernameOrEmail());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                            "userExists", true,
                            "message", "Authentication failed",
                            "error", "Invalid password"
                        ));
            } catch (LockedException e) {
                log.warn("Locked account access attempt: {}", loginRequest.getUsernameOrEmail());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                            "userExists", true,
                            "message", "Account locked",
                            "error", "Your account has been locked due to too many failed login attempts"
                        ));
            } catch (DisabledException e) {
                log.warn("Disabled account access attempt: {}", loginRequest.getUsernameOrEmail());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                            "userExists", true,
                            "message", "Account disabled",
                            "error", "Your account has been disabled"
                        ));
            } catch (Exception e) {
                log.error("Authentication error: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                            "userExists", true,
                            "causeType", e.getClass().getName(),
                            "errorMessage", e.getMessage() != null ? e.getMessage() : "No error message available",
                            "cause", e.getCause() != null ? e.getCause().toString() : null,
                            "message", "Authentication failed",
                            "error", "Internal authentication error"
                        ));
            }
            
            // Get the authenticated user
            User user = (User) authentication.getPrincipal();
            log.info("User authenticated successfully: {}", user.getUsername());
            
            // Record successful login
            userService.loginSuccess(user);
            
            // Check if user has any authorities
            if (user.getAuthorities() == null || user.getAuthorities().isEmpty()) {
                log.warn("User has no authorities: {}", user.getUsername());
            } else {
                log.info("User authorities: {}", user.getAuthorities());
            }
            
            // Generate tokens
            String accessToken;
            RefreshToken refreshToken;
            try {
                accessToken = jwtService.generateToken(user);
                refreshToken = refreshTokenService.createRefreshToken(user, request);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Error generating authentication tokens"));
            }
            
            // Build response
            return ResponseEntity.ok(AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .tokenType("Bearer")
                    .userId(user.getId().toString())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .build());
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Authentication failed: " + e.getMessage()));
        }
    }
    
    /**
     * Register endpoint
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            // Register the user
            User user = userService.registerUser(
                    registerRequest.getUsername(),
                    registerRequest.getEmail(),
                    registerRequest.getPassword(),
                    registerRequest.getFirstName(),
                    registerRequest.getLastName()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "User registered successfully");
            response.put("userId", user.getId().toString());
            response.put("username", user.getUsername());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Registration error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registration failed: " + e.getMessage()));
        }
    }
    
    /**
     * Refresh token endpoint
     */
    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        
        try {
            // Find and validate the refresh token
            RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshToken)
                    .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
                    
            if (!refreshTokenService.verifyExpiration(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Refresh token has expired"));
            }
            
            // Get the user
            User user = refreshToken.getUser();
            
            // Generate a new access token
            String accessToken = jwtService.generateToken(user);
            
            // Build response
            return ResponseEntity.ok(AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .tokenType("Bearer")
                    .userId(user.getId().toString())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .build());
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Token refresh failed: " + e.getMessage()));
        }
    }
    
    /**
     * Logout endpoint
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody Map<String, String> logoutRequest) {
        try {
            String refreshToken = logoutRequest.get("refreshToken");
            if (refreshToken != null) {
                refreshTokenService.revokeRefreshToken(refreshToken);
            }
            
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Logout failed: " + e.getMessage()));
        }
    }
} 