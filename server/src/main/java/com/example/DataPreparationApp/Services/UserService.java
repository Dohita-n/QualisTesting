package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    private static final int MAX_FAILED_ATTEMPTS = 5;
    
    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    @PostConstruct
    @Transactional
    public void initDefaultUser() {
        // Check if any users exist, if not create a default user
        if (userRepository.count() == 0) {
            log.info("No users found in the system. Creating a default admin user.");
            
            User defaultUser = User.builder()
                .email("admin@example.com")
                .username("admin")
                .password(passwordEncoder.encode("password"))
                .firstName("Admin")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
            
            defaultUser = userRepository.saveAndFlush(defaultUser);
            
            log.info("Default admin user created with email: admin@example.com");
        } else {
            // Ensure admin user's account flags are all set to true
            userRepository.findByUsername("admin").ifPresent(admin -> {
                boolean needsUpdate = false;
                
                if (!admin.isEnabled()) {
                    admin.setEnabled(true);
                    needsUpdate = true;
                }
                if (!admin.isAccountNonExpired()) {
                    admin.setAccountNonExpired(true);
                    needsUpdate = true;
                }
                if (!admin.isAccountNonLocked()) {
                    admin.setAccountNonLocked(true);
                    needsUpdate = true;
                }
                if (!admin.isCredentialsNonExpired()) {
                    admin.setCredentialsNonExpired(true);
                    needsUpdate = true;
                }
                
                if (needsUpdate) {
                    log.info("Updating admin user account flags to enabled state");
                    userRepository.saveAndFlush(admin);
                }
            });
        }
    }
    
    /**
     * Get the currently authenticated user
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof UserDetails)) {
            throw new IllegalStateException("No authenticated user found");
        }
    
        String username = ((UserDetails) authentication.getPrincipal()).getUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
    
    /**
     * Load user details by username or email
     */
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        try {
            // First try to find by username
            Optional<User> userOpt = userRepository.findByUsernameWithProfiles(usernameOrEmail);
            
            // If not found by username, try to find by email
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail(usernameOrEmail)
                    .map(user -> userRepository.findByUsernameWithProfiles(user.getUsername())
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail)));
            }
            
            return userOpt.orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));
        } catch (Exception e) {
            log.error("Error loading user by username/email: {}", usernameOrEmail, e);
            throw new UsernameNotFoundException("Error loading user: " + e.getMessage());
        }
    }
    
    /**
     * Process successful login (reset failed attempts)
     */
    @Transactional
    public synchronized void loginSuccess(User user) {
        if (user.getFailedAttemptCount() > 0) {
            user.setFailedAttemptCount(0);
            user.setLastFailedAttempt(null);
            userRepository.save(user);
        }
    }
    
    /**
     * Process failed login (increment failed attempts and possibly lock account)
     */
    @Transactional
    public synchronized void loginFailure(String usernameOrEmail) {
        Optional<User> userOpt = userRepository.findByUsername(usernameOrEmail);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(usernameOrEmail);
        }
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            int failedAttempts = user.getFailedAttemptCount() + 1;
            user.setFailedAttemptCount(failedAttempts);
            user.setLastFailedAttempt(LocalDateTime.now());
            
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setAccountNonLocked(false);
                log.warn("User account locked: {}", user.getUsername());
            }
            
            userRepository.save(user);
        }
    }
    
    /**
     * Register a new user
     */
    @Transactional
    public User registerUser(String username, String email, String password, String firstName, String lastName) {
        // Check if username already exists
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        
        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }
        
        // Create the new user
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        
        return userRepository.save(user);
    }
    
    /**
     * Find a user by ID
     */
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    }
    
    /**
     * Unlock a user account
     */
    @Transactional
    public synchronized void unlockUser(UUID userId) {
        User user = findById(userId);
        user.setAccountNonLocked(true);
        user.setFailedAttemptCount(0);
        user.setLastFailedAttempt(null);
        userRepository.save(user);
        log.info("User account unlocked: {}", user.getUsername());
    }
    
    /**
     * Updates the current user's profile information
     */
    @Transactional
    public User updateUserProfile(String firstName, String lastName, String username) {
        User currentUser = getCurrentUser();
        
        if (firstName != null) {
            currentUser.setFirstName(firstName);
        }
        
        if (lastName != null) {
            currentUser.setLastName(lastName);
        }
        
        if (username != null && !username.equals(currentUser.getUsername())) {
            // Check if username is already taken
            boolean usernameExists = userRepository.findByUsername(username).isPresent();
            if (usernameExists) {
                throw new IllegalArgumentException("Username already exists");
            }
            currentUser.setUsername(username);
        }
        
        return userRepository.save(currentUser);
    }
    
    /**
     * Updates the security context after a username change to prevent session loss
     */
    @Transactional
    public void updateSecurityContextAfterUsernameChange(String oldUsername, String newUsername) {
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth != null && currentAuth.getName().equals(oldUsername)) {
            // Load the updated user with new username
            UserDetails updatedUser = loadUserByUsername(newUsername);
            
            // Create a new authentication with the updated principal
            Authentication newAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                updatedUser, currentAuth.getCredentials(), currentAuth.getAuthorities());
            
            // Update the security context
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            log.info("Security context updated after username change from {} to {}", oldUsername, newUsername);
        }
    }
    
    /**
     * Updates the user's avatar URL
     */
    @Transactional
    public User updateUserAvatar(String avatarUrl) {
        User currentUser = getCurrentUser();
        currentUser.setAvatarUrl(avatarUrl);
        return userRepository.save(currentUser);
    }
    
    /**
     * Change a user's password
     */
    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        User user = getCurrentUser();
        
        // Validate the current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        
        // Check if the new password is different from the current one
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        
        // Basic password validation
        if (newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        
        // Update the password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("Password changed successfully for user: {}", user.getUsername());
    }
    
    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    /**
     * Delete a user by ID
     */
    @Transactional
    public void deleteUser(UUID id) {
        User user = findById(id);
        
        // Check if trying to delete the admin user
        if (user.getUsername().equals("admin")) {
            throw new IllegalArgumentException("Cannot delete the admin user");
        }
        
        // Delete the user
        userRepository.delete(user);
        log.info("User deleted successfully: {}", user.getUsername());
    }
} 