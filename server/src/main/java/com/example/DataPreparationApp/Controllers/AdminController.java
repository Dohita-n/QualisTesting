package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.DTO.ProfileDTO;
import com.example.DataPreparationApp.DTO.RoleDTO;
import com.example.DataPreparationApp.DTO.UserDTO;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Model.UserProfile;
import com.example.DataPreparationApp.Model.ProfileRole;
import com.example.DataPreparationApp.Services.ProfileService;
import com.example.DataPreparationApp.Services.RoleService;
import com.example.DataPreparationApp.Services.UserService;
import com.example.DataPreparationApp.Services.ValidationService;
import com.example.DataPreparationApp.Repository.ProfileRoleRepository;
import com.example.DataPreparationApp.Repository.UserProfileRepository;
import com.example.DataPreparationApp.Repository.UserRepository;
import com.example.DataPreparationApp.Services.AuthorityService;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * AdminController provides administrative endpoints to diagnose and fix data integrity issues
 * These endpoints should be restricted in a production environment
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminController {
    
    private static final Logger logger = Logger.getLogger(AdminController.class.getName());
    private final RoleService roleService;
    private final ProfileService profileService;
    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;
    private final ValidationService validationService;
    private final ProfileRoleRepository profileRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final AuthorityService authorityService;
    
    @Autowired
    public AdminController(
            RoleService roleService,
            ProfileService profileService,
            UserService userService,
            JdbcTemplate jdbcTemplate,
            ValidationService validationService,
            ProfileRoleRepository profileRoleRepository,
            UserProfileRepository userProfileRepository,
            UserRepository userRepository,
            AuthorityService authorityService) {
        this.roleService = roleService;
        this.profileService = profileService;
        this.userService = userService;
        this.jdbcTemplate = jdbcTemplate;
        this.validationService = validationService;
        this.profileRoleRepository = profileRoleRepository;
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
        this.authorityService = authorityService;
    }
    
    // Role management endpoints
    
    @GetMapping("/roles")
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        try {
            return ResponseEntity.ok(roleService.getAllRoles());
        } catch (Exception e) {
            log.error("Error fetching roles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.emptyList());
        }
    }
    
    @GetMapping("/roles/{id}")
    public ResponseEntity<RoleDTO> getRoleById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(roleService.getRoleById(id));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
    
   /*  @PostMapping("/roles")
    public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody RoleDTO roleDTO) {
        try {
            return new ResponseEntity<>(roleService.createRole(roleDTO), HttpStatus.CREATED);
        } catch (EntityExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
    
    @PutMapping("/roles/{id}")
    public ResponseEntity<RoleDTO> updateRole(@PathVariable UUID id, @Valid @RequestBody RoleDTO roleDTO) {
        try {
            return ResponseEntity.ok(roleService.updateRole(id, roleDTO));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (EntityExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    } 
    
    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        try {
            roleService.deleteRole(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }*/
    
    // Profile management endpoints
    
    @GetMapping("/profiles")
    public ResponseEntity<List<ProfileDTO>> getAllProfiles() {
        try {
            List<ProfileDTO> profiles = profileService.getAllProfilesWithRoleNames();
            return ResponseEntity.ok(profiles);
        } catch (Exception e) {
            log.error("Error fetching profiles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.emptyList());
        }
    }
    
    @GetMapping("/profiles/{id}")
    public ResponseEntity<ProfileDTO> getProfileById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(profileService.getProfileById(id));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching profile: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching profile");
        }
    }
    
    @PostMapping("/profiles")
    public ResponseEntity<ProfileDTO> createProfile(@Valid @RequestBody ProfileDTO profileDTO) {
        try {
            return new ResponseEntity<>(profileService.createProfile(profileDTO), HttpStatus.CREATED);
        } catch (EntityExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Error creating profile: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error creating profile");
        }
    }
    
    @PutMapping("/profiles/{id}")
    public ResponseEntity<ProfileDTO> updateProfile(@PathVariable UUID id, @Valid @RequestBody ProfileDTO profileDTO) {
        try {
            return ResponseEntity.ok(profileService.updateProfile(id, profileDTO));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (EntityExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Error updating profile: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating profile");
        }
    }
    
    @DeleteMapping("/profiles/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable UUID id) {
        try {
            profileService.deleteProfile(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting profile: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting profile");
        }
    }
    
    // Profile-Role assignments
    
    @PostMapping("/profiles/{profileId}/roles")
    public ResponseEntity<Map<String, Object>> assignRolesToProfile(
            @PathVariable UUID profileId, 
            @RequestBody List<UUID> roleIds) {
        try {
            ProfileDTO updatedProfile = profileService.assignRolesToProfile(profileId, roleIds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Roles assigned to profile and security cache invalidated for affected users");
            response.put("profile", updatedProfile);
            
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error assigning roles to profile: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error assigning roles");
        }
    }
    
    @DeleteMapping("/profiles/{profileId}/roles")
    public ResponseEntity<Map<String, Object>> removeRolesFromProfile(
            @PathVariable UUID profileId, 
            @RequestBody List<UUID> roleIds) {
        try {
            ProfileDTO updatedProfile = profileService.removeRolesFromProfile(profileId, roleIds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Roles removed from profile and security cache invalidated for affected users");
            response.put("profile", updatedProfile);
            
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error removing roles from profile: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error removing roles");
        }
    }
    
    // User-Profile assignments
    
    @GetMapping("/profiles/{profileId}/users")
    public ResponseEntity<List<UserDTO>> getUsersByProfile(@PathVariable UUID profileId) {
        try {
            return ResponseEntity.ok(profileService.getUsersByProfileId(profileId));
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching users by profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.emptyList());
        }
    }
    
    @PostMapping("/users/{userId}/profiles/{profileId}")
    public ResponseEntity<Map<String, Object>> assignProfileToUser(
            @PathVariable UUID userId, 
            @PathVariable UUID profileId) {
        try {
            User currentUser = userService.getCurrentUser();
            profileService.assignProfileToUser(profileId, userId, currentUser);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile assigned successfully and security cache invalidated");
            response.put("userId", userId);
            response.put("profileId", profileId);
            
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (EntityExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("Error assigning profile to user: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error assigning profile");
        }
    }
    
    @DeleteMapping("/users/{userId}/profiles/{profileId}")
    public ResponseEntity<Map<String, Object>> removeProfileFromUser(
            @PathVariable UUID userId, 
            @PathVariable UUID profileId) {
        try {
            profileService.removeProfileFromUser(profileId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile removed successfully and security cache invalidated");
            response.put("userId", userId);
            response.put("profileId", profileId);
            
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error removing profile from user: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error removing profile");
        }
    }
    
    /**
     * Diagnose database integrity issues related to dataset_column_statistics
     */
    @GetMapping("/diagnose/statistics")
    public ResponseEntity<?> diagnoseStatistics() {
        try {
            Map<String, Object> results = new HashMap<>();
            
            // Find columns with duplicate statistics
            String duplicateQuery = "SELECT dataset_column_id, COUNT(*) as count FROM dataset_column_statistics GROUP BY dataset_column_id HAVING COUNT(*) > 1";
            List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(duplicateQuery);
            
            results.put("duplicateCount", duplicates.size());
            results.put("duplicates", duplicates);
            
            if (!duplicates.isEmpty()) {
                // Get details of the first duplicate entry to help with debugging
                String columnId = duplicates.get(0).get("dataset_column_id").toString();
                String detailsQuery = "SELECT * FROM dataset_column_statistics WHERE dataset_column_id = ?";
                List<Map<String, Object>> details = jdbcTemplate.queryForList(detailsQuery, columnId);
                results.put("sampleDetails", details);
            }
            
            // Get overall statistics count
            String countQuery = "SELECT COUNT(*) FROM dataset_column_statistics";
            Integer totalCount = jdbcTemplate.queryForObject(countQuery, Integer.class);
            results.put("totalStatistics", totalCount);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.severe("Error diagnosing statistics: " + e.getMessage());
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Fix all duplicate statistics records in the database
     */
    @PostMapping("/fix/statistics")
    @Transactional
    public ResponseEntity<?> fixStatistics() {
        try {
            // Find all columns with duplicate statistics
            String duplicateQuery = "SELECT dataset_column_id, COUNT(*) as count FROM dataset_column_statistics GROUP BY dataset_column_id HAVING COUNT(*) > 1";
            List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(duplicateQuery);
            
            Map<String, Object> results = new HashMap<>();
            results.put("duplicateColumnsFound", duplicates.size());
            
            List<String> fixedColumns = new ArrayList<>();
            
            // Fix each duplicate
            for (Map<String, Object> duplicate : duplicates) {
                String columnId = duplicate.get("dataset_column_id").toString();
                validationService.fixDuplicatesWithSql(UUID.fromString(columnId));
                fixedColumns.add(columnId);
            }
            
            results.put("fixedColumns", fixedColumns);
            
            // Verify fix
            String verifyQuery = "SELECT dataset_column_id, COUNT(*) as count FROM dataset_column_statistics GROUP BY dataset_column_id HAVING COUNT(*) > 1";
            List<Map<String, Object>> remaining = jdbcTemplate.queryForList(verifyQuery);
            
            results.put("remainingDuplicates", remaining.size());
            results.put("remainingDetails", remaining);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.severe("Error fixing statistics: " + e.getMessage());
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Fix duplicate statistics for a specific column
     */
    @PostMapping("/fix/statistics/{columnId}")
    @Transactional
    public ResponseEntity<?> fixColumnStatistics(@PathVariable String columnId) {
        try {
            UUID id = UUID.fromString(columnId);
            
            // Check if duplicates exist
            String checkQuery = "SELECT COUNT(*) FROM dataset_column_statistics WHERE dataset_column_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkQuery, Integer.class, id);
            
            Map<String, Object> results = new HashMap<>();
            results.put("columnId", columnId);
            results.put("statisticsCount", count);
            
            if (count != null && count > 1) {
                validationService.fixDuplicatesWithSql(id);
                results.put("fixed", true);
                
                // Verify fix
                Integer newCount = jdbcTemplate.queryForObject(checkQuery, Integer.class, id);
                results.put("newCount", newCount);
            } else {
                results.put("fixed", false);
                results.put("message", "No duplicates found for this column");
            }
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.severe("Error fixing column statistics: " + e.getMessage());
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    // User management endpoints
    
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();
            List<Map<String, Object>> userDTOs = new ArrayList<>();
            
            for (User user : users) {
                Map<String, Object> userDTO = new HashMap<>();
                userDTO.put("id", user.getId());
                userDTO.put("username", user.getUsername());
                userDTO.put("email", user.getEmail());
                userDTO.put("firstName", user.getFirstName());
                userDTO.put("lastName", user.getLastName());
                userDTO.put("avatarUrl", user.getAvatarUrl());
                userDTO.put("enabled", user.isEnabled());
                userDTO.put("accountNonLocked", user.isAccountNonLocked());
                userDTO.put("createdAt", user.getCreatedAt());
                
                // Get user status
                String status = "active";
                if (!user.isEnabled()) {
                    status = "disabled";
                } else if (!user.isAccountNonLocked()) {
                    status = "locked";
                }
                userDTO.put("status", status);
                
                // Get user profiles
                List<UserProfile> userProfiles = userProfileRepository.findByUserId(user.getId());
                List<Map<String, Object>> profilesInfo = userProfiles.stream()
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
                
                userDTO.put("profiles", profilesInfo);
                userDTOs.add(userDTO);
            }
            
            return ResponseEntity.ok(userDTOs);
        } catch (Exception e) {
            log.error("Error fetching users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.emptyList());
        }
    }
    
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting user");
        }
    }
    
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable UUID id) {
        try {
            User user = userService.findById(id);
            
            Map<String, Object> userDTO = new HashMap<>();
            userDTO.put("id", user.getId());
            userDTO.put("username", user.getUsername());
            userDTO.put("email", user.getEmail());
            userDTO.put("firstName", user.getFirstName());
            userDTO.put("lastName", user.getLastName());
            userDTO.put("avatarUrl", user.getAvatarUrl());
            userDTO.put("enabled", user.isEnabled());
            userDTO.put("accountNonLocked", user.isAccountNonLocked());
            userDTO.put("createdAt", user.getCreatedAt());
            
            // Get user status
            String status = "active";
            if (!user.isEnabled()) {
                status = "disabled";
            } else if (!user.isAccountNonLocked()) {
                status = "locked";
            }
            userDTO.put("status", status);
            
            // Get user profiles
            List<UserProfile> userProfiles = userProfileRepository.findByUserId(user.getId());
            List<Map<String, Object>> profilesInfo = userProfiles.stream()
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
            
            userDTO.put("profiles", profilesInfo);
            
            return ResponseEntity.ok(userDTO);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "User not found", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error fetching user", "message", e.getMessage()));
        }
    }
    
    /**
     * Manually clear the authority cache for a specific user
     */
    @PostMapping("/users/{userId}/clear-cache")
    public ResponseEntity<Map<String, Object>> clearUserCache(@PathVariable UUID userId) {
        try {
            // Check if user exists
            if (!userRepository.existsById(userId)) {
                throw new EntityNotFoundException("User not found with id: " + userId);
            }
            
            // Clear cache for this user
            authorityService.clearCacheForUser(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Authority cache cleared for user");
            response.put("userId", userId);
            
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error clearing cache for user: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error clearing cache");
        }
    }
    
    /**
     * Manually clear the authority cache for all users
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<Map<String, Object>> clearAllCache() {
        try {
            // Clear the entire cache
            authorityService.clearCache();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Authority cache cleared for all users");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error clearing cache: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error clearing cache");
        }
    }
    
    /**
     * Global exception handler for this controller
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception in AdminController: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An unexpected error occurred", 
                         "message", e.getMessage() != null ? e.getMessage() : "No message available"));
    }
} 