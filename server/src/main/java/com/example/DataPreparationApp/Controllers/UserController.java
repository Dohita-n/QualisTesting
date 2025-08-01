package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.DTO.UserDTO;
import com.example.DataPreparationApp.DTO.ProfileDTO;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Model.UserProfile;
import com.example.DataPreparationApp.Model.ProfileRole;
import com.example.DataPreparationApp.Repository.UserProfileRepository;
import com.example.DataPreparationApp.Repository.ProfileRoleRepository;
import com.example.DataPreparationApp.Services.UserService;
import com.example.DataPreparationApp.Services.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {
    
    private final UserService userService;
    private final UserProfileRepository userProfileRepository;
    private final ProfileRoleRepository profileRoleRepository;
    private final FileStorageService fileStorageService;
    
    /**
     * Get the current authenticated user's information
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            User currentUser = userService.getCurrentUser();
            
            // Fetch user profiles separately to avoid lazy loading issues
            List<UserProfile> userProfiles = userProfileRepository.findByUserId(currentUser.getId());
            
            // Create a custom response
            Map<String, Object> response = new HashMap<>();
            response.put("id", currentUser.getId());
            response.put("username", currentUser.getUsername());
            response.put("email", currentUser.getEmail());
            response.put("firstName", currentUser.getFirstName());
            response.put("lastName", currentUser.getLastName());
            response.put("avatar", currentUser.getAvatarUrl());
            
            // Add profile information with roles
            List<Map<String, Object>> profiles = userProfiles.stream()
                .map(userProfile -> {
                    Map<String, Object> profileInfo = new HashMap<>();
                    profileInfo.put("id", userProfile.getProfile().getId());
                    profileInfo.put("name", userProfile.getProfile().getName());
                    profileInfo.put("description", userProfile.getProfile().getDescription());
                    
                    // Fetch roles for this profile
                    List<ProfileRole> profileRoles = profileRoleRepository.findByProfileId(userProfile.getProfile().getId());
                    List<String> roleNames = profileRoles.stream()
                        .map(profileRole -> profileRole.getRole().getName())
                        .collect(Collectors.toList());
                    
                    profileInfo.put("roleNames", roleNames);
                    return profileInfo;
                })
                .collect(Collectors.toList());
            
            response.put("profiles", profiles);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve user information", 
                             "message", e.getMessage()));
        }
    }
    
    /**
     * Update the current user's profile information
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody Map<String, String> profileData) {
        try {
            String firstName = profileData.get("firstName");
            String lastName = profileData.get("lastName");
            String username = profileData.get("username");
            
            // Store the original username before the update for session management
            User currentUser = userService.getCurrentUser();
            String originalUsername = currentUser.getUsername();
            
            User updatedUser = userService.updateUserProfile(firstName, lastName, username);
            
            // If username changed, update the security context to prevent session loss
            if (!originalUsername.equals(username)) {
                userService.updateSecurityContextAfterUsernameChange(originalUsername, username);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedUser.getId());
            response.put("username", updatedUser.getUsername());
            response.put("email", updatedUser.getEmail());
            response.put("firstName", updatedUser.getFirstName());
            response.put("lastName", updatedUser.getLastName());
            response.put("avatar", updatedUser.getAvatarUrl());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating user profile: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to update profile", 
                             "message", e.getMessage()));
        }
    }
    
    /**
     * Upload and update the user's avatar image
     */
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload"));
            }
            
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only JPEG and PNG images are allowed"));
            }
            
            // Get current user
            User currentUser = userService.getCurrentUser();
            String userId = currentUser.getId().toString();
            
            // Generate a unique filename for the avatar
            String fileName = "avatar_" + userId + "_" + System.currentTimeMillis() + 
                (contentType.equals("image/png") ? ".png" : ".jpg");
            
            // Save the file and update user record
            String avatarUrl = fileStorageService.storeFile(file, "avatars", fileName);
            User updatedUser = userService.updateUserAvatar(avatarUrl);
            
            return ResponseEntity.ok(Map.of(
                "message", "Avatar uploaded successfully",
                "avatarUrl", avatarUrl
            ));
        } catch (Exception e) {
            log.error("Error uploading avatar: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to upload avatar", 
                             "message", e.getMessage()));
        }
    }
    
    /**
     * Change the current user's password
     */
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> passwordData) {
        try {
            String currentPassword = passwordData.get("currentPassword");
            String newPassword = passwordData.get("newPassword");
            
            if (currentPassword == null || newPassword == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Both current and new passwords are required"));
            }
            
            userService.changePassword(currentPassword, newPassword);
            
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid password change attempt: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error changing password: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to change password", 
                             "message", e.getMessage()));
        }
    }
} 